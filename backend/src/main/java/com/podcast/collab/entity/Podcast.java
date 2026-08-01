package com.podcast.collab.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "podcasts")
public class Podcast {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long teamId;

    @Column(nullable = false)
    private String name;

    /** 类型：INTERVIEW访谈/NARRATIVE叙事/KNOWLEDGE知识/NEWS新闻 */
    @Column(nullable = false)
    private String type;

    private String updateFrequency;

    /** 目标时长（秒） */
    private Integer targetDuration;

    /** 固定板块结构模板 JSON：[{"name":"开场","durationSec":30},...] */
    @Column(columnDefinition = "TEXT")
    private String structureTemplateJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
