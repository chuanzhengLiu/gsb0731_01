package com.podcast.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A per-episode, per-platform distribution task (README §4.4). Status is tracked
 * independently for each platform; {@code platformDataJson} holds the
 * platform-specific info (shownotes, category tags, etc.).
 */
@Entity
@Table(name = "distribution")
public class Distribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "episode_id", nullable = false)
    private Long episodeId;

    @Column(name = "platform_id", nullable = false)
    private Long platformId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DistributionStatus status = DistributionStatus.NOT_STARTED;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "platform_data_json", columnDefinition = "json")
    private String platformDataJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEpisodeId() { return episodeId; }
    public void setEpisodeId(Long episodeId) { this.episodeId = episodeId; }

    public Long getPlatformId() { return platformId; }
    public void setPlatformId(Long platformId) { this.platformId = platformId; }

    public DistributionStatus getStatus() { return status; }
    public void setStatus(DistributionStatus status) { this.status = status; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public String getPlatformDataJson() { return platformDataJson; }
    public void setPlatformDataJson(String v) { this.platformDataJson = v; }
}
