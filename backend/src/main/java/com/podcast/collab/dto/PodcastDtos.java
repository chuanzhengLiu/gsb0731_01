package com.podcast.collab.dto;

import com.podcast.collab.domain.Enums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public record PodcastDtos() {

    public record StructureSegment(String name, Integer targetSeconds) {}

    public record PodcastResponse(Long id, String name, Enums.PodcastType type, String updateFrequency,
                                  Integer targetDurationSeconds, String structureTemplateJson,
                                  Instant createdAt) {}

    public record CreatePodcastRequest(
            @NotBlank @Size(max = 160) String name,
            @NotNull Enums.PodcastType type,
            @Size(max = 32) String updateFrequency,
            Integer targetDurationSeconds,
            String structureTemplateJson
    ) {}

    public record UpdatePodcastRequest(
            @Size(max = 160) String name,
            Enums.PodcastType type,
            @Size(max = 32) String updateFrequency,
            Integer targetDurationSeconds,
            String structureTemplateJson
    ) {}

    public record EpisodeResponse(Long id, Long podcastId, Integer number, String title, String theme,
                                  LocalDate recordDate, Enums.EpisodeStatus status, String finalAudioUrl,
                                  Instant scheduledAt, Instant createdAt, Instant updatedAt) {}

    public record CreateEpisodeRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 500) String theme,
            LocalDate recordDate,
            Instant scheduledAt
    ) {}

    public record UpdateEpisodeRequest(
            @Size(max = 255) String title,
            @Size(max = 500) String theme,
            LocalDate recordDate,
            Enums.EpisodeStatus status,
            Instant scheduledAt
    ) {}
}
