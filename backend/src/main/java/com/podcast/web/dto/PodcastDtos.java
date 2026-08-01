package com.podcast.web.dto;

import com.podcast.domain.Podcast;
import jakarta.validation.constraints.NotBlank;

public class PodcastDtos {

    public record CreatePodcastRequest(
            @NotBlank String name,
            @NotBlank String type,
            String updateFrequency,
            Long targetDurationMs,
            String structureTemplateJson
    ) {}

    public record UpdatePodcastRequest(
            String name,
            String type,
            String updateFrequency,
            Long targetDurationMs,
            String structureTemplateJson
    ) {}

    public record PodcastResponse(
            Long id,
            Long teamId,
            String name,
            String type,
            String updateFrequency,
            Long targetDurationMs,
            String structureTemplateJson,
            String createdAt
    ) {
        public static PodcastResponse from(Podcast p) {
            return new PodcastResponse(
                    p.getId(), p.getTeamId(), p.getName(), p.getType(),
                    p.getUpdateFrequency(), p.getTargetDurationMs(),
                    p.getStructureTemplateJson(), p.getCreatedAt().toString());
        }
    }
}
