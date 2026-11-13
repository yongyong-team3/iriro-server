-- H2 equivalent for repository tests. MariaDB-only UNSIGNED and COLLATE clauses
-- remain defined by the production Flyway migration.
CREATE TABLE members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_name VARCHAR(12) NULL,
    member_name_key VARCHAR(12) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_members_name_key UNIQUE (member_name_key),
    CONSTRAINT ck_members_name_pair CHECK (
        (member_name IS NULL AND member_name_key IS NULL)
        OR (member_name IS NOT NULL AND member_name_key IS NOT NULL)
    ),
    CONSTRAINT ck_members_name_length CHECK (
        member_name IS NULL OR CHAR_LENGTH(member_name) BETWEEN 2 AND 12
    )
);

CREATE TABLE email_credentials (
    member_id BIGINT NOT NULL,
    email_normalized VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (member_id),
    CONSTRAINT uq_email_credentials_email UNIQUE (email_normalized),
    CONSTRAINT fk_email_credentials_member FOREIGN KEY (member_id)
        REFERENCES members (id) ON DELETE CASCADE
);

CREATE TABLE member_profile_photos (
    member_id BIGINT NOT NULL PRIMARY KEY,
    content_type VARCHAR(32) NOT NULL,
    content LONGBLOB NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_profile_photo_member FOREIGN KEY (member_id)
        REFERENCES members (id) ON DELETE CASCADE
);

CREATE TABLE refresh_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_member FOREIGN KEY (member_id)
        REFERENCES members (id) ON DELETE CASCADE
);

CREATE TABLE social_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    provider VARCHAR(16) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_social_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT uq_social_member_provider UNIQUE (member_id, provider),
    CONSTRAINT ck_social_provider CHECK (provider IN ('KAKAO', 'NAVER')),
    CONSTRAINT fk_social_member FOREIGN KEY (member_id)
        REFERENCES members (id) ON DELETE CASCADE
);

CREATE TABLE oauth_states (
    state_hash CHAR(64) NOT NULL PRIMARY KEY,
    provider VARCHAR(16) NOT NULL,
    redirect_uri VARCHAR(2048) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL
);

CREATE TABLE oauth_login_codes (
    code_hash CHAR(64) NOT NULL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    is_new_member BOOLEAN NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    CONSTRAINT fk_oauth_login_member FOREIGN KEY (member_id)
        REFERENCES members (id) ON DELETE CASCADE
);

CREATE TABLE recovery_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trail_id BIGINT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    first_notified_at DATETIME(6) NULL,
    ended_at DATETIME(6) NULL,
    CONSTRAINT uq_recovery_event_trail_started UNIQUE (trail_id, started_at)
);

CREATE TABLE trails (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL
);

CREATE TABLE routes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    trail_id BIGINT NOT NULL
);

CREATE TABLE favorites (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    route_id BIGINT NOT NULL,
    CONSTRAINT uq_favorite_member_route UNIQUE (member_id, route_id)
);

CREATE TABLE notification_settings (
    member_id BIGINT NOT NULL PRIMARY KEY,
    recovery_forecast_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_notification_settings_member FOREIGN KEY (member_id)
        REFERENCES members (id) ON DELETE CASCADE
);

CREATE TABLE devices (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    installation_id CHAR(36) NOT NULL UNIQUE,
    platform VARCHAR(12) NOT NULL CHECK (platform IN ('IOS', 'ANDROID')),
    expo_push_token VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_device_member FOREIGN KEY (member_id)
        REFERENCES members (id) ON DELETE CASCADE
);

CREATE TABLE pending_push_receipts (
    ticket_id VARCHAR(128) NOT NULL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    push_token VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_pending_push_receipts_device FOREIGN KEY (device_id)
        REFERENCES devices (id) ON DELETE CASCADE
);

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    event_id BIGINT NULL,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(160) NOT NULL,
    body TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    read_at DATETIME(6) NULL,
    CONSTRAINT uq_notification_event_member UNIQUE (event_id, member_id),
    CONSTRAINT ck_notification_event_type CHECK (
        (type = 'RECOVERY_FORECAST' AND event_id IS NOT NULL)
        OR (type <> 'RECOVERY_FORECAST' AND event_id IS NULL)
    ),
    CONSTRAINT fk_notification_member FOREIGN KEY (member_id)
        REFERENCES members (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_event FOREIGN KEY (event_id)
        REFERENCES recovery_events (id)
);
