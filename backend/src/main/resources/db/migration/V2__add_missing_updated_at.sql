-- V2__add_missing_updated_at.sql
-- audio_versions and platform_accounts extend BaseEntity which requires updated_at,
-- but the initial V1 migration only created created_at for them.
ALTER TABLE audio_versions
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);

ALTER TABLE platform_accounts
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
