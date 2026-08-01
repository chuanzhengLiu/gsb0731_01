package com.podcast.collab.controller;

import com.podcast.collab.dto.Dtos.*;
import com.podcast.collab.entity.*;
import com.podcast.collab.repository.*;
import com.podcast.collab.security.ForbiddenException;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.AuditService;
import com.podcast.collab.service.TeamGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 时间轴标注：点标记（startTimeMs）与时间段标记（start+end） */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MarkerController {
    private final TimelineMarkerRepository markerRepository;
    private final AudioVersionRepository audioVersionRepository;
    private final UserRepository userRepository;
    private final TeamGuard teamGuard;
    private final AuditService auditService;

    /** 标记列表：按类型/提出人/状态筛选 + 关键词搜索 */
    @GetMapping("/episodes/{episodeId}/markers")
    public List<Map<String, Object>> list(@PathVariable Long episodeId,
                                          @RequestParam(required = false) String type,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) Long createdBy,
                                          @RequestParam(required = false) Long versionId,
                                          @RequestParam(required = false) String keyword) {
        teamGuard.requireEpisode(episodeId);
        List<TimelineMarker> markers;
        if (versionId != null) {
            // 校验版本归属：versionId 必须属于该单集，防止拼接他团队版本 ID 越权读取
            AudioVersion version = audioVersionRepository.findById(versionId)
                    .orElseThrow(() -> new IllegalArgumentException("音频版本不存在"));
            if (!version.getEpisodeId().equals(episodeId)) {
                throw new ForbiddenException("无权访问该版本的标记");
            }
            markers = markerRepository.findByAudioVersionIdOrderByStartTimeMs(versionId);
        } else {
            markers = markerRepository.findByEpisodeIdOrderByStartTimeMs(episodeId);
        }
        return markers.stream()
                .filter(m -> type == null || m.getType().name().equals(type))
                .filter(m -> status == null || m.getStatus().name().equals(status))
                .filter(m -> createdBy == null || m.getCreatedBy().equals(createdBy))
                .filter(m -> keyword == null || keyword.isBlank()
                        || (m.getDescription() != null && m.getDescription().contains(keyword)))
                .map(this::toInfo)
                .collect(Collectors.toList());
    }

    @PostMapping("/episodes/{episodeId}/markers")
    public Map<String, Object> create(@PathVariable Long episodeId, @Valid @RequestBody MarkerRequest req) {
        Episode episode = teamGuard.requireEpisode(episodeId);
        // 默认关联最新版本
        AudioVersion version = audioVersionRepository.findTopByEpisodeIdOrderByVersionNumberDesc(episodeId)
                .orElseThrow(() -> new IllegalArgumentException("请先上传音频再添加标记"));
        TimelineMarker marker = buildMarker(episodeId, version.getId(), req);
        TimelineMarker saved = markerRepository.save(marker);
        Long teamId = teamGuard.requirePodcast(episode.getPodcastId()).getTeamId();
        auditService.log(SecurityUtils.currentUserId(), teamId, "MARKER_CREATE",
                "marker", saved.getId(), "添加标记 " + saved.getType() + " @" + saved.getStartTimeMs() + "ms");
        return toInfo(saved);
    }

    @PutMapping("/markers/{id}")
    public Map<String, Object> update(@PathVariable Long id, @Valid @RequestBody MarkerRequest req) {
        TimelineMarker marker = requireMarker(id);
        checkEditPermission(marker);
        marker.setStartTimeMs(req.startTimeMs());
        marker.setEndTimeMs(req.endTimeMs());
        marker.setType(parseType(req.type()));
        marker.setDescription(req.description());
        marker.setScreenshotUrl(req.screenshotUrl());
        return toInfo(markerRepository.save(marker));
    }

    /** 标记状态流转：待处理/处理中/已解决/已忽略 */
    @PutMapping("/markers/{id}/status")
    public Map<String, Object> updateStatus(@PathVariable Long id, @Valid @RequestBody MarkerStatusRequest req) {
        TimelineMarker marker = requireMarker(id);
        MarkerStatus newStatus;
        try {
            newStatus = MarkerStatus.valueOf(req.status());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("状态非法");
        }
        checkEditPermission(marker);
        marker.setStatus(newStatus);
        if (newStatus == MarkerStatus.RESOLVED) {
            marker.setResolvedBy(SecurityUtils.currentUserId());
        }
        TimelineMarker saved = markerRepository.save(marker);
        Episode episode = teamGuard.requireEpisode(marker.getEpisodeId());
        Long teamId = teamGuard.requirePodcast(episode.getPodcastId()).getTeamId();
        auditService.log(SecurityUtils.currentUserId(), teamId, "MARKER_STATUS",
                "marker", id, "标记状态变更为 " + newStatus);
        return toInfo(saved);
    }

    @DeleteMapping("/markers/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        TimelineMarker marker = requireMarker(id);
        checkEditPermission(marker);
        Episode episode = teamGuard.requireEpisode(marker.getEpisodeId());
        Long teamId = teamGuard.requirePodcast(episode.getPodcastId()).getTeamId();
        markerRepository.delete(marker);
        auditService.log(SecurityUtils.currentUserId(), teamId, "MARKER_DELETE", "marker", id, "删除标记");
        return Map.of("message", "标记已删除");
    }

    private TimelineMarker buildMarker(Long episodeId, Long versionId, MarkerRequest req) {
        TimelineMarker marker = new TimelineMarker();
        marker.setEpisodeId(episodeId);
        marker.setAudioVersionId(versionId);
        marker.setStartTimeMs(req.startTimeMs());
        marker.setEndTimeMs(req.endTimeMs());
        marker.setType(parseType(req.type()));
        marker.setDescription(req.description());
        marker.setScreenshotUrl(req.screenshotUrl());
        marker.setCreatedBy(SecurityUtils.currentUserId());
        return marker;
    }

    private MarkerType parseType(String type) {
        try {
            return MarkerType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("标记类型非法");
        }
    }

    private TimelineMarker requireMarker(Long id) {
        TimelineMarker marker = markerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("标记不存在"));
        teamGuard.requireEpisode(marker.getEpisodeId());
        return marker;
    }

    /** 制作人可改一切；其他角色只能改自己创建的标记 */
    private void checkEditPermission(TimelineMarker marker) {
        String role = SecurityUtils.currentUser().getRole();
        if ("ADMIN".equals(role) || "PRODUCER".equals(role)) {
            return;
        }
        // 剪辑师可流转任何标记状态（用于"标记已解决"），但不能改他人标记内容——此处统一：非创建人仅限 EDITOR 状态流转
        if (!marker.getCreatedBy().equals(SecurityUtils.currentUserId()) && !"EDITOR".equals(role)) {
            throw new ForbiddenException("只能操作自己创建的标记");
        }
    }

    private Map<String, Object> toInfo(TimelineMarker m) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", m.getId());
        map.put("episodeId", m.getEpisodeId());
        map.put("audioVersionId", m.getAudioVersionId());
        map.put("startTimeMs", m.getStartTimeMs());
        map.put("endTimeMs", m.getEndTimeMs());
        map.put("type", m.getType().name());
        map.put("description", m.getDescription());
        map.put("screenshotUrl", m.getScreenshotUrl());
        map.put("status", m.getStatus().name());
        map.put("createdBy", m.getCreatedBy());
        map.put("creatorName", userRepository.findById(m.getCreatedBy()).map(User::getName).orElse("未知"));
        map.put("resolvedBy", m.getResolvedBy());
        map.put("createdAt", m.getCreatedAt());
        map.put("updatedAt", m.getUpdatedAt());
        return map;
    }
}
