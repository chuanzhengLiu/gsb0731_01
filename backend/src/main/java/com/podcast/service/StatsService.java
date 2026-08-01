package com.podcast.service;

import com.podcast.domain.*;
import com.podcast.repository.*;
import com.podcast.web.dto.StatsDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Team analytics (README §4.6). Reports production metrics — per-episode marker
 * counts / cycle time, per-member efficiency, and distribution coverage.
 * README §9 explicitly forbids play-count statistics, so none are computed.
 */
@Service
public class StatsService {

    private final PodcastRepository podcastRepo;
    private final EpisodeRepository episodeRepo;
    private final AudioVersionRepository audioRepo;
    private final TimelineMarkerRepository markerRepo;
    private final TaskRepository taskRepo;
    private final DistributionRepository distRepo;
    private final PlatformRepository platformRepo;
    private final UserRepository userRepo;
    private final AccessGuard accessGuard;

    public StatsService(PodcastRepository podcastRepo, EpisodeRepository episodeRepo,
                        AudioVersionRepository audioRepo, TimelineMarkerRepository markerRepo,
                        TaskRepository taskRepo, DistributionRepository distRepo,
                        PlatformRepository platformRepo, UserRepository userRepo,
                        AccessGuard accessGuard) {
        this.podcastRepo = podcastRepo;
        this.episodeRepo = episodeRepo;
        this.audioRepo = audioRepo;
        this.markerRepo = markerRepo;
        this.taskRepo = taskRepo;
        this.distRepo = distRepo;
        this.platformRepo = platformRepo;
        this.userRepo = userRepo;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public TeamStats teamStats() {
        Long teamId = accessGuard.requireTeamId();
        List<Podcast> podcasts = podcastRepo.findByTeamId(teamId);
        Map<Long, String> podcastNames = podcasts.stream()
                .collect(Collectors.toMap(Podcast::getId, Podcast::getName));
        List<Episode> episodes = podcasts.isEmpty() ? List.of()
                : episodeRepo.findByPodcastIdIn(new ArrayList<>(podcastNames.keySet()));
        List<Long> episodeIds = episodes.stream().map(Episode::getId).toList();

        // Preload audio versions + markers for all episodes to avoid N+1.
        List<AudioVersion> versions = episodeIds.isEmpty() ? List.of()
                : audioRepo.findByEpisodeIdIn(episodeIds);
        Map<Long, List<AudioVersion>> versionsByEpisode = versions.stream()
                .collect(Collectors.groupingBy(AudioVersion::getEpisodeId));
        List<Long> versionIds = versions.stream().map(AudioVersion::getId).toList();
        List<TimelineMarker> markers = versionIds.isEmpty() ? List.of()
                : markerRepo.findByAudioVersionIdIn(versionIds);
        Map<Long, Long> markerCountByEpisode = new HashMap<>();
        Map<Long, Long> versionEpisode = versions.stream()
                .collect(Collectors.toMap(AudioVersion::getId, AudioVersion::getEpisodeId));
        for (TimelineMarker m : markers) {
            Long epId = versionEpisode.get(m.getAudioVersionId());
            if (epId != null) {
                markerCountByEpisode.merge(epId, 1L, Long::sum);
            }
        }

        // Distributions across the team's episodes (for cycle days + coverage).
        List<Distribution> dists = episodeIds.isEmpty() ? List.of()
                : distRepo.findByEpisodeIdIn(episodeIds);
        Map<Long, List<Distribution>> distsByEpisode = dists.stream()
                .collect(Collectors.groupingBy(Distribution::getEpisodeId));

        List<EpisodeStat> episodeStats = new ArrayList<>();
        long totalMarkers = 0;
        for (Episode e : episodes) {
            long mc = markerCountByEpisode.getOrDefault(e.getId(), 0L);
            totalMarkers += mc;
            List<AudioVersion> evs = versionsByEpisode.getOrDefault(e.getId(), List.of());
            Long duration = evs.stream()
                    .max(Comparator.comparingInt(AudioVersion::getVersionNumber))
                    .map(AudioVersion::getDurationMs).orElse(null);
            Integer cycleDays = cycleDays(e, distsByEpisode.getOrDefault(e.getId(), List.of()));
            episodeStats.add(new EpisodeStat(
                    e.getId(), e.getNumber(), e.getTitle(),
                    podcastNames.get(e.getPodcastId()),
                    duration, mc, evs.size(), cycleDays));
        }

        // Per-member efficiency.
        List<User> members = userRepo.findByTeamIdOrderByCreatedAtAsc(teamId);
        Map<Long, Long> markersByUser = markers.stream()
                .filter(m -> m.getCreatedBy() != null)
                .collect(Collectors.groupingBy(TimelineMarker::getCreatedBy, Collectors.counting()));
        List<MemberEfficiency> memberStats = new ArrayList<>();
        long totalOverdue = 0;
        for (User u : members) {
            List<Task> tasks = taskRepo.findByAssigneeId(u.getId());
            long open = tasks.stream().filter(t -> t.getStatus() != TaskStatus.DONE).count();
            long overdue = tasks.stream().filter(this::isOverdue).count();
            totalOverdue += overdue;
            memberStats.add(new MemberEfficiency(
                    u.getId(), u.getName(), u.getRole().name(),
                    markersByUser.getOrDefault(u.getId(), 0L), open, overdue));
        }

        // Distribution coverage per platform.
        Map<Long, String> platformNames = platformRepo.findAll().stream()
                .collect(Collectors.toMap(Platform::getId, Platform::getName));
        Map<Long, List<Distribution>> distsByPlatform = dists.stream()
                .collect(Collectors.groupingBy(Distribution::getPlatformId));
        List<PlatformCoverage> platformStats = new ArrayList<>();
        for (Map.Entry<Long, List<Distribution>> entry : distsByPlatform.entrySet()) {
            List<Distribution> list = entry.getValue();
            long total = list.size();
            long published = list.stream()
                    .filter(d -> d.getStatus() == DistributionStatus.PUBLISHED).count();
            double rate = total > 0 ? (double) published / total : 0.0;
            Double avgReview = avgReviewHours(list);
            platformStats.add(new PlatformCoverage(
                    entry.getKey(), platformNames.getOrDefault(entry.getKey(), "未知平台"),
                    total, published, rate, avgReview));
        }

        double avgMarkers = episodes.isEmpty() ? 0.0 : (double) totalMarkers / episodes.size();
        return new TeamStats(episodeStats, memberStats, platformStats, avgMarkers, totalOverdue);
    }

    private boolean isOverdue(Task t) {
        return t.getStatus() != TaskStatus.DONE
                && t.getDueDate() != null
                && t.getDueDate().isBefore(LocalDate.now());
    }

    /** Days from record date to first platform publish (README §4.6). */
    private Integer cycleDays(Episode e, List<Distribution> dists) {
        if (e.getRecordDate() == null) {
            return null;
        }
        return dists.stream()
                .map(Distribution::getPublishedAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .map(pub -> (int) ChronoUnit.DAYS.between(
                        e.getRecordDate(), pub.atOffset(ZoneOffset.UTC).toLocalDate()))
                .orElse(null);
    }

    /** Average submitted→published hours for the platform's distributions. */
    private Double avgReviewHours(List<Distribution> list) {
        List<Long> spans = list.stream()
                .filter(d -> d.getSubmittedAt() != null && d.getPublishedAt() != null)
                .map(d -> ChronoUnit.HOURS.between(d.getSubmittedAt(), d.getPublishedAt()))
                .toList();
        if (spans.isEmpty()) {
            return null;
        }
        return spans.stream().mapToLong(Long::longValue).average().orElse(0);
    }
}
