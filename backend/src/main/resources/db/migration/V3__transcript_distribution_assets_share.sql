-- =====================================================================
-- V3: P1 transcription follow-ups + P2 feature tables
--   - transcript_segment gained a speaker colour + ordering column
--   - platform_account (per-team account maintenance, README §4.4)
--   - asset_usage (素材在每集的使用位置和次数, README §4.5)
--   - share_link (访客分享链接，7天过期，访问记录, README §3.1/§8)
-- Core P2 tables (platform / distribution / asset / transcript_segment)
-- were forward-declared in V1; here we only add what was missing.
-- Engine: InnoDB, charset utf8mb4 (MySQL 8.0)
-- =====================================================================

-- ---------- transcript_segment: presentation + edit metadata ----------
ALTER TABLE transcript_segment
    ADD COLUMN segment_index INT          NOT NULL DEFAULT 0 AFTER audio_version_id,
    ADD COLUMN speaker_color VARCHAR(16)  NULL           AFTER speaker,
    ADD COLUMN edited        BOOLEAN      NOT NULL DEFAULT FALSE AFTER speaker_color;

CREATE INDEX idx_ts_av_order ON transcript_segment (audio_version_id, segment_index);

-- ---------- Platform account maintenance (README §4.4 平台账号管理) ----------
CREATE TABLE platform_account (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    team_id      BIGINT       NOT NULL,
    platform_id  BIGINT       NOT NULL,
    account_name VARCHAR(190) NOT NULL,      -- 账号名称/展示名
    account_url  VARCHAR(512) NULL,          -- 主页链接
    notes        VARCHAR(512) NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pa_team_platform (team_id, platform_id),
    KEY idx_pa_team (team_id),
    CONSTRAINT fk_pa_team FOREIGN KEY (team_id) REFERENCES team (id),
    CONSTRAINT fk_pa_platform FOREIGN KEY (platform_id) REFERENCES platform (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- Asset: storage + categorisation columns (README §4.5) ----------
ALTER TABLE asset
    ADD COLUMN file_key     VARCHAR(512) NULL AFTER file_url,
    ADD COLUMN content_type VARCHAR(64)  NULL AFTER file_key,
    ADD COLUMN size_bytes   BIGINT       NULL AFTER content_type,
    ADD COLUMN duration_ms  BIGINT       NULL AFTER size_bytes,
    ADD COLUMN category     VARCHAR(64)  NULL AFTER type;

-- ---------- Asset usage tracking (README §4.5 素材使用追踪) ----------
CREATE TABLE asset_usage (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    asset_id     BIGINT       NOT NULL,
    episode_id   BIGINT       NOT NULL,
    position_ms  BIGINT       NULL,          -- 使用位置（音频时间点，可空表示仅记录使用）
    note         VARCHAR(255) NULL,
    created_by   BIGINT       NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_au_asset (asset_id),
    KEY idx_au_episode (episode_id),
    CONSTRAINT fk_au_asset FOREIGN KEY (asset_id) REFERENCES asset (id),
    CONSTRAINT fk_au_episode FOREIGN KEY (episode_id) REFERENCES episode (id),
    CONSTRAINT fk_au_created_by FOREIGN KEY (created_by) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- Guest share links (README §3.1/§8 访客分享) ----------
CREATE TABLE share_link (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    team_id      BIGINT       NOT NULL,
    episode_id   BIGINT       NOT NULL,
    token_hash   VARCHAR(100) NOT NULL,      -- SHA-256 of the random token
    created_by   BIGINT       NULL,
    expires_at   DATETIME     NOT NULL,      -- 7 days after creation
    revoked      BOOLEAN      NOT NULL DEFAULT FALSE,
    access_count INT          NOT NULL DEFAULT 0,
    last_accessed_at DATETIME NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_share_token_hash (token_hash),
    KEY idx_share_episode (episode_id),
    KEY idx_share_team (team_id),
    CONSTRAINT fk_share_episode FOREIGN KEY (episode_id) REFERENCES episode (id),
    CONSTRAINT fk_share_team FOREIGN KEY (team_id) REFERENCES team (id),
    CONSTRAINT fk_share_created_by FOREIGN KEY (created_by) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- Access log for share links (README §8 访问记录日志) ----------
CREATE TABLE share_access_log (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    share_link_id BIGINT      NOT NULL,
    ip_address    VARCHAR(64) NULL,
    user_agent    VARCHAR(255) NULL,
    accessed_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sal_link (share_link_id),
    CONSTRAINT fk_sal_link FOREIGN KEY (share_link_id) REFERENCES share_link (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
