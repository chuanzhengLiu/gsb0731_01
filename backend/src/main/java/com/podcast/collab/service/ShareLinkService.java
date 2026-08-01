package com.podcast.collab.service;

import com.podcast.collab.dto.share.CreateShareLinkRequest;
import com.podcast.collab.dto.share.ShareAccessLogResponse;
import com.podcast.collab.dto.share.ShareLinkResponse;
import com.podcast.collab.dto.share.SharedEpisodeResponse;
import com.podcast.collab.entity.AudioVersion;
import com.podcast.collab.entity.Episode;
import com.podcast.collab.entity.Podcast;
import com.podcast.collab.entity.ShareAccessLog;
import com.podcast.collab.entity.ShareLink;
import com.podcast.collab.entity.TimelineMarker;
import com.podcast.collab.exception.AccessDeniedException;
import com.podcast.collab.exception.BadRequestException;
import com.podcast.collab.exception.ResourceNotFoundException;
import com.podcast.collab.repository.AudioVersionRepository;
import com.podcast.collab.repository.EpisodeRepository;
import com.podcast.collab.repository.PodcastRepository;
import com.podcast.collab.repository.ShareAccessLogRepository;
import com.podcast.collab.repository.ShareLinkRepository;
import com.podcast.collab.repository.TeamMemberRepository;
import com.podcast.collab.repository.TimelineMarkerRepository;
import com.podcast.collab.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ShareLinkService {

    private final ShareLinkRepository shareLinkRepository;
    private final ShareAccessLogRepository shareAccessLogRepository;
    private final EpisodeRepository episodeRepository;
    private final PodcastRepository podcastRepository;
    private final AudioVersionRepository audioVersionRepository;
    private final TimelineMarkerRepository timelineMarkerRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AuditService auditService;

    public ShareLinkService(ShareLinkRepository shareLinkRepository,
                            ShareAccessLogRepository shareAccessLogRepository,
                            EpisodeRepository episodeRepository,
                            PodcastRepository podcastRepository,
                            AudioVersionRepository audioVersionRepository,
                            TimelineMarkerRepository timelineMarkerRepository,
                            TeamMemberRepository teamMemberRepository,
                            AuditService auditService) {
        this.shareLinkRepository = shareLinkRepository;
        this.shareAccessLogRepository = shareAccessLogRepository;
        this.episodeRepository = episodeRepository;
        this.podcastRepository = podcastRepository;
        this.audioVersionRepository = audioVersionRepository;
        this.timelineMarkerRepository = timelineMarkerRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ShareLinkResponse createShareLink(CreateShareLinkRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Episode episode = findEpisodeOrThrow(request.getEpisodeId());
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());
        verifyTeamMembership(podcast.getTeamId(), userId);

        ShareLink shareLink = ShareLink.builder()
                .episodeId(request.getEpisodeId())
                .token(UUID.randomUUID().toString())
                .createdBy(userId)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .accessCount(0)
                .isRevoked(false)
                .build();
        shareLink = shareLinkRepository.save(shareLink);

        auditService.log(userId, "CREATE_SHARE_LINK", "ShareLink", shareLink.getId(),
                "Share link created for episode " + request.getEpisodeId());

        return mapToResponse(shareLink, episode.getTitle());
    }

    @Transactional(readOnly = true)
    public List<ShareLinkResponse> listByEpisode(Long episodeId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Episode episode = findEpisodeOrThrow(episodeId);
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());
        verifyTeamMembership(podcast.getTeamId(), userId);

        return shareLinkRepository.findByEpisodeId(episodeId).stream()
                .map(sl -> mapToResponse(sl, episode.getTitle()))
                .toList();
    }

    @Transactional
    public ShareLinkResponse revoke(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        ShareLink shareLink = findShareLinkOrThrow(id);
        Episode episode = findEpisodeOrThrow(shareLink.getEpisodeId());
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());
        verifyTeamMembership(podcast.getTeamId(), userId);

        shareLink.setIsRevoked(true);
        shareLink = shareLinkRepository.save(shareLink);

        auditService.log(userId, "REVOKE_SHARE_LINK", "ShareLink", id,
                "Share link revoked");

        return mapToResponse(shareLink, episode.getTitle());
    }

    @Transactional
    public SharedEpisodeResponse getByToken(String token, String ipAddress, String userAgent) {
        ShareLink shareLink = validateAndLogAccess(token, ipAddress, userAgent);

        Episode episode = findEpisodeOrThrow(shareLink.getEpisodeId());
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());

        SharedEpisodeResponse.SharedAudioVersion latestVersion = null;
        AudioVersion latest = audioVersionRepository
                .findTopByEpisodeIdOrderByVersionNumberDesc(episode.getId())
                .orElse(null);
        if (latest != null) {
            latestVersion = SharedEpisodeResponse.SharedAudioVersion.builder()
                    .id(latest.getId())
                    .versionNumber(latest.getVersionNumber())
                    .fileUrl(latest.getFileUrl())
                    .durationMs(latest.getDurationMs())
                    .waveformUrl(latest.getWaveformUrl())
                    .createdAt(latest.getCreatedAt())
                    .build();
        }

        List<SharedEpisodeResponse.SharedMarker> markers = timelineMarkerRepository
                .findByEpisodeId(episode.getId()).stream()
                .map(this::mapToSharedMarker)
                .toList();

        return SharedEpisodeResponse.builder()
                .episodeId(episode.getId())
                .number(episode.getNumber())
                .title(episode.getTitle())
                .status(episode.getStatus())
                .finalAudioUrl(episode.getFinalAudioUrl())
                .podcastName(podcast.getName())
                .latestAudioVersion(latestVersion)
                .markers(markers)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ShareAccessLogResponse> getAccessLogs(Long shareLinkId) {
        Long userId = SecurityUtils.getCurrentUserId();
        ShareLink shareLink = findShareLinkOrThrow(shareLinkId);
        Episode episode = findEpisodeOrThrow(shareLink.getEpisodeId());
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());
        verifyTeamMembership(podcast.getTeamId(), userId);

        return shareAccessLogRepository.findByShareLinkIdOrderByAccessedAtDesc(shareLinkId).stream()
                .map(this::mapToLogResponse)
                .toList();
    }

    @Transactional
    public void validateShareTokenForVersion(String token, Long versionId, String ipAddress, String userAgent) {
        ShareLink shareLink = validateAndLogAccess(token, ipAddress, userAgent);
        AudioVersion version = audioVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Audio version not found"));
        if (!version.getEpisodeId().equals(shareLink.getEpisodeId())) {
            throw new AccessDeniedException("This audio version does not belong to the shared episode");
        }
    }

    private ShareLink validateAndLogAccess(String token, String ipAddress, String userAgent) {
        ShareLink shareLink = shareLinkRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found"));

        if (Boolean.TRUE.equals(shareLink.getIsRevoked())) {
            throw new BadRequestException("Share link has been revoked");
        }
        if (shareLink.getExpiresAt() != null && shareLink.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Share link has expired");
        }

        shareLink.setAccessCount(shareLink.getAccessCount() + 1);
        shareLink.setLastAccessedAt(LocalDateTime.now());
        shareLinkRepository.save(shareLink);

        ShareAccessLog accessLog = ShareAccessLog.builder()
                .shareLinkId(shareLink.getId())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        shareAccessLogRepository.save(accessLog);

        return shareLink;
    }

    private SharedEpisodeResponse.SharedMarker mapToSharedMarker(TimelineMarker marker) {
        return SharedEpisodeResponse.SharedMarker.builder()
                .id(marker.getId())
                .startTimeMs(marker.getStartTimeMs())
                .endTimeMs(marker.getEndTimeMs())
                .type(marker.getType())
                .description(marker.getDescription())
                .status(marker.getStatus())
                .build();
    }

    private ShareAccessLogResponse mapToLogResponse(ShareAccessLog log) {
        return ShareAccessLogResponse.builder()
                .id(log.getId())
                .shareLinkId(log.getShareLinkId())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .accessedAt(log.getAccessedAt())
                .build();
    }

    private ShareLinkResponse mapToResponse(ShareLink shareLink, String episodeTitle) {
        return ShareLinkResponse.builder()
                .id(shareLink.getId())
                .episodeId(shareLink.getEpisodeId())
                .token(shareLink.getToken())
                .createdBy(shareLink.getCreatedBy())
                .expiresAt(shareLink.getExpiresAt())
                .lastAccessedAt(shareLink.getLastAccessedAt())
                .accessCount(shareLink.getAccessCount())
                .isRevoked(shareLink.getIsRevoked())
                .createdAt(shareLink.getCreatedAt())
                .episodeTitle(episodeTitle)
                .build();
    }

    private ShareLink findShareLinkOrThrow(Long id) {
        return shareLinkRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found"));
    }

    private Episode findEpisodeOrThrow(Long episodeId) {
        return episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));
    }

    private Podcast findPodcastOrThrow(Long podcastId) {
        return podcastRepository.findById(podcastId)
                .orElseThrow(() -> new ResourceNotFoundException("Podcast not found"));
    }

    private void verifyTeamMembership(Long teamId, Long userId) {
        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            throw new AccessDeniedException("You are not a member of this team");
        }
    }
}
