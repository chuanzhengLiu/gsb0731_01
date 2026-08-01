package com.podcast.collab.service;

import com.podcast.collab.common.ApiException;
import com.podcast.collab.common.ErrorCode;
import com.podcast.collab.domain.AudioVersion;
import com.podcast.collab.domain.Enums;
import com.podcast.collab.domain.TimelineMarker;
import com.podcast.collab.domain.User;
import com.podcast.collab.dto.AudioDtos.*;
import com.podcast.collab.repo.AudioVersionRepository;
import com.podcast.collab.repo.TimelineMarkerRepository;
import com.podcast.collab.repo.UserRepository;
import com.podcast.collab.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class MarkerService {

    private final TimelineMarkerRepository markerRepo;
    private final AudioVersionRepository versionRepo;
    private final UserRepository userRepo;
    private final TeamGuard teamGuard;
    private final AuditService auditService;

    public MarkerService(TimelineMarkerRepository markerRepo, AudioVersionRepository versionRepo,
                         UserRepository userRepo, TeamGuard teamGuard, AuditService auditService) {
        this.markerRepo = markerRepo;
        this.versionRepo = versionRepo;
        this.userRepo = userRepo;
        this.teamGuard = teamGuard;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<MarkerResponse> list(Long versionId, Enums.MarkerType type, Enums.MarkerStatus status,
                                     Long createdBy, String keyword, CurrentUser user) {
        AudioVersion version = requireVersion(versionId, user);
        List<TimelineMarker> markers = markerRepo.search(version.getId(), type, status, createdBy,
                keyword == null || keyword.isBlank() ? null : keyword.trim());
        Map<Long, String> names = userRepo.findAllById(markers.stream().map(TimelineMarker::getCreatedBy).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(User::getId, User::getName));
        return markers.stream().map(m -> toResponse(m, names.get(m.getCreatedBy()))).toList();
    }

    @Transactional
    public MarkerResponse create(Long versionId, CreateMarkerRequest req, CurrentUser user) {
        AudioVersion version = requireVersion(versionId, user);
        if (req.startTimeMs() < 0 || req.startTimeMs() > version.getDurationMs()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "startTimeMs out of range");
        }
        if (req.endTimeMs() != null) {
            if (req.endTimeMs() < req.startTimeMs()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "endTimeMs must be >= startTimeMs");
            }
            if (req.endTimeMs() > version.getDurationMs()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "endTimeMs out of range");
            }
        }
        TimelineMarker marker = new TimelineMarker();
        marker.setAudioVersionId(version.getId());
        marker.setStartTimeMs(req.startTimeMs());
        marker.setEndTimeMs(req.endTimeMs());
        marker.setType(req.type());
        marker.setDescription(req.description());
        marker.setStatus(Enums.MarkerStatus.PENDING);
        marker.setScreenshotUrl(req.screenshotUrl());
        marker.setCreatedBy(user.id());
        markerRepo.save(marker);
        auditService.log(user, "MARKER_CREATE", "TimelineMarker", marker.getId(),
                Map.of("versionId", versionId, "start", req.startTimeMs(), "type", req.type().name()));
        return toResponse(marker, user.name());
    }

    @Transactional
    public MarkerResponse update(Long versionId, Long markerId, UpdateMarkerRequest req, CurrentUser user) {
        TimelineMarker marker = requireOwned(versionId, markerId, user);
        if (req.startTimeMs() != null) marker.setStartTimeMs(req.startTimeMs());
        if (req.endTimeMs() != null) marker.setEndTimeMs(req.endTimeMs());
        if (req.type() != null) marker.setType(req.type());
        if (req.description() != null) marker.setDescription(req.description());
        if (req.screenshotUrl() != null) marker.setScreenshotUrl(req.screenshotUrl());
        if (req.status() != null) {
            Enums.MarkerStatus newStatus = req.status();
            marker.setStatus(newStatus);
            if (newStatus == Enums.MarkerStatus.RESOLVED && marker.getResolvedAt() == null) {
                marker.setResolvedBy(user.id());
                marker.setResolvedAt(Instant.now());
            }
        }
        markerRepo.save(marker);
        auditService.log(user, "MARKER_UPDATE", "TimelineMarker", markerId,
                Map.of("status", marker.getStatus().name()));
        String creatorName = userRepo.findById(marker.getCreatedBy()).map(User::getName).orElse("");
        return toResponse(marker, creatorName);
    }

    @Transactional
    public void delete(Long versionId, Long markerId, CurrentUser user) {
        TimelineMarker marker = requireOwned(versionId, markerId, user);
        markerRepo.delete(marker);
        auditService.log(user, "MARKER_DELETE", "TimelineMarker", markerId, null);
    }

    private TimelineMarker requireOwned(Long versionId, Long markerId, CurrentUser user) {
        requireVersion(versionId, user);
        TimelineMarker marker = markerRepo.findById(markerId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Marker not found"));
        if (!marker.getAudioVersionId().equals(versionId)) {
            throw new ApiException(ErrorCode.TEAM_MISMATCH);
        }
        if (!marker.getCreatedBy().equals(user.id()) && !user.isProducerOrAbove()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Only the creator or a producer can modify this marker");
        }
        return marker;
    }

    private AudioVersion requireVersion(Long versionId, CurrentUser user) {
        AudioVersion version = versionRepo.findById(versionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Audio version not found"));
        teamGuard.requireEpisode(version.getEpisodeId(), user);
        return version;
    }

    private MarkerResponse toResponse(TimelineMarker m, String creatorName) {
        return new MarkerResponse(m.getId(), m.getAudioVersionId(), m.getStartTimeMs(), m.getEndTimeMs(),
                m.getType(), m.getDescription(), m.getStatus(), m.getScreenshotUrl(),
                m.getCreatedBy(), creatorName, m.getResolvedBy(), m.getResolvedAt(), m.getCreatedAt());
    }
}
