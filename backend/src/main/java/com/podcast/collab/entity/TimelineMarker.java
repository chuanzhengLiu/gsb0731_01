package com.podcast.collab.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "timeline_markers")
public class TimelineMarker {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long audioVersionId;

    @Column(nullable = false)
    private Long episodeId;

    @Column(nullable = false)
    private Long startTimeMs;

    /** 为空表示点标记 */
    private Long endTimeMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(32)")
    private MarkerType type;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 1024)
    private String screenshotUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(32)")
    private MarkerStatus status = MarkerStatus.PENDING;

    @Column(nullable = false)
    private Long createdBy;

    private Long resolvedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
