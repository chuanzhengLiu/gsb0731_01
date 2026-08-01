package com.podcast.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "episode")
public class Episode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
    private EpisodeStatus status = EpisodeStatus.PLANNING;

    @Column(name = "final_audio_url", length = 512)
    private String finalAudioUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPodcastId() { return podcastId; }
    public void setPodcastId(Long podcastId) { this.podcastId = podcastId; }

    public Integer getNumber() { return number; }
    public void setNumber(Integer number) { this.number = number; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public LocalDate getRecordDate() { return recordDate; }
    public void setRecordDate(LocalDate recordDate) { this.recordDate = recordDate; }

    public EpisodeStatus getStatus() { return status; }
    public void setStatus(EpisodeStatus status) { this.status = status; }

    public String getFinalAudioUrl() { return finalAudioUrl; }
    public void setFinalAudioUrl(String finalAudioUrl) { this.finalAudioUrl = finalAudioUrl; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
