package com.podcast.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "podcast")
public class Podcast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(name = "update_frequency", length = 64)
    private String updateFrequency;

    @Column(name = "target_duration_ms")
    private Long targetDurationMs;

    @Column(name = "structure_template_json", columnDefinition = "json")
    private String structureTemplateJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getUpdateFrequency() { return updateFrequency; }
    public void setUpdateFrequency(String updateFrequency) { this.updateFrequency = updateFrequency; }

    public Long getTargetDurationMs() { return targetDurationMs; }
    public void setTargetDurationMs(Long targetDurationMs) { this.targetDurationMs = targetDurationMs; }

    public String getStructureTemplateJson() { return structureTemplateJson; }
    public void setStructureTemplateJson(String structureTemplateJson) { this.structureTemplateJson = structureTemplateJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
