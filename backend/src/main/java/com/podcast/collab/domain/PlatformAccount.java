package com.podcast.collab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "platform_accounts")
@Getter
@Setter
public class PlatformAccount extends BaseEntity {
    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "platform_id", nullable = false)
    private Long platformId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "credentials_json", columnDefinition = "json")
    private String credentialsJson;
}
