package com.podcast.collab.controller;

import com.podcast.collab.dto.common.ApiResponse;
import com.podcast.collab.dto.episode.CreateEpisodeRequest;
import com.podcast.collab.dto.episode.EpisodeResponse;
import com.podcast.collab.dto.episode.UpdateEpisodeRequest;
import com.podcast.collab.dto.episode.UpdateEpisodeStatusRequest;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.EpisodeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/episodes")
public class EpisodeController {

    private final EpisodeService episodeService;

    public EpisodeController(EpisodeService episodeService) {
        this.episodeService = episodeService;
    }

    @PostMapping
    public ApiResponse<EpisodeResponse> createEpisode(@RequestParam Long podcastId,
                                                       @Valid @RequestBody CreateEpisodeRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        EpisodeResponse response = episodeService.create(podcastId, request, userId);
        return ApiResponse.ok(response);
    }

    @GetMapping
    public ApiResponse<List<EpisodeResponse>> listByPodcast(@RequestParam Long podcastId) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<EpisodeResponse> episodes = episodeService.listByPodcast(podcastId, userId);
        return ApiResponse.ok(episodes);
    }

    @GetMapping("/{id}")
    public ApiResponse<EpisodeResponse> getById(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        EpisodeResponse response = episodeService.getById(id, userId);
        return ApiResponse.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<EpisodeResponse> updateStatus(@PathVariable Long id,
                                                      @Valid @RequestBody UpdateEpisodeStatusRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        EpisodeResponse response = episodeService.updateStatus(id, request, userId);
        return ApiResponse.ok(response);
    }

    @PutMapping("/{id}")
    public ApiResponse<EpisodeResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody UpdateEpisodeRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        EpisodeResponse response = episodeService.update(id, request, userId);
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        episodeService.delete(id, userId);
        return ApiResponse.ok("Episode deleted successfully", null);
    }
}
