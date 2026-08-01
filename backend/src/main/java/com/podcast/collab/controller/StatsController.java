package com.podcast.collab.controller;

import com.podcast.collab.dto.common.ApiResponse;
import com.podcast.collab.dto.stats.EpisodeStatsResponse;
import com.podcast.collab.dto.stats.TeamStatsResponse;
import com.podcast.collab.service.StatsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/episode/{episodeId}")
    public ApiResponse<EpisodeStatsResponse> getEpisodeStats(@PathVariable Long episodeId) {
        return ApiResponse.ok(statsService.getEpisodeStats(episodeId));
    }

    @GetMapping("/team/{teamId}")
    public ApiResponse<TeamStatsResponse> getTeamStats(@PathVariable Long teamId) {
        return ApiResponse.ok(statsService.getTeamStats(teamId));
    }
}
