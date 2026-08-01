package com.podcast.collab.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long teamId;

    @Column(nullable = false)
    private String action;

    private String targetType;

    private Long targetId;

    @Column(columnDefinition = "TEXT")
    private String details;

    private String ipAddress;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
