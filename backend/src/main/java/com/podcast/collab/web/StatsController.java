package com.podcast.collab.web;

import com.podcast.collab.dto.StatsDtos.*;
import com.podcast.collab.security.SecurityUtils;
import com.podcast.collab.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/episodes/{episodeId}")
    public EpisodeStats episode(@PathVariable Long episodeId) {
        return statsService.episodeStats(episodeId, SecurityUtils.requireUser());
    }

    @GetMapping("/team")
    public TeamStats team() {
        return statsService.teamStats(SecurityUtils.requireUser());
    }

    @GetMapping("/distributions")
    public List<DistributionCoverage> distributions() {
        return statsService.distributionCoverage(SecurityUtils.requireUser());
    }

    @GetMapping("/episodes/{episodeId}/structure")
    public List<StructureComparison> structure(@PathVariable Long episodeId) {
        return statsService.compareStructure(episodeId, SecurityUtils.requireUser());
    }
}
