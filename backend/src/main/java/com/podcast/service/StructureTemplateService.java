package com.podcast.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.podcast.domain.AudioVersion;
import com.podcast.domain.Episode;
import com.podcast.domain.Podcast;
import com.podcast.repository.AudioVersionRepository;
import com.podcast.web.dto.StructureTemplateDtos.StructureComparison;
import com.podcast.web.dto.StructureTemplateDtos.StructureTemplate;
import com.podcast.web.dto.StructureTemplateDtos.TemplateSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Structure-template comparison (README §4.1): a podcast defines fixed sections
 * with target durations (stored as structure_template_json); each episode's
 * actual audio duration is compared against the template total.
 */
@Service
public class StructureTemplateService {

    private static final Logger log = LoggerFactory.getLogger(StructureTemplateService.class);

    private final AccessGuard accessGuard;
    private final AudioVersionRepository audioRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public StructureTemplateService(AccessGuard accessGuard, AudioVersionRepository audioRepo) {
        this.accessGuard = accessGuard;
        this.audioRepo = audioRepo;
    }

    /** Parses a podcast's stored template JSON, tolerating null/blank/invalid. */
    public StructureTemplate parseTemplate(Podcast podcast) {
        String json = podcast.getStructureTemplateJson();
        if (json == null || json.isBlank()) {
            return new StructureTemplate(List.of());
        }
        try {
            StructureTemplate t = mapper.readValue(json, StructureTemplate.class);
            return t.sections() == null ? new StructureTemplate(List.of()) : t;
        } catch (Exception e) {
            log.warn("invalid structure_template_json for podcast {}: {}",
                    podcast.getId(), e.getMessage());
            return new StructureTemplate(List.of());
        }
    }

    @Transactional(readOnly = true)
    public StructureComparison compareEpisode(Long episodeId) {
        Episode episode = accessGuard.requireEpisode(episodeId); // team isolation
        Podcast podcast = accessGuard.requirePodcast(episode.getPodcastId());
        StructureTemplate template = parseTemplate(podcast);

        boolean hasTemplate = !template.sections().isEmpty();
        Long templateTotal = hasTemplate
                ? template.sections().stream()
                    .map(TemplateSection::targetDurationMs)
                    .filter(java.util.Objects::nonNull)
                    .mapToLong(Long::longValue).sum()
                : null;

        Long actual = audioRepo.findTopByEpisodeIdOrderByVersionNumberDesc(episodeId)
                .map(AudioVersion::getDurationMs).orElse(null);

        Long diff = (templateTotal != null && actual != null) ? actual - templateTotal : null;

        return new StructureComparison(
                episodeId, hasTemplate, templateTotal, actual, diff, template.sections());
    }
}
