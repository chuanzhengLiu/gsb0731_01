package com.podcast.collab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "distributions")
@Getter
@Setter
public class Distribution extends BaseEntity {
    @Column(name = "episode_id", nullable = false)
    private Long episodeId;

    @Column(name = "platform_account_id", nullable = false)
    private Long platformAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Enums.DistributionStatus status = Enums.DistributionStatus.NOT_STARTED;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "platform_data_json", columnDefinition = "json")
    private String platformDataJson;

    @Column(name = "rejection_reason")
    private String rejectionReason;
}
