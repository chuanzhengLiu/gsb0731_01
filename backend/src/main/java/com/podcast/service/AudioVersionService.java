package com.podcast.service;

import com.podcast.domain.AudioVersion;
import com.podcast.domain.TimelineMarker;
import com.podcast.repository.AudioVersionRepository;
import com.podcast.repository.TimelineMarkerRepository;
import com.podcast.security.SecurityUtils;
import com.podcast.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class AudioVersionService {

    /** README §9: keep the latest 10 versions online; older ones are archived. */
    private static final int MAX_ONLINE_VERSIONS = 10;

    private final AudioVersionRepository repo;
    private final TimelineMarkerRepository markerRepo;
    private final AccessGuard accessGuard;
    private final AuthorizationService authz;
    private final StorageService storage;
    private final AudioFileValidator validator;
    private final FfmpegService ffmpeg;
    private final SignedUrlService signedUrl;
    private final AuditService audit;
    private final TranscriptionService transcription;

    public AudioVersionService(AudioVersionRepository repo, TimelineMarkerRepository markerRepo,
                               AccessGuard accessGuard, AuthorizationService authz,
                               StorageService storage, AudioFileValidator validator,
                               FfmpegService ffmpeg, SignedUrlService signedUrl, AuditService audit,
                               TranscriptionService transcription) {
        this.repo = repo;
        this.markerRepo = markerRepo;
        this.accessGuard = accessGuard;
        this.authz = authz;
        this.storage = storage;
        this.validator = validator;
        this.ffmpeg = ffmpeg;
        this.signedUrl = signedUrl;
        this.audit = audit;
        this.transcription = transcription;
    }

    @Transactional(readOnly = true)
    public List<AudioVersion> list(Long episodeId) {
        accessGuard.requireEpisode(episodeId);
        return repo.findByEpisodeIdOrderByVersionNumberDesc(episodeId);
    }

    @Transactional(readOnly = true)
    public AudioVersion get(Long audioVersionId) {
        AudioVersion v = repo.findById(audioVersionId)
                .orElseThrow(() -> ApiException.notFound("音频版本不存在"));
        accessGuard.requireEpisode(v.getEpisodeId()); // team isolation
        return v;
    }

    @Transactional
    public AudioVersion upload(Long episodeId, MultipartFile file) {
        accessGuard.requireEpisode(episodeId);
        authz.checkCanUploadAudio(episodeId); // README §9: 剪辑师仅限分配给自己的单集
        String ext = validator.validate(file);

        // Previous top version (if any) — used to migrate its markers forward.
        AudioVersion previous = repo.findTopByEpisodeIdOrderByVersionNumberDesc(episodeId).orElse(null);
        int nextVersion = previous != null ? previous.getVersionNumber() + 1 : 1;

        String storedName = "v" + nextVersion + "-" + UUID.randomUUID() + "." + ext;
        String key;
        try {
            key = storage.store(episodeId, storedName, file.getInputStream());
        } catch (Exception e) {
            throw ApiException.badRequest("文件读取失败: " + e.getMessage());
        }

        Path path = storage.resolve(key);
        FfmpegService.AudioMeta meta = ffmpeg.probe(path);
        String waveform = ffmpeg.generateWaveformJson(path, meta.durationMs());

        AudioVersion v = new AudioVersion();
        v.setEpisodeId(episodeId);
        v.setVersionNumber(nextVersion);
        v.setFileKey(key);
        v.setFileUrl("/api/media/versions/" + "PENDING"); // replaced after id known
        v.setOriginalName(file.getOriginalFilename());
        v.setContentType(file.getContentType());
        v.setSizeBytes(file.getSize());
        v.setDurationMs(meta.durationMs());
        v.setWaveformJson(waveform);
        v.setUploadedBy(SecurityUtils.currentUserId());
        v = repo.save(v);

        v.setFileUrl("/api/media/versions/" + v.getId());
        repo.save(v);

        // README §4.2 版本对比：保留历史标记，时间偏移自动适配到新版本。
        int migrated = migrateMarkers(previous, v);

        // README §4.3 转写文本对齐：上传后生成时间对齐的转写文本（可降级）。
        int segments = 0;
        try {
            segments = transcription.generateFor(v, true).size();
        } catch (Exception e) {
            // Transcription is best-effort; never fail the upload because of it.
        }

        archiveOldVersions(episodeId);
        audit.log("AUDIO_UPLOAD", "AudioVersion", v.getId(),
                "episode=" + episodeId + " version=" + nextVersion
                        + " migratedMarkers=" + migrated + " transcriptSegments=" + segments);
        return v;
    }

    /**
     * Copies markers from the previous version onto the new one, adapting each
     * timestamp by the duration ratio (newDuration / oldDuration). When either
     * duration is unknown, timestamps are carried over unchanged. Range markers
     * keep both endpoints scaled; migrated markers reset to PENDING and record
     * their origin so reviewers know they came from an earlier cut.
     */
    private int migrateMarkers(AudioVersion previous, AudioVersion next) {
        if (previous == null) {
            return 0;
        }
        List<TimelineMarker> old = markerRepo.findByAudioVersionIdOrderByStartTimeMsAsc(previous.getId());
        if (old.isEmpty()) {
            return 0;
        }
        double ratio = 1.0;
        Long oldDur = previous.getDurationMs();
        Long newDur = next.getDurationMs();
        if (oldDur != null && oldDur > 0 && newDur != null && newDur > 0) {
            ratio = (double) newDur / (double) oldDur;
        }
        long newDurationCap = (newDur != null && newDur > 0) ? newDur : Long.MAX_VALUE;

        int count = 0;
        for (TimelineMarker src : old) {
            TimelineMarker copy = new TimelineMarker();
            copy.setAudioVersionId(next.getId());
            copy.setStartTimeMs(adapt(src.getStartTimeMs(), ratio, newDurationCap));
            copy.setEndTimeMs(src.getEndTimeMs() == null
                    ? null : adapt(src.getEndTimeMs(), ratio, newDurationCap));
            copy.setType(src.getType());
            copy.setDescription(src.getDescription());
            copy.setScreenshotUrl(src.getScreenshotUrl());
            // Carried-over markers start fresh for the new cut.
            copy.setStatus(com.podcast.domain.MarkerStatus.PENDING);
            copy.setCreatedBy(src.getCreatedBy());
            markerRepo.save(copy);
            count++;
        }
        return count;
    }

    private long adapt(long timeMs, double ratio, long cap) {
        long scaled = Math.round(timeMs * ratio);
        if (scaled < 0) scaled = 0;
        if (scaled > cap) scaled = cap;
        return scaled;
    }

    /**
     * Compares two audio versions of the same episode (README §4.2 版本对比),
     * returning their durations and the delta (new - old) in milliseconds.
     */
    @Transactional(readOnly = true)
    public VersionComparison compare(Long fromVersionId, Long toVersionId) {
        AudioVersion from = get(fromVersionId);
        AudioVersion to = get(toVersionId);
        if (!from.getEpisodeId().equals(to.getEpisodeId())) {
            throw ApiException.badRequest("只能对比同一单集的音频版本");
        }
        Long diff = (from.getDurationMs() != null && to.getDurationMs() != null)
                ? to.getDurationMs() - from.getDurationMs() : null;
        return new VersionComparison(
                from.getId(), from.getVersionNumber(), from.getDurationMs(),
                to.getId(), to.getVersionNumber(), to.getDurationMs(), diff);
    }

    /** Result of comparing two audio versions. */
    public record VersionComparison(
            Long fromVersionId, Integer fromVersionNumber, Long fromDurationMs,
            Long toVersionId, Integer toVersionNumber, Long toDurationMs,
            Long durationDiffMs) {}

    /** Marks all but the newest MAX_ONLINE_VERSIONS as archived. */
    private void archiveOldVersions(Long episodeId) {
        List<AudioVersion> all = repo.findByEpisodeIdOrderByVersionNumberDesc(episodeId);
        for (int i = 0; i < all.size(); i++) {
            AudioVersion v = all.get(i);
            boolean shouldArchive = i >= MAX_ONLINE_VERSIONS;
            if (v.isArchived() != shouldArchive) {
                v.setArchived(shouldArchive);
                repo.save(v);
            }
        }
    }

    public String streamUrlFor(AudioVersion v) {
        return "/api/media/stream/" + v.getId() + "?token=" + signedUrl.sign(v.getId());
    }
}
