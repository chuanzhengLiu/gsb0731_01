package com.podcast.collab.controller;

import com.podcast.collab.dto.common.ApiResponse;
import com.podcast.collab.dto.podcast.CreatePodcastRequest;
import com.podcast.collab.dto.podcast.PodcastResponse;
import com.podcast.collab.dto.podcast.UpdatePodcastRequest;
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

    @PostMapping
    public ApiResponse<PodcastResponse> createPodcast(@RequestParam Long teamId,
                                                       @Valid @RequestBody CreatePodcastRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        PodcastResponse response = podcastService.create(teamId, request, userId);
        return ApiResponse.ok(response);
    }

    @GetMapping
    public ApiResponse<List<PodcastResponse>> listByTeam(@RequestParam Long teamId) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<PodcastResponse> podcasts = podcastService.listByTeam(teamId, userId);
        return ApiResponse.ok(podcasts);
    }

    @GetMapping("/{id}")
    public ApiResponse<PodcastResponse> getById(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        PodcastResponse response = podcastService.getById(id, userId);
        return ApiResponse.ok(response);
    }

    @PutMapping("/{id}")
    public ApiResponse<PodcastResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody UpdatePodcastRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        PodcastResponse response = podcastService.update(id, request, userId);
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        podcastService.delete(id, userId);
        return ApiResponse.ok("Podcast deleted successfully", null);
    }
}
