package com.podcast.collab.entity;

import com.podcast.collab.entity.enums.MarkerStatus;
import com.podcast.collab.entity.enums.MarkerType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "timeline_markers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimelineMarker {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "audio_version_id", nullable = false)
    private Long audioVersionId;

    @Column(name = "start_time_ms", nullable = false)
    private Long startTimeMs;

    @Column(name = "end_time_ms")
    private Long endTimeMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarkerType type;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "screenshot_url")
    private String screenshotUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MarkerStatus status = MarkerStatus.PENDING;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
