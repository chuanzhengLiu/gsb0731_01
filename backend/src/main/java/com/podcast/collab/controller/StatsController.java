package com.podcast.collab.controller;

import com.podcast.collab.entity.*;
import com.podcast.collab.repository.*;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.TeamGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 数据统计：单集数据 / 团队效率 / 分发覆盖（不含播放量——README 明确不做） */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {
    private final EpisodeRepository episodeRepository;
    private final PodcastRepository podcastRepository;
    private final AudioVersionRepository audioVersionRepository;
    private final TimelineMarkerRepository markerRepository;
    private final TaskRepository taskRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final DistributionRepository distributionRepository;
    private final PlatformRepository platformRepository;
    private final TeamGuard teamGuard;

    /** 单集数据：时长、标记数量（修改轮次）、录制到发布周期天数 */
    @GetMapping("/episodes/{episodeId}")
    public Map<String, Object> episodeStats(@PathVariable Long episodeId) {
        Episode episode = teamGuard.requireEpisode(episodeId);
        Map<String, Object> map = new HashMap<>();
        map.put("episodeId", episodeId);
        map.put("status", episode.getStatus().name());
        map.put("versionCount", audioVersionRepository.countByEpisodeId(episodeId));
        map.put("markerCount", markerRepository.countByEpisodeId(episodeId));
        map.put("resolvedMarkerCount", markerRepository.countByEpisodeIdAndStatus(episodeId, MarkerStatus.RESOLVED));
        audioVersionRepository.findTopByEpisodeIdOrderByVersionNumberDesc(episodeId)
                .ifPresent(v -> map.put("durationMs", v.getDurationMs()));
        if (episode.getRecordDate() != null) {
            LocalDate end = episode.getStatus() == EpisodeStatus.PUBLISHED
                    ? episode.getUpdatedAt().toLocalDate() : LocalDate.now();
            map.put("cycleDays", ChronoUnit.DAYS.between(episode.getRecordDate(), end));
        }
        return map;
    }

    /** 团队效率：人均处理标记数、平均审听轮次（版本数）、逾期任务数 */
    @GetMapping("/team")
    public Map<String, Object> teamStats() {
        Long teamId = SecurityUtils.currentTeamId();
        List<TeamMember> members = teamMemberRepository.findByTeamId(teamId);
        Map<String, Object> map = new HashMap<>();
        map.put("memberCount", members.size());

        long totalMarkers = members.stream()
                .mapToLong(m -> markerRepository.countByCreatedBy(m.getUserId())).sum();
        map.put("totalMarkersCreated", totalMarkers);
        map.put("avgMarkersPerMember",
                members.isEmpty() ? 0 : Math.round(totalMarkers * 100.0 / members.size()) / 100.0);

        List<Long> episodeIds = teamEpisodeIds(teamId);
        double avgVersions = episodeIds.isEmpty() ? 0
                : episodeIds.stream().mapToLong(audioVersionRepository::countByEpisodeId).average().orElse(0);
        map.put("avgReviewRounds", Math.round(avgVersions * 100.0) / 100.0);
        map.put("episodeCount", episodeIds.size());

        long overdue = taskRepository.findByDueDateBeforeAndStatusNot(LocalDate.now(), "DONE").stream()
                .filter(t -> episodeIds.contains(t.getEpisodeId())).count();
        map.put("overdueTaskCount", overdue);
        return map;
    }

    /** 分发覆盖：各平台上架率、平均审核时长 */
    @GetMapping("/distribution")
    public Map<String, Object> distributionStats() {
        Long teamId = SecurityUtils.currentTeamId();
        var episodeIds = teamEpisodeIds(teamId);
        List<Map<String, Object>> perPlatform = platformRepository.findByTeamId(teamId).stream()
                .map(platform -> {
                    List<Distribution> all = distributionRepository.findAll().stream()
                            .filter(d -> d.getPlatformId().equals(platform.getId())
                                    && episodeIds.contains(d.getEpisodeId())).toList();
                    long live = all.stream().filter(d -> d.getStatus() == DistributionStatus.LIVE).count();
                    double liveRate = all.isEmpty() ? 0 : live / (double) all.size();
                    double avgReviewHours = all.stream()
                            .filter(d -> d.getSubmittedAt() != null && d.getPublishedAt() != null)
                            .mapToLong(d -> ChronoUnit.HOURS.between(d.getSubmittedAt(), d.getPublishedAt()))
                            .average().orElse(0);
                    Map<String, Object> p = new HashMap<String, Object>();
                    p.put("platformId", platform.getId());
                    p.put("platformName", platform.getName());
                    p.put("totalDistributions", all.size());
                    p.put("liveCount", live);
                    p.put("liveRate", Math.round(liveRate * 10000.0) / 100.0);
                    p.put("avgReviewHours", Math.round(avgReviewHours * 100.0) / 100.0);
                    return p;
                }).toList();
        Map<String, Object> map = new HashMap<>();
        map.put("platforms", perPlatform);
        return map;
    }

    private List<Long> teamEpisodeIds(Long teamId) {
        return podcastRepository.findByTeamId(teamId).stream()
                .flatMap(p -> episodeRepository.findByPodcastIdOrderByNumberDesc(p.getId()).stream())
                .map(Episode::getId)
                .collect(Collectors.toList());
    }
}
