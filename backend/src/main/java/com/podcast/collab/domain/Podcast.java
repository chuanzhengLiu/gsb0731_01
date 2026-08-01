package com.podcast.collab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "podcasts")
@Getter
@Setter
public class Podcast extends BaseEntity {
    @Column(nullable = false)
    private String name;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Enums.PodcastType type;

    @Column(name = "update_frequency", length = 32)
    private String updateFrequency;

    @Column(name = "target_duration_seconds")
    private Integer targetDurationSeconds;

    @Column(name = "structure_template_json", columnDefinition = "json")
    private String structureTemplateJson;
}
