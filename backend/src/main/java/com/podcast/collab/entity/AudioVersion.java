package com.podcast.collab.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "audio_versions")
public class AudioVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long episodeId;

    @Column(nullable = false)
    private Integer versionNumber;

    @Column(nullable = false, length = 1024)
    private String fileUrl;

    private Long durationMs;

    /** 文件大小（字节），RSS enclosure length */
    private Long fileSize;

    private Integer sampleRate;

    /** 预生成的波形峰值 JSON 数组 */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String waveformJson;

    /** ACTIVE / ARCHIVED（每集仅保留最近10个 ACTIVE） */
    @Column(nullable = false)
    private String status = "ACTIVE";

    private Long uploadedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
