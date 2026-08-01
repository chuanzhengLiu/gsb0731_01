package com.podcast.collab.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "share_access_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareAccessLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "share_link_id", nullable = false)
    private Long shareLinkId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "accessed_at", updatable = false)
    private LocalDateTime accessedAt;

    @PrePersist
    protected void onCreate() {
        accessedAt = LocalDateTime.now();
    }
}
