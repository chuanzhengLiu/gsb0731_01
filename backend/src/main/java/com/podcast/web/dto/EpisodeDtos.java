package com.podcast.web.dto;

import com.podcast.domain.Episode;
import com.podcast.domain.EpisodeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class EpisodeDtos {

    public record CreateEpisodeRequest(
            @NotNull Integer number,
            @NotBlank String title,
            String theme,
            LocalDate recordDate,
            EpisodeStatus status
    ) {}

    public record UpdateEpisodeRequest(
            Integer number,
            String title,
            String theme,
            LocalDate recordDate,
            EpisodeStatus status,
            String finalAudioUrl
    ) {}

    public record EpisodeResponse(
            Long id,
            Long podcastId,
            Integer number,
            String title,
            String theme,
            LocalDate recordDate,
            String status,
            String finalAudioUrl,
            String createdAt
    ) {
        public static EpisodeResponse from(Episode e) {
            return new EpisodeResponse(
                    e.getId(), e.getPodcastId(), e.getNumber(), e.getTitle(),
                    e.getTheme(), e.getRecordDate(), e.getStatus().name(),
                    e.getFinalAudioUrl(), e.getCreatedAt().toString());
        }
    }
}
