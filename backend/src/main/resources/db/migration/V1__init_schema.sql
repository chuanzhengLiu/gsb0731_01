-- V1__init_schema.sql
-- Independent podcast production & distribution collaboration system

CREATE TABLE teams (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(120) NOT NULL,
    created_by   BIGINT       NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    email             VARCHAR(255) NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    name              VARCHAR(120) NOT NULL,
    role              VARCHAR(32)  NOT NULL,
    active_team_id    BIGINT       NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_active_team (active_team_id),
    CONSTRAINT fk_users_team FOREIGN KEY (active_team_id) REFERENCES teams(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE teams
    ADD CONSTRAINT fk_teams_created_by FOREIGN KEY (created_by) REFERENCES users(id);

CREATE TABLE team_members (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    team_id       BIGINT      NOT NULL,
    user_id       BIGINT      NOT NULL,
    role_in_team  VARCHAR(32) NOT NULL,
    joined_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_member (team_id, user_id),
    KEY idx_tm_user (user_id),
    CONSTRAINT fk_tm_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_tm_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE invitations (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    team_id      BIGINT       NOT NULL,
    email        VARCHAR(255) NOT NULL,
    role_in_team VARCHAR(32)  NOT NULL,
    token        VARCHAR(128) NOT NULL,
    invited_by   BIGINT       NOT NULL,
    expires_at   DATETIME(6)  NOT NULL,
    accepted_at  DATETIME(6)  NULL,
    created_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invitations_token (token),
    KEY idx_inv_team (team_id),
    CONSTRAINT fk_inv_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_inv_inviter FOREIGN KEY (invited_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refresh_tokens (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    token_hash    VARCHAR(255) NOT NULL,
    expires_at    DATETIME(6)  NOT NULL,
    revoked       BIT(1)       NOT NULL DEFAULT b'0',
    user_agent    VARCHAR(255) NULL,
    ip_address    VARCHAR(64)  NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_rt_user (user_id),
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE password_resets (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    used_at     DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pr_token (token_hash),
    KEY idx_pr_user (user_id),
    CONSTRAINT fk_pr_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE podcasts (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    team_id                  BIGINT       NOT NULL,
    name                     VARCHAR(160) NOT NULL,
    type                     VARCHAR(32)  NOT NULL,
    update_frequency         VARCHAR(32)  NULL,
    target_duration_seconds  INT          NULL,
    structure_template_json  JSON         NULL,
    created_at               DATETIME(6)  NOT NULL,
    updated_at               DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_podcasts_team (team_id),
    CONSTRAINT fk_podcasts_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE episodes (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    podcast_id       BIGINT       NOT NULL,
    number           INT          NOT NULL,
    title            VARCHAR(255) NOT NULL,
    theme            VARCHAR(500) NULL,
    record_date      DATE         NULL,
    status           VARCHAR(32)  NOT NULL,
    final_audio_url  VARCHAR(500) NULL,
    scheduled_at     DATETIME(6)  NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_episode_number (podcast_id, number),
    KEY idx_episodes_podcast (podcast_id),
    CONSTRAINT fk_episodes_podcast FOREIGN KEY (podcast_id) REFERENCES podcasts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tasks (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    episode_id   BIGINT       NOT NULL,
    assignee_id  BIGINT       NULL,
    title        VARCHAR(200) NOT NULL,
    description  TEXT         NULL,
    due_date     DATETIME(6)  NULL,
    status       VARCHAR(32)  NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_tasks_episode (episode_id),
    KEY idx_tasks_assignee (assignee_id),
    CONSTRAINT fk_tasks_episode FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_assignee FOREIGN KEY (assignee_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audio_versions (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    episode_id      BIGINT       NOT NULL,
    version_number  INT          NOT NULL,
    file_url        VARCHAR(500) NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    mime_type       VARCHAR(80)  NOT NULL,
    file_size       BIGINT       NOT NULL,
    duration_ms     BIGINT       NOT NULL,
    peaks_url       VARCHAR(500) NULL,
    archived        BIT(1)       NOT NULL DEFAULT b'0',
    uploaded_by     BIGINT       NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_audio_version (episode_id, version_number),
    KEY idx_av_episode (episode_id),
    CONSTRAINT fk_av_episode FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE CASCADE,
    CONSTRAINT fk_av_uploader FOREIGN KEY (uploaded_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE timeline_markers (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    audio_version_id  BIGINT       NOT NULL,
    start_time_ms     BIGINT       NOT NULL,
    end_time_ms       BIGINT       NULL,
    type              VARCHAR(40)  NOT NULL,
    description       TEXT         NOT NULL,
    status            VARCHAR(32)  NOT NULL,
    screenshot_url    VARCHAR(500) NULL,
    created_by        BIGINT       NOT NULL,
    resolved_by       BIGINT       NULL,
    resolved_at       DATETIME(6)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_markers_version (audio_version_id),
    KEY idx_markers_status (status),
    KEY idx_markers_creator (created_by),
    CONSTRAINT fk_markers_version FOREIGN KEY (audio_version_id) REFERENCES audio_versions(id) ON DELETE CASCADE,
    CONSTRAINT fk_markers_creator FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_markers_resolver FOREIGN KEY (resolved_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE transcript_segments (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    audio_version_id  BIGINT      NOT NULL,
    start_time_ms     BIGINT      NOT NULL,
    end_time_ms       BIGINT      NOT NULL,
    text              TEXT        NOT NULL,
    speaker           VARCHAR(64) NULL,
    seq               INT         NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ts_version (audio_version_id),
    CONSTRAINT fk_ts_version FOREIGN KEY (audio_version_id) REFERENCES audio_versions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE platforms (
    id                         BIGINT       NOT NULL AUTO_INCREMENT,
    name                       VARCHAR(80)  NOT NULL,
    code                       VARCHAR(40)  NOT NULL,
    rss_required_fields_json   JSON         NULL,
    category_options_json      JSON         NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platforms_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE platform_accounts (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    team_id      BIGINT       NOT NULL,
    platform_id  BIGINT       NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    credentials_json JSON     NULL,
    created_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_pa_team (team_id),
    CONSTRAINT fk_pa_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_pa_platform FOREIGN KEY (platform_id) REFERENCES platforms(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE distributions (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    episode_id           BIGINT       NOT NULL,
    platform_account_id  BIGINT       NOT NULL,
    status               VARCHAR(32)  NOT NULL,
    submitted_at         DATETIME(6)  NULL,
    published_at         DATETIME(6)  NULL,
    platform_data_json   JSON         NULL,
    rejection_reason     VARCHAR(500) NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_distribution (episode_id, platform_account_id),
    KEY idx_dist_episode (episode_id),
    CONSTRAINT fk_dist_episode FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE CASCADE,
    CONSTRAINT fk_dist_account FOREIGN KEY (platform_account_id) REFERENCES platform_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE assets (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    team_id      BIGINT       NOT NULL,
    name         VARCHAR(160) NOT NULL,
    type         VARCHAR(32)  NOT NULL,
    file_url     VARCHAR(500) NULL,
    content      TEXT         NULL,
    usage_count  INT          NOT NULL DEFAULT 0,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_assets_team (team_id),
    CONSTRAINT fk_assets_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE asset_usages (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    asset_id     BIGINT      NOT NULL,
    episode_id   BIGINT      NOT NULL,
    used_at_ms   BIGINT      NULL,
    used_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_au_asset (asset_id),
    KEY idx_au_episode (episode_id),
    CONSTRAINT fk_au_asset FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE CASCADE,
    CONSTRAINT fk_au_episode FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE share_links (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    episode_id  BIGINT       NOT NULL,
    token       VARCHAR(128) NOT NULL,
    created_by  BIGINT       NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_share_token (token),
    KEY idx_share_episode (episode_id),
    CONSTRAINT fk_share_episode FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE CASCADE,
    CONSTRAINT fk_share_creator FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE share_access_logs (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    share_link_id BIGINT       NOT NULL,
    ip_address    VARCHAR(64)  NULL,
    user_agent    VARCHAR(255) NULL,
    accessed_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_sal_share (share_link_id),
    CONSTRAINT fk_sal_share FOREIGN KEY (share_link_id) REFERENCES share_links(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_logs (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NULL,
    team_id      BIGINT       NULL,
    action       VARCHAR(64)  NOT NULL,
    target_type  VARCHAR(64)  NULL,
    target_id    VARCHAR(64)  NULL,
    details      JSON         NULL,
    ip_address   VARCHAR(64)  NULL,
    created_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_user (user_id),
    KEY idx_audit_team (team_id),
    KEY idx_audit_action (action),
    KEY idx_audit_target (target_type, target_id),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_audit_team FOREIGN KEY (team_id) REFERENCES teams(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed platforms
INSERT INTO platforms (name, code, rss_required_fields_json, category_options_json) VALUES
('小宇宙', 'xiaoyuzhou', '["shownotes"]', '[]'),
('Apple Podcasts', 'apple', '["subtitle","summary","category","explicit"]', '["Arts","Business","Comedy","Education","News","Society & Culture","Technology"]'),
('Spotify', 'spotify', '["description","category","explicit"]', '["Arts","Business","Comedy","Education","Leisure","News","Science","Society & Culture","Sports","Technology"]'),
('网易云音乐', 'netease', '["shownotes","category"]', '[]'),
('喜马拉雅', 'ximalaya', '["shownotes","category"]', '[]');
