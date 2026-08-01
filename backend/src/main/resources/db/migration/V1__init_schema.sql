-- =====================================================================
-- V1: Core schema for 独立播客制作与分发协作系统
-- Covers P0 tables plus forward-declared tables from the data model so
-- later features (P1/P2) do not require destructive migrations.
-- Engine: InnoDB, charset utf8mb4 (MySQL 8.0)
-- =====================================================================

-- ---------- Team ----------
CREATE TABLE team (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(128) NOT NULL,
    created_by  BIGINT       NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- User ----------
CREATE TABLE app_user (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    email          VARCHAR(190) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    name           VARCHAR(128) NOT NULL,
    role           VARCHAR(32)  NOT NULL,          -- global role
    team_id        BIGINT       NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_email (email),
    KEY idx_user_team (team_id),
    CONSTRAINT fk_user_team FOREIGN KEY (team_id) REFERENCES team (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE team
    ADD CONSTRAINT fk_team_created_by FOREIGN KEY (created_by) REFERENCES app_user (id);

-- ---------- TeamMember ----------
CREATE TABLE team_member (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    team_id       BIGINT      NOT NULL,
    user_id       BIGINT      NOT NULL,
    role_in_team  VARCHAR(32) NOT NULL,
    joined_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_user (team_id, user_id),
    CONSTRAINT fk_tm_team FOREIGN KEY (team_id) REFERENCES team (id),
    CONSTRAINT fk_tm_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- Podcast ----------
CREATE TABLE podcast (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    team_id                  BIGINT       NOT NULL,
    name                     VARCHAR(190) NOT NULL,
    type                     VARCHAR(32)  NOT NULL,   -- 访谈/叙事/知识/新闻
    update_frequency         VARCHAR(64)  NULL,
    target_duration_ms       BIGINT       NULL,
    structure_template_json  JSON         NULL,
    created_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_podcast_team (team_id),
    CONSTRAINT fk_podcast_team FOREIGN KEY (team_id) REFERENCES team (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- Episode ----------
CREATE TABLE episode (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    podcast_id       BIGINT       NOT NULL,
    number           INT          NOT NULL,
    title            VARCHAR(255) NOT NULL,
    theme            VARCHAR(255) NULL,
    record_date      DATE         NULL,
    status           VARCHAR(32)  NOT NULL DEFAULT 'PLANNING',
    final_audio_url  VARCHAR(512) NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_episode_podcast_number (podcast_id, number),
    KEY idx_episode_podcast (podcast_id),
    CONSTRAINT fk_episode_podcast FOREIGN KEY (podcast_id) REFERENCES podcast (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- Task ----------
CREATE TABLE task (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    episode_id   BIGINT       NOT NULL,
    assignee_id  BIGINT       NULL,
    description  VARCHAR(512) NOT NULL,
    due_date     DATE         NULL,
    status       VARCHAR(32)  NOT NULL DEFAULT 'TODO',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_task_episode (episode_id),
    KEY idx_task_assignee (assignee_id),
    CONSTRAINT fk_task_episode FOREIGN KEY (episode_id) REFERENCES episode (id),
    CONSTRAINT fk_task_assignee FOREIGN KEY (assignee_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- AudioVersion ----------
CREATE TABLE audio_version (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    episode_id      BIGINT       NOT NULL,
    version_number  INT          NOT NULL,
    file_url        VARCHAR(512) NOT NULL,
    file_key        VARCHAR(512) NOT NULL,   -- storage key/path
    original_name   VARCHAR(255) NULL,
    content_type    VARCHAR(64)  NULL,
    size_bytes      BIGINT       NULL,
    duration_ms     BIGINT       NULL,
    waveform_json   MEDIUMTEXT   NULL,       -- pre-generated peaks for wavesurfer
    archived        BOOLEAN      NOT NULL DEFAULT FALSE,
    uploaded_by     BIGINT       NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_av_episode_version (episode_id, version_number),
    KEY idx_av_episode (episode_id),
    CONSTRAINT fk_av_episode FOREIGN KEY (episode_id) REFERENCES episode (id),
    CONSTRAINT fk_av_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- TimelineMarker ----------
CREATE TABLE timeline_marker (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    audio_version_id  BIGINT       NOT NULL,
    start_time_ms     BIGINT       NOT NULL,
    end_time_ms       BIGINT       NULL,      -- NULL = point marker (P0); range in P1
    type              VARCHAR(32)  NOT NULL,  -- 口误/补录/音量问题/背景音乐/音效插入/过渡不自然/事实待核实
    description       VARCHAR(1000) NULL,
    screenshot_url    VARCHAR(512) NULL,
    status            VARCHAR(32)  NOT NULL DEFAULT 'PENDING', -- 待处理/处理中/已解决/已忽略
    created_by        BIGINT       NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_marker_av (audio_version_id),
    KEY idx_marker_status (status),
    CONSTRAINT fk_marker_av FOREIGN KEY (audio_version_id) REFERENCES audio_version (id),
    CONSTRAINT fk_marker_created_by FOREIGN KEY (created_by) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- TranscriptSegment (P1) ----------
CREATE TABLE transcript_segment (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    audio_version_id  BIGINT      NOT NULL,
    start_time_ms     BIGINT      NOT NULL,
    end_time_ms       BIGINT      NOT NULL,
    text              TEXT        NOT NULL,
    speaker           VARCHAR(64) NULL,
    PRIMARY KEY (id),
    KEY idx_ts_av (audio_version_id),
    CONSTRAINT fk_ts_av FOREIGN KEY (audio_version_id) REFERENCES audio_version (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- Platform (P2) ----------
CREATE TABLE platform (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    name                     VARCHAR(64)  NOT NULL,
    rss_required_fields_json JSON         NULL,
    category_options_json    JSON         NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- Distribution (P2) ----------
CREATE TABLE distribution (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    episode_id          BIGINT      NOT NULL,
    platform_id         BIGINT      NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED',
    submitted_at        DATETIME    NULL,
    published_at        DATETIME    NULL,
    platform_data_json  JSON        NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dist_episode_platform (episode_id, platform_id),
    CONSTRAINT fk_dist_episode FOREIGN KEY (episode_id) REFERENCES episode (id),
    CONSTRAINT fk_dist_platform FOREIGN KEY (platform_id) REFERENCES platform (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- Asset (P2) ----------
CREATE TABLE asset (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    team_id      BIGINT       NOT NULL,
    name         VARCHAR(190) NOT NULL,
    type         VARCHAR(32)  NOT NULL,  -- 音频素材/文本素材 sub-types
    file_url     VARCHAR(512) NULL,
    text_content TEXT         NULL,
    usage_count  INT          NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_asset_team (team_id),
    CONSTRAINT fk_asset_team FOREIGN KEY (team_id) REFERENCES team (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- AuditLog ----------
CREATE TABLE audit_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NULL,
    team_id      BIGINT       NULL,
    action       VARCHAR(64)  NOT NULL,
    target_type  VARCHAR(64)  NULL,
    target_id    BIGINT       NULL,
    details      VARCHAR(1000) NULL,
    ip_address   VARCHAR(64)  NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_user (user_id),
    KEY idx_audit_team (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- RefreshToken (session management + force logout) ----------
CREATE TABLE refresh_token (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    token_hash   VARCHAR(100) NOT NULL,
    expires_at   DATETIME     NOT NULL,
    revoked      BOOLEAN      NOT NULL DEFAULT FALSE,
    user_agent   VARCHAR(255) NULL,
    ip_address   VARCHAR(64)  NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_user (user_id),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
