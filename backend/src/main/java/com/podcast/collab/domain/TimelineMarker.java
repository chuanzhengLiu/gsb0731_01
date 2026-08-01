package com.podcast.collab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "timeline_markers")
@Getter
@Setter
public class TimelineMarker extends BaseEntity {
    @Column(name = "audio_version_id", nullable = false)
    private Long audioVersionId;

    @Column(name = "start_time_ms", nullable = false)
    private Long startTimeMs;

    @Column(name = "end_time_ms")
    private Long endTimeMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Enums.MarkerType type;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Enums.MarkerStatus status = Enums.MarkerStatus.PENDING;

    @Column(name = "screenshot_url")
    private String screenshotUrl;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
