package com.podcast.collab.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "assets")
public class Asset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long teamId;

    @Column(nullable = false)
    private String name;

    /** AUDIO（音频素材） / TEXT（文本素材） */
    @Column(nullable = false)
    private String type;

    /** 分类：开场音乐/过渡音效/广告片花/口播文案/slogan 等 */
    private String category;

    @Column(length = 1024)
    private String fileUrl;

    /** 文本素材内容 */
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Integer usageCount = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
