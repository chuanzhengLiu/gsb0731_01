package com.podcast.collab.controller;

import com.podcast.collab.dto.Dtos.ShareLinkResponse;
import com.podcast.collab.entity.*;
import com.podcast.collab.repository.*;
import com.podcast.collab.security.JwtService;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.AuditService;
import com.podcast.collab.service.TeamGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 访客分享：随机 token 链接，7天过期，访问记录日志 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ShareController {
    private final ShareLinkRepository shareLinkRepository;
    private final EpisodeRepository episodeRepository;
    private final PodcastRepository podcastRepository;
    private final AudioVersionRepository audioVersionRepository;
    private final TimelineMarkerRepository markerRepository;
    private final TeamGuard teamGuard;
    private final AuditService auditService;
    private final JwtService jwtService;

    @Value("${app.share-link-ttl-days}")
    private long shareTtlDays;
    @Value("${app.audio-signed-url-ttl-minutes}")
    private long signedUrlTtlMinutes;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 创建分享链接（制作人/管理员） */
    @PostMapping("/episodes/{episodeId}/share")
    public ShareLinkResponse create(@PathVariable Long episodeId) {
        SecurityUtils.requireRole("ADMIN", "PRODUCER");
        Episode episode = teamGuard.requireEpisode(episodeId);
        ShareLink link = new ShareLink();
        link.setEpisodeId(episodeId);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        link.setToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        link.setCreatedBy(SecurityUtils.currentUserId());
        link.setExpiresAt(LocalDateTime.now().plusDays(shareTtlDays)); // 7天过期
        ShareLink saved = shareLinkRepository.save(link);
        Long teamId = teamGuard.requirePodcast(episode.getPodcastId()).getTeamId();
        auditService.log(SecurityUtils.currentUserId(), teamId, "SHARE_CREATE",
                "episode", episodeId, "创建访客分享链接");
        return new ShareLinkResponse(saved.getToken(), "/share/" + saved.getToken(), saved.getExpiresAt());
    }

    /** 访客通过 token 访问单集（只读，无需登录） */
    @GetMapping("/share/{token}")
    public Map<String, Object> view(@PathVariable String token) {
        ShareLink link = shareLinkRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("分享链接无效"));
        if (link.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("分享链接已过期");
        }
        Episode episode = episodeRepository.findById(link.getEpisodeId())
                .orElseThrow(() -> new IllegalArgumentException("单集不存在"));

        // 访问记录日志
        Long teamId = teamGuardTeamId(episode);
        auditService.log(null, teamId, "SHARE_ACCESS", "episode", episode.getId(), "访客访问分享链接");

        Map<String, Object> map = new HashMap<>();
        map.put("title", "第" + episode.getNumber() + "期 " + episode.getTitle());
        map.put("theme", episode.getTheme());
        map.put("expiresAt", link.getExpiresAt());

        audioVersionRepository.findTopByEpisodeIdOrderByVersionNumberDesc(episode.getId())
                .filter(v -> "ACTIVE".equals(v.getStatus()))
                .ifPresent(v -> {
                    long exp = System.currentTimeMillis() / 1000 + signedUrlTtlMinutes * 60;
                    String sig = jwtService.signAudioUrl(v.getId(), exp);
                    map.put("audioUrl", "/api/audio/stream/" + v.getId() + "?exp=" + exp + "&sig=" + sig);
                    map.put("durationMs", v.getDurationMs());
                    map.put("peaks", v.getWaveformJson() == null ? "[]" : v.getWaveformJson());
                });

        List<Map<String, Object>> markers = markerRepository
                .findByEpisodeIdOrderByStartTimeMs(episode.getId()).stream()
                .map(m -> {
                    Map<String, Object> mm = new HashMap<String, Object>();
                    mm.put("startTimeMs", m.getStartTimeMs());
                    mm.put("endTimeMs", m.getEndTimeMs());
                    mm.put("type", m.getType().name());
                    mm.put("description", m.getDescription());
                    return mm;
                }).toList();
        map.put("markers", markers);
        return map;
    }

    private Long teamGuardTeamId(Episode episode) {
        return podcastRepository.findById(episode.getPodcastId()).map(Podcast::getTeamId).orElse(null);
    }
}
