package com.podcast.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Access log entry for a guest share link (README §8 访问记录日志). */
@Entity
@Table(name = "share_access_log")
public class ShareAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "share_link_id", nullable = false)
    private Long shareLinkId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "accessed_at", nullable = false, updatable = false)
    private Instant accessedAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getShareLinkId() { return shareLinkId; }
    public void setShareLinkId(Long shareLinkId) { this.shareLinkId = shareLinkId; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public Instant getAccessedAt() { return accessedAt; }
    public void setAccessedAt(Instant accessedAt) { this.accessedAt = accessedAt; }
}
