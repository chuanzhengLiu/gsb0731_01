package com.podcast.collab.dto.episode;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CreateEpisodeRequest {

    @NotBlank(message = "Episode title is required")
    private String title;

    private String theme;

    private LocalDate recordDate;

    private Integer number;

    private LocalDateTime scheduledAt;
}
