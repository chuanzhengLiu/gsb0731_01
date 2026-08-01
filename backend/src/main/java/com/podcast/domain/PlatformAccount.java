package com.podcast.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** A team's maintained account on a distribution platform (README §4.4). */
@Entity
@Table(name = "platform_account")
public class PlatformAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "platform_id", nullable = false)
    private Long platformId;

    @Column(name = "account_name", nullable = false, length = 190)
    private String accountName;

    @Column(name = "account_url", length = 512)
    private String accountUrl;

    @Column(length = 512)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public Long getPlatformId() { return platformId; }
    public void setPlatformId(Long platformId) { this.platformId = platformId; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public String getAccountUrl() { return accountUrl; }
    public void setAccountUrl(String accountUrl) { this.accountUrl = accountUrl; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
