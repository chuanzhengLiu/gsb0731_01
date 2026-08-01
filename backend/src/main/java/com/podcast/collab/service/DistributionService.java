package com.podcast.collab.service;

import com.podcast.collab.dto.distribution.CreateDistributionRequest;
import com.podcast.collab.dto.distribution.DistributionResponse;
import com.podcast.collab.dto.distribution.PlatformResponse;
import com.podcast.collab.dto.distribution.UpdateDistributionRequest;
import com.podcast.collab.dto.distribution.UpdateDistributionStatusRequest;
import com.podcast.collab.entity.AudioVersion;
import com.podcast.collab.entity.Distribution;
import com.podcast.collab.entity.Episode;
import com.podcast.collab.entity.Platform;
import com.podcast.collab.entity.Podcast;
import com.podcast.collab.entity.enums.DistributionStatus;
import com.podcast.collab.entity.enums.EpisodeStatus;
import com.podcast.collab.exception.AccessDeniedException;
import com.podcast.collab.exception.BadRequestException;
import com.podcast.collab.exception.ResourceNotFoundException;
import com.podcast.collab.repository.AudioVersionRepository;
import com.podcast.collab.repository.DistributionRepository;
import com.podcast.collab.repository.EpisodeRepository;
import com.podcast.collab.repository.PlatformRepository;
import com.podcast.collab.repository.PodcastRepository;
import com.podcast.collab.repository.TeamMemberRepository;
import com.podcast.collab.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DistributionService {

    private final DistributionRepository distributionRepository;
    private final PlatformRepository platformRepository;
    private final EpisodeRepository episodeRepository;
    private final PodcastRepository podcastRepository;
    private final AudioVersionRepository audioVersionRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AuditService auditService;

    public DistributionService(DistributionRepository distributionRepository,
                               PlatformRepository platformRepository,
                               EpisodeRepository episodeRepository,
                               PodcastRepository podcastRepository,
                               AudioVersionRepository audioVersionRepository,
                               TeamMemberRepository teamMemberRepository,
                               AuditService auditService) {
        this.distributionRepository = distributionRepository;
        this.platformRepository = platformRepository;
        this.episodeRepository = episodeRepository;
        this.podcastRepository = podcastRepository;
        this.audioVersionRepository = audioVersionRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.auditService = auditService;
    }

    @Transactional
    public DistributionResponse createDistribution(Long episodeId, CreateDistributionRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Episode episode = findEpisodeOrThrow(episodeId);
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());
        verifyTeamMembership(podcast.getTeamId(), userId);

        Platform platform = platformRepository.findById(request.getPlatformId())
                .orElseThrow(() -> new ResourceNotFoundException("Platform not found"));

        distributionRepository.findByEpisodeIdAndPlatformId(episodeId, request.getPlatformId())
                .ifPresent(d -> {
                    throw new BadRequestException("Distribution for this platform already exists for the episode");
                });

        Distribution distribution = Distribution.builder()
                .episodeId(episodeId)
                .platformId(request.getPlatformId())
                .status(DistributionStatus.NOT_STARTED)
                .platformDataJson(request.getPlatformDataJson())
                .createdBy(userId)
                .build();
        distribution = distributionRepository.save(distribution);

        auditService.log(userId, "CREATE_DISTRIBUTION", "Distribution", distribution.getId(),
                "Distribution created for platform: " + platform.getName());

        return mapToResponse(distribution, platform);
    }

    @Transactional(readOnly = true)
    public List<DistributionResponse> listByEpisode(Long episodeId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Episode episode = findEpisodeOrThrow(episodeId);
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());
        verifyTeamMembership(podcast.getTeamId(), userId);

        List<Distribution> distributions = distributionRepository.findByEpisodeId(episodeId);
        Map<Long, Platform> platformMap = platformRepository.findAllById(
                        distributions.stream().map(Distribution::getPlatformId).toList())
                .stream()
                .collect(Collectors.toMap(Platform::getId, Function.identity()));

        return distributions.stream()
                .map(d -> mapToResponse(d, platformMap.get(d.getPlatformId())))
                .toList();
    }

    @Transactional
    public DistributionResponse updateStatus(Long id, UpdateDistributionStatusRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Distribution distribution = findDistributionOrThrow(id);
        Episode episode = findEpisodeOrThrow(distribution.getEpisodeId());
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());
        verifyTeamMembership(podcast.getTeamId(), userId);

        distribution.setStatus(request.getStatus());

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        switch (request.getStatus()) {
            case SUBMITTED -> distribution.setSubmittedAt(now);
            case IN_REVIEW -> distribution.setReviewedAt(now);
            case PUBLISHED -> distribution.setPublishedAt(now);
            case REJECTED -> distribution.setRejectionReason(request.getRejectionReason());
            default -> {
            }
        }

        distribution = distributionRepository.save(distribution);

        auditService.log(userId, "UPDATE_DISTRIBUTION_STATUS", "Distribution", distribution.getId(),
                "Distribution status changed to " + request.getStatus());

        Platform platform = platformRepository.findById(distribution.getPlatformId()).orElse(null);
        return mapToResponse(distribution, platform);
    }

    @Transactional
    public DistributionResponse updateData(Long id, UpdateDistributionRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Distribution distribution = findDistributionOrThrow(id);
        Episode episode = findEpisodeOrThrow(distribution.getEpisodeId());
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());
        verifyTeamMembership(podcast.getTeamId(), userId);

        distribution.setPlatformDataJson(request.getPlatformDataJson());
        distribution = distributionRepository.save(distribution);

        auditService.log(userId, "UPDATE_DISTRIBUTION_DATA", "Distribution", distribution.getId(),
                "Distribution data updated");

        Platform platform = platformRepository.findById(distribution.getPlatformId()).orElse(null);
        return mapToResponse(distribution, platform);
    }

    @Transactional
    public void delete(Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        Distribution distribution = findDistributionOrThrow(id);
        Episode episode = findEpisodeOrThrow(distribution.getEpisodeId());
        Podcast podcast = findPodcastOrThrow(episode.getPodcastId());
        verifyTeamMembership(podcast.getTeamId(), userId);

        distributionRepository.delete(distribution);

        auditService.log(userId, "DELETE_DISTRIBUTION", "Distribution", id,
                "Distribution deleted");
    }

    @Transactional(readOnly = true)
    public List<PlatformResponse> listPlatforms() {
        return platformRepository.findByIsActiveTrue().stream()
                .map(this::mapToPlatformResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public String generateRssFeed(Long podcastId) {
        Podcast podcast = podcastRepository.findById(podcastId)
                .orElseThrow(() -> new ResourceNotFoundException("Podcast not found"));

        List<Episode> episodes = episodeRepository.findByPodcastIdOrderByNumberDesc(podcastId).stream()
                .filter(e -> (e.getStatus() == EpisodeStatus.FINALIZED || e.getStatus() == EpisodeStatus.PUBLISHED)
                        && e.getFinalAudioUrl() != null && !e.getFinalAudioUrl().isBlank())
                .toList();

        DateTimeFormatter rfc1123 = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<rss version=\"2.0\" xmlns:itunes=\"http://www.itunes.com/dtds/podcast-1.0.dtd\">\n");
        xml.append("<channel>\n");
        xml.append("  <title>").append(escapeXml(podcast.getName())).append("</title>\n");
        xml.append("  <description>").append(escapeXml(podcast.getDescription() != null ? podcast.getDescription() : "")).append("</description>\n");
        if (podcast.getCoverImageUrl() != null) {
            xml.append("  <itunes:image href=\"").append(escapeXml(podcast.getCoverImageUrl())).append("\"/>\n");
        }

        for (Episode episode : episodes) {
            AudioVersion latest = audioVersionRepository
                    .findTopByEpisodeIdOrderByVersionNumberDesc(episode.getId())
                    .orElse(null);

            xml.append("  <item>\n");
            xml.append("    <title>").append(escapeXml(episode.getTitle())).append("</title>\n");
            xml.append("    <description>").append(escapeXml(episode.getTheme() != null ? episode.getTheme() : "")).append("</description>\n");

            java.time.LocalDateTime pubDate = episode.getScheduledAt() != null
                    ? episode.getScheduledAt()
                    : (episode.getUpdatedAt() != null ? episode.getUpdatedAt() : episode.getCreatedAt());
            xml.append("    <pubDate>").append(rfc1123.format(pubDate.atOffset(ZoneOffset.UTC))).append("</pubDate>\n");

            if (latest != null && latest.getDurationMs() != null) {
                xml.append("    <itunes:duration>").append(formatDuration(latest.getDurationMs())).append("</itunes:duration>\n");
            }

            xml.append("    <enclosure url=\"").append(escapeXml(episode.getFinalAudioUrl()))
                    .append("\" length=\"").append(latest != null && latest.getFileSize() != null ? latest.getFileSize() : 0)
                    .append("\" type=\"audio/mpeg\"/>\n");
            xml.append("    <guid isPermaLink=\"false\">episode-").append(episode.getId()).append("</guid>\n");
            xml.append("  </item>\n");
        }

        xml.append("</channel>\n");
        xml.append("</rss>");
        return xml.toString();
    }

    private String formatDuration(Long durationMs) {
        long totalSeconds = durationMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private Distribution findDistributionOrThrow(Long id) {
        return distributionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Distribution not found"));
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

    private DistributionResponse mapToResponse(Distribution distribution, Platform platform) {
        return DistributionResponse.builder()
                .id(distribution.getId())
                .episodeId(distribution.getEpisodeId())
                .platformId(distribution.getPlatformId())
                .status(distribution.getStatus())
                .platformDataJson(distribution.getPlatformDataJson())
                .submittedAt(distribution.getSubmittedAt())
                .publishedAt(distribution.getPublishedAt())
                .reviewedAt(distribution.getReviewedAt())
                .rejectionReason(distribution.getRejectionReason())
                .createdBy(distribution.getCreatedBy())
                .createdAt(distribution.getCreatedAt())
                .updatedAt(distribution.getUpdatedAt())
                .platformName(platform != null ? platform.getName() : null)
                .platformDisplayName(platform != null ? platform.getDisplayName() : null)
                .build();
    }

    private PlatformResponse mapToPlatformResponse(Platform platform) {
        return PlatformResponse.builder()
                .id(platform.getId())
                .name(platform.getName())
                .displayName(platform.getDisplayName())
                .rssRequiredFieldsJson(platform.getRssRequiredFieldsJson())
                .categoryOptionsJson(platform.getCategoryOptionsJson())
                .isActive(platform.getIsActive())
                .createdAt(platform.getCreatedAt())
                .build();
    }
}
