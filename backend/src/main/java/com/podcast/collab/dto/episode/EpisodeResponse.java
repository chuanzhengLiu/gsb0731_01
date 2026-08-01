package com.podcast.collab.dto.episode;

import com.podcast.collab.entity.enums.EpisodeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpisodeResponse {

    private Long id;
    private Long podcastId;
    private Integer number;
    private String title;
    private String theme;
    private LocalDate recordDate;
    private EpisodeStatus status;
    private String finalAudioUrl;
    private LocalDateTime scheduledAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String podcastName;
    private String createdByName;
    private long markerCount;
    private long taskCount;
}
