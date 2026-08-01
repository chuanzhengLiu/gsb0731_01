package com.podcast.collab.service;

import com.podcast.collab.dto.stats.EpisodeStatsResponse;
import com.podcast.collab.dto.stats.TeamStatsResponse;
import com.podcast.collab.entity.AudioVersion;
import com.podcast.collab.entity.Distribution;
import com.podcast.collab.entity.Episode;
import com.podcast.collab.entity.Platform;
import com.podcast.collab.entity.Podcast;
import com.podcast.collab.entity.Task;
import com.podcast.collab.entity.TimelineMarker;
import com.podcast.collab.entity.User;
import com.podcast.collab.entity.enums.DistributionStatus;
import com.podcast.collab.entity.enums.EpisodeStatus;
import com.podcast.collab.entity.enums.MarkerStatus;
import com.podcast.collab.entity.enums.TaskStatus;
import com.podcast.collab.exception.AccessDeniedException;
import com.podcast.collab.exception.ResourceNotFoundException;
import com.podcast.collab.repository.AudioVersionRepository;
import com.podcast.collab.repository.DistributionRepository;
import com.podcast.collab.repository.EpisodeRepository;
import com.podcast.collab.repository.PlatformRepository;
import com.podcast.collab.repository.PodcastRepository;
import com.podcast.collab.repository.TaskRepository;
import com.podcast.collab.repository.TeamMemberRepository;
import com.podcast.collab.repository.TimelineMarkerRepository;
import com.podcast.collab.repository.UserRepository;
import com.podcast.collab.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final EpisodeRepository episodeRepository;
    private final PodcastRepository podcastRepository;
    private final AudioVersionRepository audioVersionRepository;
    private final TimelineMarkerRepository timelineMarkerRepository;
    private final TaskRepository taskRepository;
    private final DistributionRepository distributionRepository;
    private final PlatformRepository platformRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    public StatsService(EpisodeRepository episodeRepository,
                        PodcastRepository podcastRepository,
                        AudioVersionRepository audioVersionRepository,
                        TimelineMarkerRepository timelineMarkerRepository,
                        TaskRepository taskRepository,
                        DistributionRepository distributionRepository,
                        PlatformRepository platformRepository,
                        TeamMemberRepository teamMemberRepository,
                        UserRepository userRepository) {
        this.episodeRepository = episodeRepository;
        this.podcastRepository = podcastRepository;
        this.audioVersionRepository = audioVersionRepository;
        this.timelineMarkerRepository = timelineMarkerRepository;
        this.taskRepository = taskRepository;
        this.distributionRepository = distributionRepository;
        this.platformRepository = platformRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public EpisodeStatsResponse getEpisodeStats(Long episodeId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Episode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found"));
        Podcast podcast = podcastRepository.findById(episode.getPodcastId())
                .orElseThrow(() -> new ResourceNotFoundException("Podcast not found"));
        verifyTeamMembership(podcast.getTeamId(), userId);

        Long durationMs = audioVersionRepository.findTopByEpisodeIdOrderByVersionNumberDesc(episodeId)
                .map(AudioVersion::getDurationMs)
                .orElse(0L);

        long totalMarkers = timelineMarkerRepository.countByEpisodeId(episodeId);
        long pendingMarkers = timelineMarkerRepository.countByEpisodeIdAndStatus(episodeId, MarkerStatus.PENDING);
        long resolvedMarkers = timelineMarkerRepository.countByEpisodeIdAndStatus(episodeId, MarkerStatus.RESOLVED);
        long taskCount = taskRepository.countByEpisodeId(episodeId);

        Long daysFromRecordToPublish = null;
        if (episode.getRecordDate() != null) {
            LocalDate endDate;
            if (episode.getStatus() == EpisodeStatus.PUBLISHED && episode.getUpdatedAt() != null) {
                endDate = episode.getUpdatedAt().toLocalDate();
            } else {
                endDate = LocalDate.now();
            }
            daysFromRecordToPublish = Duration.between(
                    episode.getRecordDate().atStartOfDay(),
                    endDate.atStartOfDay()
            ).toDays();
        }

        long distributionCount = distributionRepository.countByEpisodeId(episodeId);
        long publishedPlatformCount = distributionRepository.countByEpisodeIdAndStatus(
                episodeId, DistributionStatus.PUBLISHED);

        return EpisodeStatsResponse.builder()
                .episodeId(episode.getId())
                .title(episode.getTitle())
                .durationMs(durationMs)
                .totalMarkers(totalMarkers)
                .pendingMarkers(pendingMarkers)
                .resolvedMarkers(resolvedMarkers)
                .taskCount(taskCount)
                .daysFromRecordToPublish(daysFromRecordToPublish)
                .distributionCount(distributionCount)
                .publishedPlatformCount(publishedPlatformCount)
                .build();
    }

    @Transactional(readOnly = true)
    public TeamStatsResponse getTeamStats(Long teamId) {
        Long userId = SecurityUtils.getCurrentUserId();
        verifyTeamMembership(teamId, userId);

        List<Podcast> podcasts = podcastRepository.findByTeamId(teamId);
        List<Long> podcastIds = podcasts.stream().map(Podcast::getId).toList();

        List<Episode> episodes = podcastIds.stream()
                .flatMap(pid -> episodeRepository.findByPodcastIdOrderByNumberDesc(pid).stream())
                .toList();
        List<Long> episodeIds = episodes.stream().map(Episode::getId).toList();

        long totalPodcasts = podcasts.size();
        long totalEpisodes = episodes.size();

        List<TimelineMarker> allMarkers = episodeIds.isEmpty()
                ? List.of()
                : timelineMarkerRepository.findByEpisodeIdIn(episodeIds);
        long totalMarkers = allMarkers.size();

        Double avgResolutionTimeHours = allMarkers.stream()
                .filter(m -> m.getStatus() == MarkerStatus.RESOLVED && m.getResolvedAt() != null && m.getCreatedAt() != null)
                .mapToDouble(m -> Duration.between(m.getCreatedAt(), m.getResolvedAt()).toMinutes() / 60.0)
                .average()
                .orElse(0.0);

        Map<Long, Long> markersByUser = allMarkers.stream()
                .collect(Collectors.groupingBy(TimelineMarker::getCreatedBy, Collectors.counting()));

        List<Map<String, Object>> markersPerUser = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : markersByUser.entrySet()) {
            String name = userRepository.findById(entry.getKey())
                    .map(User::getName)
                    .orElse("Unknown");
            Map<String, Object> userStat = new LinkedHashMap<>();
            userStat.put("userId", entry.getKey());
            userStat.put("name", name);
            userStat.put("count", entry.getValue());
            markersPerUser.add(userStat);
        }

        List<Task> allTasks = episodeIds.isEmpty()
                ? List.of()
                : taskRepository.findByEpisodeIdIn(episodeIds);
        long overdueTasks = allTasks.stream()
                .filter(t -> t.getDueDate() != null
                        && t.getDueDate().isBefore(LocalDateTime.now())
                        && t.getStatus() != TaskStatus.DONE)
                .count();

        List<Distribution> allDistributions = episodeIds.isEmpty()
                ? List.of()
                : distributionRepository.findByEpisodeIdIn(episodeIds);

        Map<Long, List<Distribution>> distributionsByPlatform = allDistributions.stream()
                .collect(Collectors.groupingBy(Distribution::getPlatformId));

        List<Map<String, Object>> platformCoverage = new ArrayList<>();
        for (Map.Entry<Long, List<Distribution>> entry : distributionsByPlatform.entrySet()) {
            String platformName = platformRepository.findById(entry.getKey())
                    .map(Platform::getName)
                    .orElse("Unknown");
            long total = entry.getValue().size();
            long published = entry.getValue().stream()
                    .filter(d -> d.getStatus() == DistributionStatus.PUBLISHED)
                    .count();
            Map<String, Object> coverage = new LinkedHashMap<>();
            coverage.put("platformName", platformName);
            coverage.put("count", total);
            coverage.put("publishedCount", published);
            platformCoverage.add(coverage);
        }

        return TeamStatsResponse.builder()
                .totalPodcasts(totalPodcasts)
                .totalEpisodes(totalEpisodes)
                .totalMarkers(totalMarkers)
                .avgResolutionTimeHours(avgResolutionTimeHours)
                .markersPerUser(markersPerUser)
                .overdueTasks(overdueTasks)
                .platformCoverage(platformCoverage)
                .build();
    }

    private void verifyTeamMembership(Long teamId, Long userId) {
        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            throw new AccessDeniedException("You are not a member of this team");
        }
    }
}
