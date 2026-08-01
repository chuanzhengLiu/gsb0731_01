package com.podcast.collab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "asset_usages")
@Getter
@Setter
public class AssetUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "episode_id", nullable = false)
    private Long episodeId;

    @Column(name = "used_at_ms")
    private Long usedAtMs;

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;
}
