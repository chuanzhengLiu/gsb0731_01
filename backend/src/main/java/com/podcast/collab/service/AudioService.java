package com.podcast.collab.service;

import com.podcast.collab.common.ApiException;
import com.podcast.collab.common.ErrorCode;
import com.podcast.collab.config.AppProperties;
import com.podcast.collab.domain.*;
import com.podcast.collab.dto.AudioDtos.*;
import com.podcast.collab.repo.*;
import com.podcast.collab.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AudioService {

    private static final Logger log = LoggerFactory.getLogger(AudioService.class);
    private static final long MAX_AUDIO_SIZE = 500L * 1024 * 1024;
    private static final Set<String> ALLOWED_MIME = Set.of("audio/wav", "audio/x-wav", "audio/wave",
            "audio/mpeg", "audio/mp3", "audio/mp4", "audio/x-m4a", "audio/aac", "audio/mp4a-latm");
    private static final Set<String> ALLOWED_EXT = Set.of(".wav", ".mp3", ".m4a", ".aac");

    private final AudioVersionRepository versionRepo;
    private final EpisodeRepository episodeRepo;
    private final TimelineMarkerRepository markerRepo;
    private final UserRepository userRepo;
    private final StorageService storage;
    private final AudioProcessingService processing;
    private final TeamGuard teamGuard;
    private final AppProperties props;
    private final AuditService auditService;
    private final SignedUrlService signedUrlService;

    public AudioService(AudioVersionRepository versionRepo, EpisodeRepository episodeRepo,
                        TimelineMarkerRepository markerRepo, UserRepository userRepo,
                        StorageService storage, AudioProcessingService processing,
                        TeamGuard teamGuard, AppProperties props, AuditService auditService,
                        SignedUrlService signedUrlService) {
        this.versionRepo = versionRepo;
        this.episodeRepo = episodeRepo;
        this.markerRepo = markerRepo;
        this.userRepo = userRepo;
        this.storage = storage;
        this.processing = processing;
        this.teamGuard = teamGuard;
        this.props = props;
        this.auditService = auditService;
        this.signedUrlService = signedUrlService;
    }

    @Transactional
    public AudioVersionResponse upload(Long episodeId, MultipartFile file, CurrentUser user) {
        Episode episode = teamGuard.requireEpisode(episodeId, user);
        validateAudio(file);

        try {
            String original = file.getOriginalFilename() == null ? "audio" : file.getOriginalFilename();
            StorageService.StoredFile stored = storage.store(file.getInputStream(), "audio",
                    original, file.getContentType(), MAX_AUDIO_SIZE);
            Path audioPath = stored.absolutePath();

            long durationMs = processing.extractDurationMs(audioPath);
            int nextVersion = (int) versionRepo.countByEpisodeId(episodeId) + 1;

            String peaksRelative = null;
            try {
                String storedName = Paths.get(stored.relativePath()).getFileName().toString();
                String peaksName = storedName.replaceFirst("\\.[^.]+$", ".peaks.json");
                Path peaksTarget = storage.root().resolve("peaks").resolve(peaksName);
                processing.generatePeaks(audioPath, peaksTarget);
                peaksRelative = "peaks/" + peaksName;
            } catch (Exception e) {
                log.warn("Peak generation failed: {}", e.getMessage());
            }

            AudioVersion version = new AudioVersion();
            version.setEpisodeId(episodeId);
            version.setVersionNumber(nextVersion);
            version.setFileUrl(storage.toPublicUrl(stored.relativePath()));
            version.setFileName(original);
            version.setMimeType(file.getContentType());
            version.setFileSize(stored.size());
            version.setDurationMs(durationMs);
            version.setPeaksUrl(peaksRelative != null ? storage.toPublicUrl(peaksRelative) : null);
            version.setArchived(false);
            version.setUploadedBy(user.id());
            versionRepo.save(version);

            int migrated = migrateMarkers(episodeId, version, durationMs);

            archiveOldVersions(episodeId);

            episode.setStatus(Enums.EpisodeStatus.ROUGH_CUT);
            episodeRepo.save(episode);

            auditService.log(user, "AUDIO_UPLOAD", "AudioVersion", version.getId(),
                    Map.of("episodeId", episodeId, "version", nextVersion,
                            "durationMs", durationMs, "migratedMarkers", migrated));

            return toResponse(version, user.name());
        } catch (IOException e) {
            throw new ApiException(ErrorCode.INTERNAL, "Failed to store file: " + e.getMessage());
        }
    }

    private int migrateMarkers(Long episodeId, AudioVersion newVersion, long newDurationMs) {
        AudioVersion previous = versionRepo.findFirstByEpisodeIdOrderByVersionNumberDesc(episodeId)
                .filter(v -> !v.getId().equals(newVersion.getId())).orElse(null);
        if (previous == null || previous.getDurationMs() <= 0) return 0;
        double ratio = (double) newDurationMs / previous.getDurationMs();
        List<TimelineMarker> previousMarkers = markerRepo.findByAudioVersionIdOrderByStartTimeMsAsc(previous.getId());
        int count = 0;
        for (TimelineMarker old : previousMarkers) {
            if (old.getStatus() == Enums.MarkerStatus.RESOLVED
                    || old.getStatus() == Enums.MarkerStatus.IGNORED) {
                continue;
            }
            TimelineMarker migrated = new TimelineMarker();
            migrated.setAudioVersionId(newVersion.getId());
            migrated.setStartTimeMs((long) Math.min(newDurationMs, old.getStartTimeMs() * ratio));
            if (old.getEndTimeMs() != null) {
                migrated.setEndTimeMs((long) Math.min(newDurationMs, old.getEndTimeMs() * ratio));
            }
            migrated.setType(old.getType());
            migrated.setDescription(old.getDescription());
            migrated.setStatus(Enums.MarkerStatus.PENDING);
            migrated.setCreatedBy(old.getCreatedBy());
            markerRepo.save(migrated);
            count++;
        }
        return count;
    }

    private void archiveOldVersions(Long episodeId) {
        List<AudioVersion> versions = versionRepo.findByEpisodeIdOrderByVersionNumberDesc(episodeId);
        int keep = props.getAudio().getMaxVersions();
        for (int i = 0; i < versions.size(); i++) {
            AudioVersion v = versions.get(i);
            boolean shouldArchive = i >= keep;
            if (v.isArchived() != shouldArchive) {
                v.setArchived(shouldArchive);
                versionRepo.save(v);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<AudioVersionResponse> listVersions(Long episodeId, CurrentUser user) {
        teamGuard.requireEpisode(episodeId, user);
        return versionRepo.findByEpisodeIdOrderByVersionNumberDesc(episodeId).stream()
                .map(v -> {
                    String uploader = userRepo.findById(v.getUploadedBy()).map(User::getName).orElse("");
                    return toResponse(v, uploader);
                }).toList();
    }

    @Transactional
    public void setFinal(Long episodeId, Long versionId, CurrentUser user) {
        Episode episode = teamGuard.requireEpisode(episodeId, user);
        AudioVersion v = versionRepo.findById(versionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Version not found"));
        if (!v.getEpisodeId().equals(episodeId)) {
            throw new ApiException(ErrorCode.TEAM_MISMATCH);
        }
        episode.setFinalAudioUrl(v.getFileUrl());
        episode.setStatus(Enums.EpisodeStatus.FINALIZED);
        episodeRepo.save(episode);
        auditService.log(user, "AUDIO_SET_FINAL", "Episode", episodeId, Map.of("versionId", versionId));
    }

    @Transactional
    public void archiveVersion(Long versionId, CurrentUser user) {
        AudioVersion v = versionRepo.findById(versionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        teamGuard.requireEpisode(v.getEpisodeId(), user);
        v.setArchived(true);
        versionRepo.save(v);
        auditService.log(user, "AUDIO_ARCHIVE", "AudioVersion", versionId, null);
    }

    public AudioVersion requireVersion(Long versionId, CurrentUser user) {
        AudioVersion v = versionRepo.findById(versionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Version not found"));
        teamGuard.requireEpisode(v.getEpisodeId(), user);
        return v;
    }

    private void validateAudio(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_FILE, "No file provided");
        }
        if (file.getSize() > MAX_AUDIO_SIZE) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE);
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        boolean extOk = ALLOWED_EXT.stream().anyMatch(filename::endsWith);
        boolean mimeOk = file.getContentType() != null && ALLOWED_MIME.contains(file.getContentType().toLowerCase());
        if (!extOk && !mimeOk) {
            throw new ApiException(ErrorCode.BAD_FILE, "Only WAV, MP3 and M4A files are allowed");
        }
    }

    private AudioVersionResponse toResponse(AudioVersion v, String uploaderName) {
        // Return short-lived signed URLs so audio cannot be accessed without a token.
        // Archived versions expose no streamable fileUrl; only the download URL works.
        String streamUrl = v.isArchived() ? null : signedUrlService.signStream(v.getFileUrl());
        String peaksUrl = v.getPeaksUrl() != null ? signedUrlService.signStream(v.getPeaksUrl()) : null;
        String downloadUrl = signedUrlService.signDownload(v.getFileUrl());
        return new AudioVersionResponse(v.getId(), v.getEpisodeId(), v.getVersionNumber(), streamUrl,
                v.getFileName(), v.getMimeType(), v.getFileSize(), v.getDurationMs(), peaksUrl,
                v.isArchived(), v.getUploadedBy(), uploaderName, downloadUrl, v.getCreatedAt());
    }
}
