package com.podcast.collab.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "platforms")
public class Platform {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long teamId;

    @Column(nullable = false)
    private String name;

    /** 平台账号名 */
    private String accountName;

    /** RSS 必填字段配置 JSON */
    @Column(columnDefinition = "TEXT")
    private String rssRequiredFieldsJson;

    /** 分类选项 JSON */
    @Column(columnDefinition = "TEXT")
    private String categoryOptionsJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
