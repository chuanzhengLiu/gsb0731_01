package com.podcast.collab.web;

import com.podcast.collab.dto.PodcastDtos.*;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.PodcastService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/podcasts")
public class PodcastController {

    private final PodcastService podcastService;

    public PodcastController(PodcastService podcastService) {
        this.podcastService = podcastService;
    }

    @GetMapping
    public List<PodcastResponse> list() {
        return podcastService.list(SecurityUtils.requireUser());
    }

    @PostMapping
    public PodcastResponse create(@Valid @RequestBody CreatePodcastRequest req) {
        return podcastService.create(req, SecurityUtils.requireUser());
    }

    @GetMapping("/{id}")
    public PodcastResponse get(@PathVariable Long id) {
        return podcastService.get(id, SecurityUtils.requireUser());
    }

    @PatchMapping("/{id}")
    public PodcastResponse update(@PathVariable Long id, @Valid @RequestBody UpdatePodcastRequest req) {
        return podcastService.update(id, req, SecurityUtils.requireUser());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        podcastService.delete(id, SecurityUtils.requireUser());
    }

    @GetMapping("/{id}/episodes")
    public List<EpisodeResponse> episodes(@PathVariable Long id) {
        return podcastService.listEpisodes(id, SecurityUtils.requireUser());
    }

    @PostMapping("/{id}/episodes")
    public EpisodeResponse createEpisode(@PathVariable Long id, @Valid @RequestBody CreateEpisodeRequest req) {
        return podcastService.createEpisode(id, req, SecurityUtils.requireUser());
    }

    @GetMapping("/episodes/{episodeId}")
    public EpisodeResponse getEpisode(@PathVariable Long episodeId) {
        return podcastService.getEpisode(episodeId, SecurityUtils.requireUser());
    }

    @PatchMapping("/episodes/{episodeId}")
    public EpisodeResponse updateEpisode(@PathVariable Long episodeId, @Valid @RequestBody UpdateEpisodeRequest req) {
        return podcastService.updateEpisode(episodeId, req, SecurityUtils.requireUser());
    }

    @DeleteMapping("/episodes/{episodeId}")
    public void deleteEpisode(@PathVariable Long episodeId) {
        podcastService.deleteEpisode(episodeId, SecurityUtils.requireUser());
    }
}
