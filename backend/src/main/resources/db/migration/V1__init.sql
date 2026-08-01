-- 播客制作协作系统 初始数据库结构
CREATE TABLE teams (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    team_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_team FOREIGN KEY (team_id) REFERENCES teams(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE team_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_in_team VARCHAR(32) NOT NULL,
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_team_user (team_id, user_id),
    CONSTRAINT fk_tm_team FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT fk_tm_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE team_invites (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    token VARCHAR(128) NOT NULL UNIQUE,
    role_in_team VARCHAR(32) NOT NULL,
    expires_at DATETIME NOT NULL,
    used TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invite_team FOREIGN KEY (team_id) REFERENCES teams(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token VARCHAR(128) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    used TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    refresh_token VARCHAR(512) NOT NULL UNIQUE,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(512) NULL,
    revoked TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE podcasts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    update_frequency VARCHAR(64) NULL,
    target_duration INT NULL COMMENT '目标时长(秒)',
    structure_template_json TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_podcast_team FOREIGN KEY (team_id) REFERENCES teams(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE episodes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    podcast_id BIGINT NOT NULL,
    number INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    theme VARCHAR(512) NULL,
    record_date DATE NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PLANNING',
    final_audio_url VARCHAR(1024) NULL,
    scheduled_publish_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_podcast_number (podcast_id, number),
    CONSTRAINT fk_episode_podcast FOREIGN KEY (podcast_id) REFERENCES podcasts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    episode_id BIGINT NOT NULL,
    assignee_id BIGINT NULL,
    description VARCHAR(1024) NOT NULL,
    due_date DATE NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'TODO',
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_task_episode FOREIGN KEY (episode_id) REFERENCES episodes(id),
    CONSTRAINT fk_task_assignee FOREIGN KEY (assignee_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audio_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    episode_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    file_url VARCHAR(1024) NOT NULL,
    duration_ms BIGINT NULL,
    sample_rate INT NULL,
    waveform_json MEDIUMTEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    uploaded_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_episode_version (episode_id, version_number),
    CONSTRAINT fk_av_episode FOREIGN KEY (episode_id) REFERENCES episodes(id),
    CONSTRAINT fk_av_uploader FOREIGN KEY (uploaded_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE timeline_markers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    audio_version_id BIGINT NOT NULL,
    episode_id BIGINT NOT NULL,
    start_time_ms BIGINT NOT NULL,
    end_time_ms BIGINT NULL COMMENT '为空表示点标记',
    type VARCHAR(32) NOT NULL,
    description TEXT NULL,
    screenshot_url VARCHAR(1024) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_by BIGINT NOT NULL,
    resolved_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_marker_episode (episode_id),
    CONSTRAINT fk_marker_version FOREIGN KEY (audio_version_id) REFERENCES audio_versions(id),
    CONSTRAINT fk_marker_episode FOREIGN KEY (episode_id) REFERENCES episodes(id),
    CONSTRAINT fk_marker_creator FOREIGN KEY (created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE transcript_segments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    audio_version_id BIGINT NOT NULL,
    start_time_ms BIGINT NOT NULL,
    end_time_ms BIGINT NOT NULL,
    text TEXT NOT NULL,
    speaker VARCHAR(128) NULL,
    edited TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_ts_version (audio_version_id),
    CONSTRAINT fk_ts_version FOREIGN KEY (audio_version_id) REFERENCES audio_versions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE platforms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    account_name VARCHAR(255) NULL,
    rss_required_fields_json TEXT NULL,
    category_options_json TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_platform_team FOREIGN KEY (team_id) REFERENCES teams(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE distributions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    episode_id BIGINT NOT NULL,
    platform_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED',
    submitted_at DATETIME NULL,
    published_at DATETIME NULL,
    scheduled_at DATETIME NULL,
    platform_data_json TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_episode_platform (episode_id, platform_id),
    CONSTRAINT fk_dist_episode FOREIGN KEY (episode_id) REFERENCES episodes(id),
    CONSTRAINT fk_dist_platform FOREIGN KEY (platform_id) REFERENCES platforms(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(16) NOT NULL COMMENT 'AUDIO/TEXT',
    category VARCHAR(128) NULL,
    file_url VARCHAR(1024) NULL,
    content TEXT NULL,
    usage_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_asset_team FOREIGN KEY (team_id) REFERENCES teams(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE asset_usages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    episode_id BIGINT NOT NULL,
    position_ms BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_au_asset FOREIGN KEY (asset_id) REFERENCES assets(id),
    CONSTRAINT fk_au_episode FOREIGN KEY (episode_id) REFERENCES episodes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE share_links (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    episode_id BIGINT NOT NULL,
    token VARCHAR(128) NOT NULL UNIQUE,
    created_by BIGINT NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sl_episode FOREIGN KEY (episode_id) REFERENCES episodes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    team_id BIGINT NULL,
    action VARCHAR(128) NOT NULL,
    target_type VARCHAR(64) NULL,
    target_id BIGINT NULL,
    details TEXT NULL,
    ip_address VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_team (team_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
