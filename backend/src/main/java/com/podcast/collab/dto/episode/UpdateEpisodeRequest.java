package com.podcast.collab.dto.episode;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UpdateEpisodeRequest {

    private String title;

    private String theme;

    private LocalDate recordDate;

    private LocalDateTime scheduledAt;
}
