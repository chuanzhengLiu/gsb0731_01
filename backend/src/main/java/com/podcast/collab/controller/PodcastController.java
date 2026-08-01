package com.podcast.collab.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.podcast.collab.dto.Dtos.*;
import com.podcast.collab.entity.Podcast;
import com.podcast.collab.repository.PodcastRepository;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.AuditService;
import com.podcast.collab.service.TeamGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/podcasts")
@RequiredArgsConstructor
public class PodcastController {
    private final PodcastRepository podcastRepository;
    private final TeamGuard teamGuard;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public List<Podcast> list() {
        return podcastRepository.findByTeamId(SecurityUtils.currentTeamId());
    }

    @GetMapping("/{id}")
    public Podcast get(@PathVariable Long id) {
        return teamGuard.requirePodcast(id);
    }

    @PostMapping
    public Podcast create(@Valid @RequestBody PodcastRequest req) {
        SecurityUtils.requireRole("ADMIN", "PRODUCER");
        Podcast podcast = new Podcast();
        apply(podcast, req);
        podcast.setTeamId(SecurityUtils.currentTeamId());
        Podcast saved = podcastRepository.save(podcast);
        auditService.log(SecurityUtils.currentUserId(), saved.getTeamId(), "PODCAST_CREATE",
                "podcast", saved.getId(), "创建节目: " + saved.getName());
        return saved;
    }

    @PutMapping("/{id}")
    public Podcast update(@PathVariable Long id, @Valid @RequestBody PodcastRequest req) {
        SecurityUtils.requireRole("ADMIN", "PRODUCER");
        Podcast podcast = teamGuard.requirePodcast(id);
        apply(podcast, req);
        return podcastRepository.save(podcast);
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable Long id) {
        SecurityUtils.requireRole("ADMIN", "PRODUCER");
        Podcast podcast = teamGuard.requirePodcast(id);
        podcastRepository.delete(podcast);
        auditService.log(SecurityUtils.currentUserId(), podcast.getTeamId(), "PODCAST_DELETE",
                "podcast", id, "删除节目: " + podcast.getName());
        return Map.of("message", "节目已删除");
    }

    private void apply(Podcast podcast, PodcastRequest req) {
        podcast.setName(req.name());
        podcast.setType(req.type());
        podcast.setUpdateFrequency(req.updateFrequency());
        podcast.setTargetDuration(req.targetDuration());
        if (req.structureTemplate() != null) {
            try {
                podcast.setStructureTemplateJson(objectMapper.writeValueAsString(req.structureTemplate()));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("结构模板格式错误");
            }
        }
    }
}
