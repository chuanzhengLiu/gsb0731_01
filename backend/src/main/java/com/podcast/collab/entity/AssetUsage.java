package com.podcast.collab.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "asset_usages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "episode_id", nullable = false)
    private Long episodeId;

    @Column(name = "time_ms")
    private Long timeMs;

    @Column(name = "used_at", updatable = false)
    private LocalDateTime usedAt;

    @PrePersist
    protected void onCreate() {
        usedAt = LocalDateTime.now();
    }
}
