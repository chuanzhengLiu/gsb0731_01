package com.podcast.collab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "assets")
@Getter
@Setter
public class Asset extends BaseEntity {
    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Enums.AssetType type;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;
}
