package com.podcast.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Records where/how often an asset is used in an episode (README §4.5 素材使用
 * 追踪：统计每个素材在每集中使用的位置和次数).
 */
@Entity
@Table(name = "asset_usage")
public class AssetUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "episode_id", nullable = false)
    private Long episodeId;

    @Column(name = "position_ms")
    private Long positionMs;

    @Column(length = 255)
    private String note;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }

    public Long getEpisodeId() { return episodeId; }
    public void setEpisodeId(Long episodeId) { this.episodeId = episodeId; }

    public Long getPositionMs() { return positionMs; }
    public void setPositionMs(Long positionMs) { this.positionMs = positionMs; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
