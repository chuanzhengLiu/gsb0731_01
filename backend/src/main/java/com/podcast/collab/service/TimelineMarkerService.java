package com.podcast.collab.service;

import com.podcast.collab.dto.marker.CreateMarkerRequest;
import com.podcast.collab.dto.marker.MarkerResponse;
import com.podcast.collab.dto.marker.UpdateMarkerRequest;
import com.podcast.collab.entity.AudioVersion;
import com.podcast.collab.entity.Episode;
import com.podcast.collab.entity.Podcast;
import com.podcast.collab.entity.TeamMember;
import com.podcast.collab.entity.TimelineMarker;
import com.podcast.collab.entity.User;
import com.podcast.collab.entity.enums.MarkerStatus;
import com.podcast.collab.entity.enums.MarkerType;
import com.podcast.collab.entity.enums.TeamRole;
import com.podcast.collab.exception.AccessDeniedException;
import com.podcast.collab.exception.ResourceNotFoundException;
import com.podcast.collab.repository.AudioVersionRepository;
import com.podcast.collab.repository.EpisodeRepository;
import com.podcast.collab.repository.PodcastRepository;
import com.podcast.collab.repository.TeamMemberRepository;
import com.podcast.collab.repository.TimelineMarkerRepository;
import com.podcast.collab.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TimelineMarkerService {

    private final TimelineMarkerRepository timelineMarkerRepository;
    private final AudioVersionRepository audioVersionRepository;
    private final EpisodeRepository episodeRepository;
    private final PodcastRepository podcastRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public TimelineMarkerService(TimelineMarkerRepository timelineMarkerRepository,
                                 AudioVersionRepository audioVersionRepository,
                                 EpisodeRepository episodeRepository,
                                 PodcastRepository podcastRepository,
                                 TeamMemberRepository teamMemberRepository,
                                 UserRepository userRepository,
                                 AuditService auditService) {
        this.timelineMarkerRepository = timelineMarkerRepository;
        this.audioVersionRepository = audioVersionRepository;
        this.episodeRepository = episodeRepository;
        this.podcastRepository = podcastRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public MarkerResponse create(Long versionId, CreateMarkerRequest request, Long userId) {
        verifyVersionTeamAccess(versionId, userId);

        TimelineMarker marker = TimelineMarker.builder()
                .audioVersionId(versionId)
                .startTimeMs(request.getStartTimeMs())
                .endTimeMs(request.getEndTimeMs())
                .type(request.getType())
                .description(request.getDescription())
                .screenshotUrl(request.getScreenshotUrl())
                .status(MarkerStatus.PENDING)
                .assigneeId(request.getAssigneeId())
                .createdBy(userId)
                .build();
        marker = timelineMarkerRepository.save(marker);

        auditService.log(userId, "CREATE_MARKER", "TimelineMarker", marker.getId(),
                "Marker created at " + request.getStartTimeMs() + "ms");

        return mapToResponse(marker);
    }

    @Transactional(readOnly = true)
    public MarkerResponse getById(Long id, Long userId) {
        TimelineMarker marker = findMarkerOrThrow(id);
        verifyVersionTeamAccess(marker.getAudioVersionId(), userId);
        return mapToResponse(marker);
    }

    @Transactional(readOnly = true)
    public List<MarkerResponse> listByVersion(Long versionId, MarkerType type, MarkerStatus status,
                                              Long createdBy, String keyword, Long userId) {
        verifyVersionTeamAccess(versionId, userId);
        return timelineMarkerRepository.findFiltered(versionId, type, status, createdBy, keyword).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public MarkerResponse update(Long id, UpdateMarkerRequest request, Long userId) {
        TimelineMarker marker = findMarkerOrThrow(id);
        verifyVersionTeamAccess(marker.getAudioVersionId(), userId);

        MarkerStatus oldStatus = marker.getStatus();

        if (request.getStartTimeMs() != null) {
            marker.setStartTimeMs(request.getStartTimeMs());
        }
        if (request.getEndTimeMs() != null) {
            marker.setEndTimeMs(request.getEndTimeMs());
        }
        if (request.getType() != null) {
            marker.setType(request.getType());
        }
        if (request.getDescription() != null) {
            marker.setDescription(request.getDescription());
        }
        if (request.getAssigneeId() != null) {
            marker.setAssigneeId(request.getAssigneeId());
        }
        if (request.getStatus() != null) {
            marker.setStatus(request.getStatus());
        }

        if (request.getStatus() != null) {
            if (request.getStatus() == MarkerStatus.RESOLVED && oldStatus != MarkerStatus.RESOLVED) {
                marker.setResolvedAt(LocalDateTime.now());
            } else if (request.getStatus() != MarkerStatus.RESOLVED && oldStatus == MarkerStatus.RESOLVED) {
                marker.setResolvedAt(null);
            }
        }

        marker = timelineMarkerRepository.save(marker);

        auditService.log(userId, "UPDATE_MARKER", "TimelineMarker", marker.getId(),
                "Marker updated");

        return mapToResponse(marker);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        TimelineMarker marker = findMarkerOrThrow(id);
        verifyMarkerDeleteAccess(marker, userId);
        timelineMarkerRepository.delete(marker);

        auditService.log(userId, "DELETE_MARKER", "TimelineMarker", id,
                "Marker deleted");
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getMarkerStats(Long versionId, Long userId) {
        verifyVersionTeamAccess(versionId, userId);
        Map<String, Long> stats = new LinkedHashMap<>();
        long pending = timelineMarkerRepository.countByAudioVersionIdAndStatus(versionId, MarkerStatus.PENDING);
        long inProgress = timelineMarkerRepository.countByAudioVersionIdAndStatus(versionId, MarkerStatus.IN_PROGRESS);
        long resolved = timelineMarkerRepository.countByAudioVersionIdAndStatus(versionId, MarkerStatus.RESOLVED);
        long ignored = timelineMarkerRepository.countByAudioVersionIdAndStatus(versionId, MarkerStatus.IGNORED);
        stats.put("PENDING", pending);
        stats.put("IN_PROGRESS", inProgress);
        stats.put("RESOLVED", resolved);
        stats.put("IGNORED", ignored);
        stats.put("TOTAL", pending + inProgress + resolved + ignored);
        return stats;
    }

    private TimelineMarker findMarkerOrThrow(Long id) {
        return timelineMarkerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Marker not found"));
    }

    private AudioVersion findVersionOrThrow(Long versionId) {
        return audioVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Audio version not found"));
    }

    private Episode findEpisodeOrThrow(Long episodeId) {
        return episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));
    }

    private Podcast findPodcastOrThrow(Long podcastId) {
        return podcastRepository.findById(podcastId)
                .orElseThrow(() -> new ResourceNotFoundException("Podcast not found"));
    }

    private void verifyVersionTeamAccess(Long versionId, Long userId) {
        AudioVersion version = findVersionOrThrow(versionId);
        Episode episode = findEpisodeOrThrow(version.getEpisodeId());
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());
        if (!teamMemberRepository.existsByTeamIdAndUserId(podcast.getTeamId(), userId)) {
            throw new AccessDeniedException("You are not a member of this team");
        }
    }

    private void verifyMarkerDeleteAccess(TimelineMarker marker, Long userId) {
        AudioVersion version = findVersionOrThrow(marker.getAudioVersionId());
        Episode episode = findEpisodeOrThrow(version.getEpisodeId());
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());

        TeamMember membership = teamMemberRepository
                .findByTeamIdAndUserId(podcast.getTeamId(), userId)
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this team"));

        boolean isCreator = userId.equals(marker.getCreatedBy());
        boolean isProducerOrAdmin = membership.getRoleInTeam() == TeamRole.PRODUCER
                || membership.getRoleInTeam() == TeamRole.ADMIN;

        if (!isCreator && !isProducerOrAdmin) {
            throw new AccessDeniedException("Only the creator or a producer/admin can delete this marker");
        }
    }

    private MarkerResponse mapToResponse(TimelineMarker marker) {
        String createdByName = userRepository.findById(marker.getCreatedBy())
                .map(User::getName)
                .orElse(null);
        String assigneeName = null;
        if (marker.getAssigneeId() != null) {
            assigneeName = userRepository.findById(marker.getAssigneeId())
                    .map(User::getName)
                    .orElse(null);
        }
        return MarkerResponse.builder()
                .id(marker.getId())
                .audioVersionId(marker.getAudioVersionId())
                .startTimeMs(marker.getStartTimeMs())
                .endTimeMs(marker.getEndTimeMs())
                .type(marker.getType())
                .description(marker.getDescription())
                .screenshotUrl(marker.getScreenshotUrl())
                .status(marker.getStatus())
                .assigneeId(marker.getAssigneeId())
                .assigneeName(assigneeName)
                .createdBy(marker.getCreatedBy())
                .createdByName(createdByName)
                .resolvedAt(marker.getResolvedAt())
                .createdAt(marker.getCreatedAt())
                .updatedAt(marker.getUpdatedAt())
                .build();
    }
}
