package com.podcast.web;

import com.podcast.service.StructureTemplateService;
import com.podcast.web.dto.StructureTemplateDtos.StructureComparison;
import org.springframework.web.bind.annotation.*;

/**
 * Structure-template comparison (README §4.1). The template itself is managed
 * through the podcast's structure_template_json; here we expose the per-episode
 * actual-vs-template comparison.
 */
@RestController
@RequestMapping("/api")
public class StructureTemplateController {

    private final StructureTemplateService service;

    public StructureTemplateController(StructureTemplateService service) {
        this.service = service;
    }

    @GetMapping("/episodes/{episodeId}/structure-comparison")
    public StructureComparison compare(@PathVariable Long episodeId) {
        return service.compareEpisode(episodeId);
    }
}
