package com.podcast.web;

import com.podcast.service.StatsService;
import com.podcast.web.dto.StatsDtos.TeamStats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Team analytics endpoint (README §4.6). No play-count stats (README §9). */
@RestController
@RequestMapping("/api")
public class StatsController {

    private final StatsService service;

    public StatsController(StatsService service) {
        this.service = service;
    }

    @GetMapping("/stats/team")
    public TeamStats team() {
        return service.teamStats();
    }
}
