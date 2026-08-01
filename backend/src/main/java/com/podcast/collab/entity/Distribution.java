package com.podcast.collab.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "distributions")
public class Distribution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long episodeId;

    @Column(nullable = false)
    private Long platformId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(32)")
    private DistributionStatus status = DistributionStatus.NOT_STARTED;

    private LocalDateTime submittedAt;

    private LocalDateTime publishedAt;

    /** 排期发布时间（发布日历） */
    private LocalDateTime scheduledAt;

    /** 各平台专属信息（shownotes格式、分类标签等） */
    @Column(columnDefinition = "TEXT")
    private String platformDataJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
