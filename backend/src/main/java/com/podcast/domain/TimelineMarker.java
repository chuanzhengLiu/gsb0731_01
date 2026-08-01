package com.podcast.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "timeline_marker")
public class TimelineMarker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audio_version_id", nullable = false)
    private Long audioVersionId;

    @Column(name = "start_time_ms", nullable = false)
    private Long startTimeMs;

    /** NULL = point marker (P0). Range end for P1 segment markers. */
    @Column(name = "end_time_ms")
    private Long endTimeMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MarkerType type;

    @Column(length = 1000)
    private String description;

    @Column(name = "screenshot_url", length = 512)
    private String screenshotUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MarkerStatus status = MarkerStatus.PENDING;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAudioVersionId() { return audioVersionId; }
    public void setAudioVersionId(Long audioVersionId) { this.audioVersionId = audioVersionId; }

    public Long getStartTimeMs() { return startTimeMs; }
    public void setStartTimeMs(Long startTimeMs) { this.startTimeMs = startTimeMs; }

    public Long getEndTimeMs() { return endTimeMs; }
    public void setEndTimeMs(Long endTimeMs) { this.endTimeMs = endTimeMs; }

    public MarkerType getType() { return type; }
    public void setType(MarkerType type) { this.type = type; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getScreenshotUrl() { return screenshotUrl; }
    public void setScreenshotUrl(String screenshotUrl) { this.screenshotUrl = screenshotUrl; }

    public MarkerStatus getStatus() { return status; }
    public void setStatus(MarkerStatus status) { this.status = status; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
