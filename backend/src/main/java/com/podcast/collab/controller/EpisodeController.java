package com.podcast.collab.controller;

import com.podcast.collab.dto.Dtos.*;
import com.podcast.collab.entity.Episode;
import com.podcast.collab.entity.EpisodeStatus;
import com.podcast.collab.entity.Podcast;
import com.podcast.collab.repository.AudioVersionRepository;
import com.podcast.collab.repository.EpisodeRepository;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.AuditService;
import com.podcast.collab.service.TeamGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EpisodeController {
    private final EpisodeRepository episodeRepository;
    private final AudioVersionRepository audioVersionRepository;
    private final TeamGuard teamGuard;
    private final AuditService auditService;

    @GetMapping("/podcasts/{podcastId}/episodes")
    public List<Episode> list(@PathVariable Long podcastId) {
        teamGuard.requirePodcast(podcastId);
        return episodeRepository.findByPodcastIdOrderByNumberDesc(podcastId);
    }

    @PostMapping("/podcasts/{podcastId}/episodes")
    public Episode create(@PathVariable Long podcastId, @Valid @RequestBody EpisodeRequest req) {
        SecurityUtils.requireRole("ADMIN", "PRODUCER");
        Podcast podcast = teamGuard.requirePodcast(podcastId);
        Episode episode = new Episode();
        episode.setPodcastId(podcastId);
        apply(episode, req);
        Episode saved = episodeRepository.save(episode);
        auditService.log(SecurityUtils.currentUserId(), podcast.getTeamId(), "EPISODE_CREATE",
                "episode", saved.getId(), "创建单集: 第" + saved.getNumber() + "期 " + saved.getTitle());
        return saved;
    }

    @GetMapping("/episodes/{id}")
    public Episode get(@PathVariable Long id) {
        return teamGuard.requireEpisode(id);
    }

    @PutMapping("/episodes/{id}")
    public Episode update(@PathVariable Long id, @Valid @RequestBody EpisodeRequest req) {
        SecurityUtils.requireRole("ADMIN", "PRODUCER");
        Episode episode = teamGuard.requireEpisode(id);
        apply(episode, req);
        return episodeRepository.save(episode);
    }

    /** 状态流转：策划→录制→粗剪→精剪→审听→定稿→分发→已发布 */
    @PutMapping("/episodes/{id}/status")
    public Episode updateStatus(@PathVariable Long id, @Valid @RequestBody EpisodeStatusRequest req) {
        Episode episode = teamGuard.requireEpisode(id);
        EpisodeStatus newStatus;
        try {
            newStatus = EpisodeStatus.valueOf(req.status());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("状态非法");
        }
        // HOST 只能查看，不能流转状态
        SecurityUtils.requireRole("ADMIN", "PRODUCER", "EDITOR", "OPERATOR");
        EpisodeStatus old = episode.getStatus();
        episode.setStatus(newStatus);
        // 定稿时若尚无成片地址，回写最新 ACTIVE 版本
        if (newStatus == EpisodeStatus.FINALIZED && episode.getFinalAudioUrl() == null) {
            audioVersionRepository
                    .findTopByEpisodeIdOrderByVersionNumberDesc(episode.getId())
                    .filter(v -> "ACTIVE".equals(v.getStatus()))
                    .ifPresent(v -> episode.setFinalAudioUrl("/api/audio/stream/" + v.getId()));
        }
        Episode saved = episodeRepository.save(episode);
        Podcast podcast = teamGuard.requirePodcast(episode.getPodcastId());
        auditService.log(SecurityUtils.currentUserId(), podcast.getTeamId(), "EPISODE_STATUS",
                "episode", id, "状态变更: " + old + " → " + newStatus);
        return saved;
    }

    @DeleteMapping("/episodes/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        SecurityUtils.requireRole("ADMIN", "PRODUCER");
        Episode episode = teamGuard.requireEpisode(id);
        Podcast podcast = teamGuard.requirePodcast(episode.getPodcastId());
        episodeRepository.delete(episode);
        auditService.log(SecurityUtils.currentUserId(), podcast.getTeamId(), "EPISODE_DELETE",
                "episode", id, "删除单集: " + episode.getTitle());
        return Map.of("message", "单集已删除");
    }

    private void apply(Episode episode, EpisodeRequest req) {
        episode.setNumber(req.number());
        episode.setTitle(req.title());
        episode.setTheme(req.theme());
        episode.setRecordDate(req.recordDate());
        episode.setScheduledPublishAt(req.scheduledPublishAt());
    }
}
