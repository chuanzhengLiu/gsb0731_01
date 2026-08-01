package com.podcast.collab.service;

import com.podcast.collab.entity.AudioVersion;
import com.podcast.collab.entity.Episode;
import com.podcast.collab.entity.TimelineMarker;
import com.podcast.collab.repository.AudioVersionRepository;
import com.podcast.collab.repository.EpisodeRepository;
import com.podcast.collab.repository.TimelineMarkerRepository;
import com.podcast.collab.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioService {
    private final AudioVersionRepository audioVersionRepository;
    private final TimelineMarkerRepository timelineMarkerRepository;
    private final EpisodeRepository episodeRepository;
    private final WaveformService waveformService;
    private final AuditService auditService;
    private final TeamGuard teamGuard;

    private static final int MAX_ACTIVE_VERSIONS = 10;
    private static final Map<String, byte[]> MAGIC_BYTES = Map.of(
            "wav", new byte[]{0x52, 0x49, 0x46, 0x46},           // RIFF
            "mp3", new byte[]{(byte) 0xFF, (byte) 0xFB},          // MPEG frame sync (简化判断)
            "m4a", new byte[]{0x66, 0x74, 0x79, 0x70}            // ftyp (offset 4)
    );

    @Value("${app.upload-dir}")
    private String uploadDir;
    @Value("${app.max-audio-size-bytes}")
    private long maxAudioSize;

    @Transactional
    public AudioVersion upload(Long episodeId, MultipartFile file) throws IOException {
        Episode episode = teamGuard.requireEpisode(episodeId);
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = extensionOf(original);
        if (!MAGIC_BYTES.containsKey(ext)) {
            throw new IllegalArgumentException("仅支持 WAV/MP3/M4A 格式");
        }
        if (file.getSize() > maxAudioSize) {
            throw new IllegalArgumentException("文件大小不能超过 500MB");
        }
        validateFileHeader(file, ext);

        Path dir = Paths.get(uploadDir, "episodes", String.valueOf(episodeId));
        Files.createDirectories(dir);
        String filename = UUID.randomUUID() + "." + ext;
        Path target = dir.resolve(filename);
        file.transferTo(target);

        int nextVersion = audioVersionRepository.findTopByEpisodeIdOrderByVersionNumberDesc(episodeId)
                .map(v -> v.getVersionNumber() + 1).orElse(1);

        AudioVersion version = new AudioVersion();
        version.setEpisodeId(episodeId);
        version.setVersionNumber(nextVersion);
        version.setFileUrl(target.toString());
        version.setUploadedBy(SecurityUtils.currentUserId());

        // 元数据 + 波形（预生成，保证1小时音频波形秒级展示）
        WaveformService.AudioMeta meta = waveformService.extractMeta(target);
        version.setDurationMs(meta.durationMs());
        version.setSampleRate(meta.sampleRate());
        version.setWaveformJson(waveformService.generatePeaksJson(target));

        AudioVersion saved = audioVersionRepository.save(version);
        saved.setFileSize(file.getSize());
        saved = audioVersionRepository.save(saved);

        // 回写单集成片音频地址（RSS enclosure 使用）
        episode.setFinalAudioUrl("/api/audio/stream/" + saved.getId());
        episodeRepository.save(episode);

        // 版本对比 + 历史标记迁移：时间偏移按新旧时长比例自动适配
        migrateMarkersToNewVersion(episodeId, saved);

        // 每集仅保留最近10个版本在线，旧版本归档（可下载但不在线播放）
        archiveOldVersions(episodeId);

        Long teamId = teamGuard.requirePodcast(episode.getPodcastId()).getTeamId();
        auditService.log(SecurityUtils.currentUserId(), teamId, "AUDIO_UPLOAD",
                "audio_version", saved.getId(),
                "单集" + episodeId + " 上传版本 v" + nextVersion + " 文件 " + original);
        return saved;
    }

    /** 上传新版本后，把上一个版本的未关闭标记复制到新版本，并按时长比例偏移 */
    private void migrateMarkersToNewVersion(Long episodeId, AudioVersion newVersion) {
        List<AudioVersion> versions = audioVersionRepository.findByEpisodeIdOrderByVersionNumberDesc(episodeId);
        if (versions.size() < 2) {
            return;
        }
        AudioVersion prev = versions.stream()
                .filter(v -> !v.getId().equals(newVersion.getId()))
                .findFirst().orElse(null);
        if (prev == null) {
            return;
        }
        double ratio = 1.0;
        if (prev.getDurationMs() != null && prev.getDurationMs() > 0 && newVersion.getDurationMs() != null) {
            ratio = (double) newVersion.getDurationMs() / prev.getDurationMs();
        }
        List<TimelineMarker> oldMarkers = timelineMarkerRepository.findByAudioVersionIdOrderByStartTimeMs(prev.getId());
        for (TimelineMarker old : oldMarkers) {
            if (old.getStatus() == com.podcast.collab.entity.MarkerStatus.RESOLVED
                    || old.getStatus() == com.podcast.collab.entity.MarkerStatus.IGNORED) {
                continue; // 已关闭的标记不迁移
            }
            TimelineMarker copy = new TimelineMarker();
            copy.setAudioVersionId(newVersion.getId());
            copy.setEpisodeId(episodeId);
            copy.setStartTimeMs(Math.round(old.getStartTimeMs() * ratio));
            copy.setEndTimeMs(old.getEndTimeMs() == null ? null : Math.round(old.getEndTimeMs() * ratio));
            copy.setType(old.getType());
            copy.setDescription(old.getDescription());
            copy.setStatus(com.podcast.collab.entity.MarkerStatus.PENDING);
            copy.setCreatedBy(old.getCreatedBy());
            timelineMarkerRepository.save(copy);
        }
    }

    private void archiveOldVersions(Long episodeId) {
        List<AudioVersion> active = audioVersionRepository
                .findByEpisodeIdAndStatusOrderByVersionNumberDesc(episodeId, "ACTIVE");
        for (int i = MAX_ACTIVE_VERSIONS; i < active.size(); i++) {
            AudioVersion old = active.get(i);
            old.setStatus("ARCHIVED");
            audioVersionRepository.save(old);
        }
    }

    private String extensionOf(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx < 0 ? "" : filename.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    /** 文件头校验：防止伪装扩展名上传 */
    private void validateFileHeader(MultipartFile file, String ext) throws IOException {
        byte[] header = new byte[12];
        try (InputStream in = file.getInputStream()) {
            int read = in.read(header);
            if (read < 4) {
                throw new IllegalArgumentException("文件内容不完整");
            }
        }
        byte[] magic = MAGIC_BYTES.get(ext);
        int offset = "m4a".equals(ext) ? 4 : 0;
        boolean match = true;
        for (int i = 0; i < magic.length; i++) {
            if (header[offset + i] != magic[i]) {
                match = false;
                break;
            }
        }
        // MP3 也可能是 ID3 标签开头
        if (!match && "mp3".equals(ext)) {
            match = header[0] == 0x49 && header[1] == 0x44 && header[2] == 0x33; // ID3
        }
        if (!match) {
            throw new IllegalArgumentException("文件内容与扩展名不符，已拒绝上传");
        }
    }
}
