-- =====================================================================
-- V2: Member invitations (README §3.2 邀请机制, 24h) and password
-- recovery (README §3.2 密码找回, 30min). Tokens are stored hashed
-- (SHA-256), mirroring the refresh_token design in V1.
-- Engine: InnoDB, charset utf8mb4 (MySQL 8.0)
-- =====================================================================

-- ---------- Invitation ----------
CREATE TABLE invitation (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    team_id       BIGINT       NOT NULL,
    email         VARCHAR(190) NOT NULL,
    role_in_team  VARCHAR(32)  NOT NULL,          -- role granted on accept
    token_hash    VARCHAR(100) NOT NULL,
    invited_by    BIGINT       NULL,
    expires_at    DATETIME     NOT NULL,          -- created_at + 24h
    accepted      BOOLEAN      NOT NULL DEFAULT FALSE,
    accepted_at   DATETIME     NULL,
    revoked       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invitation_token_hash (token_hash),
    KEY idx_invitation_team (team_id),
    KEY idx_invitation_email (email),
    CONSTRAINT fk_invitation_team FOREIGN KEY (team_id) REFERENCES team (id),
    CONSTRAINT fk_invitation_invited_by FOREIGN KEY (invited_by) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- PasswordResetToken ----------
CREATE TABLE password_reset_token (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(100) NOT NULL,
    expires_at  DATETIME     NOT NULL,            -- created_at + 30min
    used        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reset_token_hash (token_hash),
    KEY idx_reset_user (user_id),
    CONSTRAINT fk_reset_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
