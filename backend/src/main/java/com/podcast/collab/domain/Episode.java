package com.podcast.collab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "episodes")
@Getter
@Setter
public class Episode extends BaseEntity {
    @Column(name = "podcast_id", nullable = false)
    private Long podcastId;

    @Column(nullable = false)
    private Integer number;

    @Column(nullable = false)
    private String title;

    private String theme;

    @Column(name = "record_date")
    private LocalDate recordDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Enums.EpisodeStatus status;

    @Column(name = "final_audio_url")
    private String finalAudioUrl;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;
}
