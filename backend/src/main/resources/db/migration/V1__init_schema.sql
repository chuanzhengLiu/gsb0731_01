-- ============================================================
-- V1__init_schema.sql
-- 独立播客制作与分发协作系统 - 初始数据库架构
-- ============================================================

-- 用户表
CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    avatar_url      VARCHAR(500),
    system_role     VARCHAR(30) NOT NULL DEFAULT 'USER',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 团队表
CREATE TABLE teams (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    created_by      BIGINT NOT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_teams_created_by FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 团队成员表
CREATE TABLE team_members (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id         BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    role_in_team    VARCHAR(30) NOT NULL,
    joined_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tm_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_tm_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_team_user (team_id, user_id),
    INDEX idx_tm_team (team_id),
    INDEX idx_tm_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 团队邀请表
CREATE TABLE team_invitations (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id         BIGINT NOT NULL,
    email           VARCHAR(255) NOT NULL,
    role_in_team    VARCHAR(30) NOT NULL,
    token           VARCHAR(100) NOT NULL UNIQUE,
    invited_by      BIGINT NOT NULL,
    expires_at      DATETIME NOT NULL,
    accepted_at     DATETIME,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ti_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_ti_invited_by FOREIGN KEY (invited_by) REFERENCES users(id),
    INDEX idx_ti_token (token),
    INDEX idx_ti_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 密码重置令牌
CREATE TABLE password_reset_tokens (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    token           VARCHAR(100) NOT NULL UNIQUE,
    expires_at      DATETIME NOT NULL,
    used_at         DATETIME,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_prt_token (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 刷新令牌
CREATE TABLE refresh_tokens (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    device_info     VARCHAR(255),
    ip_address      VARCHAR(45),
    expires_at      DATETIME NOT NULL,
    revoked_at      DATETIME,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_rt_token (token_hash(60)),
    INDEX idx_rt_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 节目表
CREATE TABLE podcasts (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id                 BIGINT NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    type                    VARCHAR(30) NOT NULL,
    update_frequency        VARCHAR(50),
    target_duration         INT,
    structure_template_json JSON,
    description             TEXT,
    cover_image_url         VARCHAR(500),
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_podcasts_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    INDEX idx_podcasts_team (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 单集表
CREATE TABLE episodes (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    podcast_id      BIGINT NOT NULL,
    number          INT NOT NULL,
    title           VARCHAR(300) NOT NULL,
    theme           VARCHAR(500),
    record_date     DATE,
    status          VARCHAR(30) NOT NULL DEFAULT 'PLANNING',
    final_audio_url VARCHAR(500),
    scheduled_at    DATETIME,
    created_by      BIGINT NOT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_episodes_podcast FOREIGN KEY (podcast_id) REFERENCES podcasts(id) ON DELETE CASCADE,
    CONSTRAINT fk_episodes_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    UNIQUE KEY uk_podcast_episode (podcast_id, number),
    INDEX idx_episodes_podcast (podcast_id),
    INDEX idx_episodes_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 任务表
CREATE TABLE tasks (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    episode_id      BIGINT NOT NULL,
    assignee_id     BIGINT,
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    due_date        DATETIME,
    status          VARCHAR(30) NOT NULL DEFAULT 'TODO',
    created_by      BIGINT NOT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tasks_episode FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_assignee FOREIGN KEY (assignee_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_tasks_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_tasks_episode (episode_id),
    INDEX idx_tasks_assignee (assignee_id),
    INDEX idx_tasks_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 音频版本表
CREATE TABLE audio_versions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    episode_id      BIGINT NOT NULL,
    version_number  INT NOT NULL,
    file_url        VARCHAR(500) NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    file_size       BIGINT,
    duration_ms     BIGINT NOT NULL DEFAULT 0,
    waveform_url    VARCHAR(500),
    uploaded_by     BIGINT NOT NULL,
    is_archived     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_av_episode FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE CASCADE,
    CONSTRAINT fk_av_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users(id),
    UNIQUE KEY uk_episode_version (episode_id, version_number),
    INDEX idx_av_episode (episode_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 时间轴标记表
CREATE TABLE timeline_markers (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    audio_version_id    BIGINT NOT NULL,
    start_time_ms       BIGINT NOT NULL,
    end_time_ms         BIGINT,
    type                VARCHAR(30) NOT NULL,
    description         TEXT,
    screenshot_url      VARCHAR(500),
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    assignee_id         BIGINT,
    created_by          BIGINT NOT NULL,
    resolved_at         DATETIME,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_markers_audio_version FOREIGN KEY (audio_version_id) REFERENCES audio_versions(id) ON DELETE CASCADE,
    CONSTRAINT fk_markers_assignee FOREIGN KEY (assignee_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_markers_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_markers_version (audio_version_id),
    INDEX idx_markers_status (status),
    INDEX idx_markers_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 转写片段表
CREATE TABLE transcript_segments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    audio_version_id    BIGINT NOT NULL,
    start_time_ms       BIGINT NOT NULL,
    end_time_ms         BIGINT NOT NULL,
    text                TEXT NOT NULL,
    speaker             VARCHAR(100),
    segment_order       INT NOT NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ts_audio_version FOREIGN KEY (audio_version_id) REFERENCES audio_versions(id) ON DELETE CASCADE,
    INDEX idx_ts_version (audio_version_id),
    INDEX idx_ts_order (audio_version_id, segment_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 平台表
CREATE TABLE platforms (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                        VARCHAR(100) NOT NULL UNIQUE,
    display_name                VARCHAR(100) NOT NULL,
    rss_required_fields_json    JSON,
    category_options_json       JSON,
    is_active                   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 分发表
CREATE TABLE distributions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    episode_id          BIGINT NOT NULL,
    platform_id         BIGINT NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
    platform_data_json  JSON,
    submitted_at        DATETIME,
    published_at        DATETIME,
    reviewed_at         DATETIME,
    rejection_reason    TEXT,
    created_by          BIGINT NOT NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_dist_episode FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE CASCADE,
    CONSTRAINT fk_dist_platform FOREIGN KEY (platform_id) REFERENCES platforms(id),
    CONSTRAINT fk_dist_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    UNIQUE KEY uk_episode_platform (episode_id, platform_id),
    INDEX idx_dist_episode (episode_id),
    INDEX idx_dist_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 素材表
CREATE TABLE assets (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id         BIGINT NOT NULL,
    name            VARCHAR(200) NOT NULL,
    type            VARCHAR(30) NOT NULL,
    category        VARCHAR(50),
    file_url        VARCHAR(500),
    content         TEXT,
    usage_count     INT NOT NULL DEFAULT 0,
    created_by      BIGINT NOT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_assets_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_assets_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_assets_team (team_id),
    INDEX idx_assets_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 素材使用记录表
CREATE TABLE asset_usages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id        BIGINT NOT NULL,
    episode_id      BIGINT NOT NULL,
    time_ms         BIGINT,
    used_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_au_asset FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE CASCADE,
    CONSTRAINT fk_au_episode FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE CASCADE,
    INDEX idx_au_asset (asset_id),
    INDEX idx_au_episode (episode_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 审计日志表
CREATE TABLE audit_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT,
    action          VARCHAR(100) NOT NULL,
    target_type     VARCHAR(50) NOT NULL,
    target_id       BIGINT,
    details         TEXT,
    ip_address      VARCHAR(45),
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_target (target_type, target_id),
    INDEX idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 访客分享链接表
CREATE TABLE share_links (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    episode_id      BIGINT NOT NULL,
    token           VARCHAR(100) NOT NULL UNIQUE,
    created_by      BIGINT NOT NULL,
    expires_at      DATETIME NOT NULL,
    last_accessed_at DATETIME,
    access_count    INT NOT NULL DEFAULT 0,
    is_revoked      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sl_episode FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE CASCADE,
    CONSTRAINT fk_sl_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_sl_token (token),
    INDEX idx_sl_episode (episode_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 分享访问日志
CREATE TABLE share_access_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    share_link_id   BIGINT NOT NULL,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    accessed_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sal_share FOREIGN KEY (share_link_id) REFERENCES share_links(id) ON DELETE CASCADE,
    INDEX idx_sal_share (share_link_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 插入默认平台数据
INSERT INTO platforms (name, display_name, rss_required_fields_json, category_options_json) VALUES
('xiaoyuzhou', '小宇宙',
 '{"shownotes": "string", "shownotes_format": "markdown"}',
 '{"categories": ["访谈", "叙事", "知识", "新闻", "生活", "科技", "文化", "艺术"]}'),
('apple_podcasts', 'Apple Podcasts',
 '{"subtitle": "string", "summary": "string", "author": "string", "explicit": "boolean", "category": "string"}',
 '{"categories": ["Arts", "Business", "Comedy", "Education", "Fiction", "Health", "History", "Leisure", "Music", "News", "Science", "Sports", "Technology", "TV & Film"]}'),
('spotify', 'Spotify',
 '{"description": "string", "category": "string", "explicit": "boolean", "language": "string"}',
 '{"categories": ["Arts", "Business", "Comedy", "Education", "Fiction", "Health", "History", "Leisure", "Music", "News", "Science", "Sports", "Technology"]}'),
('netease', '网易云音乐',
 '{"description": "string", "category": "string", "tags": "array"}',
 '{"categories": ["访谈", "音乐", "知识", "文化", "生活", "科技", "新闻"]}'),
('ximalaya', '喜马拉雅',
 '{"description": "string", "category": "string", "tags": "array", "cover": "string"}',
 '{"categories": ["访谈", "有声书", "知识", "文化", "生活", "科技", "新闻", "娱乐"]}');
