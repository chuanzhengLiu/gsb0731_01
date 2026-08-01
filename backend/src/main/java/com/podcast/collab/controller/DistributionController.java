package com.podcast.collab.controller;

import com.podcast.collab.dto.Dtos.*;
import com.podcast.collab.entity.*;
import com.podcast.collab.repository.*;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.AuditService;
import com.podcast.collab.service.TeamGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 分发管理：平台账号、每集分发任务、状态追踪、发布日历 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DistributionController {
    private final PlatformRepository platformRepository;
    private final DistributionRepository distributionRepository;
    private final EpisodeRepository episodeRepository;
    private final PodcastRepository podcastRepository;
    private final TeamGuard teamGuard;
    private final AuditService auditService;

    // ---------- 平台账号 ----------
    @GetMapping("/platforms")
    public List<Platform> platforms() {
        return platformRepository.findByTeamId(SecurityUtils.currentTeamId());
    }

    @PostMapping("/platforms")
    public Platform createPlatform(@Valid @RequestBody PlatformRequest req) {
        SecurityUtils.requireRole("ADMIN", "OPERATOR");
        Platform platform = new Platform();
        platform.setTeamId(SecurityUtils.currentTeamId());
        applyPlatform(platform, req);
        return platformRepository.save(platform);
    }

    @PutMapping("/platforms/{id}")
    public Platform updatePlatform(@PathVariable Long id, @Valid @RequestBody PlatformRequest req) {
        SecurityUtils.requireRole("ADMIN", "OPERATOR");
        Platform platform = requirePlatform(id);
        applyPlatform(platform, req);
        return platformRepository.save(platform);
    }

    @DeleteMapping("/platforms/{id}")
    public Map<String, String> deletePlatform(@PathVariable Long id) {
        SecurityUtils.requireRole("ADMIN", "OPERATOR");
        platformRepository.delete(requirePlatform(id));
        return Map.of("message", "平台已删除");
    }

    // ---------- 分发任务 ----------
    @GetMapping("/episodes/{episodeId}/distributions")
    public List<Map<String, Object>> distributions(@PathVariable Long episodeId) {
        teamGuard.requireEpisode(episodeId);
        return distributionRepository.findByEpisodeId(episodeId).stream().map(d -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", d.getId());
            map.put("episodeId", d.getEpisodeId());
            map.put("platformId", d.getPlatformId());
            map.put("platformName", platformRepository.findById(d.getPlatformId())
                    .map(Platform::getName).orElse("未知"));
            map.put("status", d.getStatus().name());
            map.put("submittedAt", d.getSubmittedAt());
            map.put("publishedAt", d.getPublishedAt());
            map.put("scheduledAt", d.getScheduledAt());
            map.put("platformDataJson", d.getPlatformDataJson());
            return map;
        }).toList();
    }

    /** 为单集创建分发任务（选择平台 + 平台专属信息） */
    @PostMapping("/episodes/{episodeId}/distributions")
    public Map<String, Object> createDistribution(@PathVariable Long episodeId,
                                                  @Valid @RequestBody DistributionRequest req) {
        SecurityUtils.requireRole("ADMIN", "OPERATOR");
        teamGuard.requireEpisode(episodeId);
        Platform platform = requirePlatform(req.platformId());
        if (distributionRepository.findByEpisodeIdAndPlatformId(episodeId, req.platformId()).isPresent()) {
            throw new IllegalArgumentException("该平台已存在分发任务");
        }
        Distribution d = new Distribution();
        d.setEpisodeId(episodeId);
        d.setPlatformId(platform.getId());
        d.setPlatformDataJson(req.platformDataJson());
        d.setScheduledAt(req.scheduledAt());
        Distribution saved = distributionRepository.save(d);
        return Map.of("id", saved.getId(), "message", "分发任务已创建");
    }

    /** 分发状态流转：未开始→已提交→审核中→已上线/被拒绝（运营操作） */
    @PutMapping("/distributions/{id}/status")
    public Map<String, Object> updateStatus(@PathVariable Long id,
                                            @Valid @RequestBody DistributionStatusRequest req) {
        SecurityUtils.requireRole("ADMIN", "OPERATOR");
        Distribution d = requireDistribution(id);
        DistributionStatus newStatus;
        try {
            newStatus = DistributionStatus.valueOf(req.status());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("状态非法");
        }
        d.setStatus(newStatus);
        if (newStatus == DistributionStatus.SUBMITTED && d.getSubmittedAt() == null) {
            d.setSubmittedAt(LocalDateTime.now());
        }
        if (newStatus == DistributionStatus.LIVE && d.getPublishedAt() == null) {
            d.setPublishedAt(LocalDateTime.now());
        }
        Distribution saved = distributionRepository.save(d);
        Episode episode = teamGuard.requireEpisode(d.getEpisodeId());
        Long teamId = teamGuard.requirePodcast(episode.getPodcastId()).getTeamId();
        auditService.log(SecurityUtils.currentUserId(), teamId, "DISTRIBUTION_STATUS",
                "distribution", id, "分发状态变更为 " + newStatus);
        return Map.of("id", saved.getId(), "status", saved.getStatus().name());
    }

    @PutMapping("/distributions/{id}")
    public Map<String, Object> updateDistribution(@PathVariable Long id,
                                                  @Valid @RequestBody DistributionRequest req) {
        SecurityUtils.requireRole("ADMIN", "OPERATOR");
        Distribution d = requireDistribution(id);
        d.setPlatformDataJson(req.platformDataJson());
        d.setScheduledAt(req.scheduledAt());
        distributionRepository.save(d);
        return Map.of("message", "已更新");
    }

    @DeleteMapping("/distributions/{id}")
    public Map<String, String> deleteDistribution(@PathVariable Long id) {
        SecurityUtils.requireRole("ADMIN", "OPERATOR");
        distributionRepository.delete(requireDistribution(id));
        return Map.of("message", "分发任务已删除");
    }

    /** 发布日历：时间范围内已排期的发布计划 */
    @GetMapping("/calendar")
    public List<Map<String, Object>> calendar(@RequestParam String from, @RequestParam String to) {
        Long teamId = SecurityUtils.currentTeamId();
        LocalDateTime fromDt = LocalDateTime.parse(from);
        LocalDateTime toDt = LocalDateTime.parse(to);
        // 本团队所有节目 ID，用于团队隔离过滤
        var teamPodcastIds = podcastRepository.findByTeamId(teamId).stream()
                .map(p -> p.getId()).collect(java.util.stream.Collectors.toSet());
        return distributionRepository.findByScheduledAtBetween(fromDt, toDt).stream()
                .filter(d -> {
                    Episode e = episodeRepository.findById(d.getEpisodeId()).orElse(null);
                    return e != null && teamPodcastIds.contains(e.getPodcastId());
                })
                .map(d -> {
                    Episode e = episodeRepository.findById(d.getEpisodeId()).orElse(null);
                    Map<String, Object> map = new HashMap<String, Object>();
                    map.put("distributionId", d.getId());
                    map.put("episodeId", d.getEpisodeId());
                    map.put("episodeTitle", e != null ? "第" + e.getNumber() + "期 " + e.getTitle() : "");
                    map.put("platformName", platformRepository.findById(d.getPlatformId())
                            .map(Platform::getName).orElse("未知"));
                    map.put("scheduledAt", d.getScheduledAt());
                    map.put("status", d.getStatus().name());
                    return map;
                }).toList();
    }

    private void applyPlatform(Platform platform, PlatformRequest req) {
        platform.setName(req.name());
        platform.setAccountName(req.accountName());
        platform.setRssRequiredFieldsJson(req.rssRequiredFieldsJson());
        platform.setCategoryOptionsJson(req.categoryOptionsJson());
    }

    private Platform requirePlatform(Long id) {
        Platform platform = platformRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("平台不存在"));
        if (!platform.getTeamId().equals(SecurityUtils.currentTeamId())) {
            throw new com.podcast.collab.security.ForbiddenException("无权访问该平台");
        }
        return platform;
    }

    private Distribution requireDistribution(Long id) {
        Distribution d = distributionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("分发任务不存在"));
        teamGuard.requireEpisode(d.getEpisodeId());
        return d;
    }
}
