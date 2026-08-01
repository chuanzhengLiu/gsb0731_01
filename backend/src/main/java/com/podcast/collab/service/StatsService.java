package com.podcast.collab.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.podcast.collab.domain.*;
import com.podcast.collab.dto.StatsDtos.*;
import com.podcast.collab.repo.*;
import com.podcast.collab.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class StatsService {

    private final PodcastRepository podcastRepo;
    private final EpisodeRepository episodeRepo;
    private final AudioVersionRepository versionRepo;
    private final TimelineMarkerRepository markerRepo;
    private final TaskRepository taskRepo;
    private final DistributionRepository distributionRepo;
    private final PlatformAccountRepository accountRepo;
    private final PlatformRepository platformRepo;
    private final UserRepository userRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StatsService(PodcastRepository podcastRepo, EpisodeRepository episodeRepo,
                        AudioVersionRepository versionRepo, TimelineMarkerRepository markerRepo,
                        TaskRepository taskRepo, DistributionRepository distributionRepo,
                        PlatformAccountRepository accountRepo, PlatformRepository platformRepo,
                        UserRepository userRepo) {
        this.podcastRepo = podcastRepo;
        this.episodeRepo = episodeRepo;
        this.versionRepo = versionRepo;
        this.markerRepo = markerRepo;
        this.taskRepo = taskRepo;
        this.distributionRepo = distributionRepo;
        this.accountRepo = accountRepo;
        this.platformRepo = platformRepo;
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public EpisodeStats episodeStats(Long episodeId, CurrentUser user) {
        Episode e = episodeRepo.findById(episodeId).orElseThrow();
        Podcast p = podcastRepo.findById(e.getPodcastId()).orElseThrow();
        if (!p.getTeamId().equals(user.teamId())) {
            throw new com.podcast.collab.common.ApiException(com.podcast.collab.common.ErrorCode.TEAM_MISMATCH);
        }
        List<AudioVersion> versions = versionRepo.findByEpisodeIdOrderByVersionNumberDesc(episodeId);
        long total = 0, resolved = 0, pending = 0;
        Map<String, Long> byType = new HashMap<>();
        for (AudioVersion v : versions) {
            List<TimelineMarker> markers = markerRepo.findByAudioVersionIdOrderByStartTimeMsAsc(v.getId());
            for (TimelineMarker m : markers) {
                total++;
                if (m.getStatus() == Enums.MarkerStatus.RESOLVED) resolved++;
                if (m.getStatus() == Enums.MarkerStatus.PENDING) pending++;
                byType.merge(m.getType().name(), 1L, Long::sum);
            }
        }
        long durationMs = versions.stream().findFirst().map(AudioVersion::getDurationMs).orElse(0L);
        long days = Duration.between(e.getCreatedAt(), Instant.now()).toDays();
        return new EpisodeStats(e.getId(), e.getTitle(), durationMs, total, resolved, pending,
                versions.size(), days, byType);
    }

    @Transactional(readOnly = true)
    public TeamStats teamStats(CurrentUser user) {
        List<Podcast> podcasts = podcastRepo.findByTeamIdOrderByCreatedAtDesc(user.teamId());
        List<Long> podcastIds = podcasts.stream().map(Podcast::getId).toList();
        List<Episode> episodes = podcastIds.stream()
                .flatMap(pid -> episodeRepo.findByPodcastIdOrderByNumberDesc(pid).stream()).toList();

        long totalMarkers = 0, resolvedMarkers = 0;
        Map<Long, long[]> perUserMarkers = new HashMap<>();
        Set<Long> allVersionIds = new HashSet<>();
        for (Episode e : episodes) {
            for (AudioVersion v : versionRepo.findByEpisodeIdOrderByVersionNumberDesc(e.getId())) {
                allVersionIds.add(v.getId());
            }
        }
        for (Long vid : allVersionIds) {
            for (TimelineMarker m : markerRepo.findByAudioVersionIdOrderByStartTimeMsAsc(vid)) {
                totalMarkers++;
                if (m.getStatus() == Enums.MarkerStatus.RESOLVED) resolvedMarkers++;
                perUserMarkers.computeIfAbsent(m.getCreatedBy(), k -> new long[2])[0]++;
            }
        }

        long overdueTasks = 0;
        Map<Long, long[]> perUserTasks = new HashMap<>();
        for (Episode e : episodes) {
            for (Task t : taskRepo.findByEpisodeIdOrderByCreatedAtDesc(e.getId())) {
                if (t.getStatus() != Enums.TaskStatus.DONE && t.getStatus() != Enums.TaskStatus.CANCELLED
                        && t.getDueDate() != null && t.getDueDate().isBefore(Instant.now())) {
                    overdueTasks++;
                }
                if (t.getAssigneeId() != null) {
                    long[] arr = perUserTasks.computeIfAbsent(t.getAssigneeId(), k -> new long[2]);
                    arr[0]++;
                    if (t.getStatus() == Enums.TaskStatus.DONE) arr[1]++;
                }
            }
        }

        Set<Long> userIds = new HashSet<>();
        userIds.addAll(perUserMarkers.keySet());
        userIds.addAll(perUserTasks.keySet());
        Map<Long, String> names = new HashMap<>();
        userRepo.findAllById(userIds).forEach(u -> names.put(u.getId(), u.getName()));

        List<MemberStat> members = userIds.stream().map(uid -> {
            long[] m = perUserMarkers.getOrDefault(uid, new long[2]);
            long[] t = perUserTasks.getOrDefault(uid, new long[2]);
            return new MemberStat(uid, names.getOrDefault(uid, ""), m[0], t[0], t[1]);
        }).toList();

        double avg = episodes.isEmpty() ? 0 : (double) totalMarkers / episodes.size();
        return new TeamStats(episodes.size(), totalMarkers, resolvedMarkers, overdueTasks,
                Math.round(avg * 10.0) / 10.0, members);
    }

    @Transactional(readOnly = true)
    public List<DistributionCoverage> distributionCoverage(CurrentUser user) {
        List<PlatformAccount> accounts = accountRepo.findByTeamIdOrderByCreatedAtDesc(user.teamId());
        List<Long> accountIds = accounts.stream().map(PlatformAccount::getId).toList();
        Map<Long, long[]> byPlatform = new HashMap<>();
        Map<Long, List<Long>> reviewDurations = new HashMap<>();
        for (Distribution d : distributionRepo.findAll()) {
            if (!accountIds.contains(d.getPlatformAccountId())) continue;
            PlatformAccount acc = accounts.stream().filter(a -> a.getId().equals(d.getPlatformAccountId())).findFirst().orElse(null);
            if (acc == null) continue;
            long[] arr = byPlatform.computeIfAbsent(acc.getPlatformId(), k -> new long[2]);
            arr[0]++;
            if (d.getStatus() == Enums.DistributionStatus.PUBLISHED) {
                arr[1]++;
                if (d.getSubmittedAt() != null && d.getPublishedAt() != null) {
                    reviewDurations.computeIfAbsent(acc.getPlatformId(), k -> new ArrayList<>())
                            .add(Duration.between(d.getSubmittedAt(), d.getPublishedAt()).toHours());
                }
            }
        }
        return byPlatform.entrySet().stream().map(e -> {
            String name = platformRepo.findById(e.getKey()).map(Platform::getName).orElse("");
            long total = e.getValue()[0];
            long published = e.getValue()[1];
            double coverage = total == 0 ? 0 : (published * 100.0 / total);
            List<Long> durations = reviewDurations.get(e.getKey());
            Double avg = durations == null || durations.isEmpty() ? null
                    : durations.stream().mapToLong(Long::longValue).average().orElse(0);
            return new DistributionCoverage(name, total, published,
                    Math.round(coverage * 10.0) / 10.0, avg);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<StructureComparison> compareStructure(Long episodeId, CurrentUser user) {
        Episode e = episodeRepo.findById(episodeId).orElseThrow();
        Podcast p = podcastRepo.findById(e.getPodcastId()).orElseThrow();
        if (!p.getTeamId().equals(user.teamId())) {
            throw new com.podcast.collab.common.ApiException(com.podcast.collab.common.ErrorCode.TEAM_MISMATCH);
        }
        if (p.getStructureTemplateJson() == null) return List.of();
        try {
            List<Map<String, Object>> segments = objectMapper.readValue(p.getStructureTemplateJson(),
                    new TypeReference<>() {});
            long totalTarget = segments.stream()
                    .mapToLong(s -> ((Number) s.getOrDefault("targetSeconds", 0)).longValue()).sum();
            long actualTotal = versionRepo.findFirstByEpisodeIdOrderByVersionNumberDesc(episodeId)
                    .map(AudioVersion::getDurationMs).orElse(0L) / 1000;
            double ratio = totalTarget == 0 ? 0 : (double) actualTotal / totalTarget;
            return segments.stream().map(s -> {
                String name = String.valueOf(s.getOrDefault("name", ""));
                int target = ((Number) s.getOrDefault("targetSeconds", 0)).intValue();
                long actual = Math.round(target * ratio);
                return new StructureComparison(name, target, actual, actual - target);
            }).toList();
        } catch (Exception ex) {
            return List.of();
        }
    }
}
