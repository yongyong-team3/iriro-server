-- MariaDB 11.2 / InnoDB. Create the iriro database with utf8mb4 before Flyway runs.
-- All DATETIME(6) values are UTC; the application controls timezone conversion.

CREATE TABLE members (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    member_name VARCHAR(12) NULL,
    member_name_key VARCHAR(12) COLLATE utf8mb4_bin NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_members_name_key (member_name_key),
    CONSTRAINT ck_members_name_pair CHECK ((member_name IS NULL AND member_name_key IS NULL) OR (member_name IS NOT NULL AND member_name_key IS NOT NULL)),
    CONSTRAINT ck_members_name_length CHECK (member_name IS NULL OR CHAR_LENGTH(member_name) BETWEEN 2 AND 12)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE email_credentials (
    member_id BIGINT UNSIGNED NOT NULL,
    email_normalized VARCHAR(254) COLLATE utf8mb4_bin NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (member_id),
    UNIQUE KEY uq_email_credentials_email (email_normalized),
    CONSTRAINT fk_email_credentials_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE social_accounts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    member_id BIGINT UNSIGNED NOT NULL,
    provider VARCHAR(16) NOT NULL,
    provider_user_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_social_provider_user (provider, provider_user_id),
    UNIQUE KEY uq_social_member_provider (member_id, provider),
    CONSTRAINT ck_social_provider CHECK (provider IN ('KAKAO', 'NAVER')),
    CONSTRAINT fk_social_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refresh_sessions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    member_id BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_refresh_token_hash (token_hash),
    KEY ix_refresh_member_expires (member_id, expires_at),
    CONSTRAINT fk_refresh_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trails (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    section_name VARCHAR(120) NULL,
    description TEXT NULL,
    center_latitude DECIMAL(9,6) NOT NULL,
    center_longitude DECIMAL(9,6) NOT NULL,
    length_meters DECIMAL(10,2) NOT NULL,
    geometry_geojson LONGTEXT NOT NULL,
    water_station_code VARCHAR(32) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_trails_name (name),
    CONSTRAINT ck_trails_lat CHECK (center_latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_trails_lon CHECK (center_longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_trails_length CHECK (length_meters > 0),
    CONSTRAINT ck_trails_geojson CHECK (JSON_VALID(geometry_geojson))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE routes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    trail_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(120) NOT NULL,
    distance_meters DECIMAL(10,2) NOT NULL,
    estimated_duration_seconds INT UNSIGNED NOT NULL,
    geometry_geojson LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_routes_trail (trail_id),
    CONSTRAINT ck_routes_distance CHECK (distance_meters > 0),
    CONSTRAINT ck_routes_duration CHECK (estimated_duration_seconds > 0),
    CONSTRAINT ck_routes_geojson CHECK (JSON_VALID(geometry_geojson)),
    CONSTRAINT fk_routes_trail FOREIGN KEY (trail_id) REFERENCES trails (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE route_activity_types (
    route_id BIGINT UNSIGNED NOT NULL,
    activity_type VARCHAR(12) NOT NULL,
    PRIMARY KEY (route_id, activity_type),
    CONSTRAINT ck_route_activity_type CHECK (activity_type IN ('WALK', 'RUN', 'BIKE')),
    CONSTRAINT fk_route_activity_route FOREIGN KEY (route_id) REFERENCES routes (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE route_start_points (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    route_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(120) NOT NULL,
    latitude DECIMAL(9,6) NOT NULL,
    longitude DECIMAL(9,6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_start_point_route_id (route_id, id),
    CONSTRAINT ck_start_lat CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_start_lon CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT fk_start_route FOREIGN KEY (route_id) REFERENCES routes (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE detours (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    route_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(120) NOT NULL,
    distance_meters DECIMAL(10,2) NOT NULL,
    extra_distance_meters DECIMAL(10,2) NOT NULL,
    geometry_geojson LONGTEXT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_detour_route_id (route_id, id),
    CONSTRAINT ck_detour_distance CHECK (distance_meters > 0),
    CONSTRAINT ck_detour_extra CHECK (extra_distance_meters >= 0),
    CONSTRAINT ck_detour_geojson CHECK (JSON_VALID(geometry_geojson)),
    CONSTRAINT fk_detour_route FOREIGN KEY (route_id) REFERENCES routes (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE activities (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    member_id BIGINT UNSIGNED NOT NULL,
    route_id BIGINT UNSIGNED NOT NULL,
    start_point_id BIGINT UNSIGNED NOT NULL,
    detour_id BIGINT UNSIGNED NULL,
    activity_type VARCHAR(12) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NOT NULL,
    elapsed_seconds INT UNSIGNED NOT NULL,
    distance_meters DECIMAL(10,2) NOT NULL,
    track_points_json LONGTEXT NOT NULL,
    idempotency_key VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_activity_idempotency (member_id, idempotency_key),
    KEY ix_activity_member_started (member_id, started_at),
    KEY ix_activity_route_start (route_id, start_point_id),
    KEY ix_activity_route_type (route_id, activity_type),
    KEY ix_activity_route_detour (route_id, detour_id),
    CONSTRAINT ck_activity_time CHECK (ended_at > started_at),
    CONSTRAINT ck_activity_elapsed CHECK (elapsed_seconds > 0),
    CONSTRAINT ck_activity_distance CHECK (distance_meters > 0),
    CONSTRAINT ck_activity_track CHECK (JSON_VALID(track_points_json)),
    CONSTRAINT fk_activity_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE,
    CONSTRAINT fk_activity_route FOREIGN KEY (route_id) REFERENCES routes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_start FOREIGN KEY (route_id, start_point_id) REFERENCES route_start_points (route_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_type FOREIGN KEY (route_id, activity_type) REFERENCES route_activity_types (route_id, activity_type) ON DELETE RESTRICT,
    CONSTRAINT fk_activity_detour FOREIGN KEY (route_id, detour_id) REFERENCES detours (route_id, id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE favorites (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    member_id BIGINT UNSIGNED NOT NULL,
    route_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_favorite_member_route (member_id, route_id),
    KEY ix_favorite_route (route_id),
    CONSTRAINT fk_favorite_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE,
    CONSTRAINT fk_favorite_route FOREIGN KEY (route_id) REFERENCES routes (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notification_settings (
    member_id BIGINT UNSIGNED NOT NULL,
    recovery_forecast_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (member_id),
    CONSTRAINT fk_notification_setting_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE devices (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    member_id BIGINT UNSIGNED NOT NULL,
    installation_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    platform VARCHAR(12) NOT NULL,
    expo_push_token VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_device_installation (installation_id),
    KEY ix_device_member (member_id),
    CONSTRAINT ck_device_platform CHECK (platform IN ('IOS', 'ANDROID')),
    CONSTRAINT fk_device_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE recovery_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    trail_id BIGINT UNSIGNED NOT NULL,
    started_at DATETIME(6) NOT NULL,
    first_notified_at DATETIME(6) NULL,
    ended_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_recovery_trail_started (trail_id, started_at),
    CONSTRAINT ck_recovery_end CHECK (ended_at IS NULL OR ended_at > started_at),
    CONSTRAINT fk_recovery_trail FOREIGN KEY (trail_id) REFERENCES trails (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notifications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    member_id BIGINT UNSIGNED NOT NULL,
    event_id BIGINT UNSIGNED NULL,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(160) NOT NULL,
    body TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    read_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_notification_event_member (event_id, member_id),
    KEY ix_notification_member_created (member_id, created_at),
    CONSTRAINT ck_notification_recovery_event CHECK ((type = 'RECOVERY_FORECAST' AND event_id IS NOT NULL) OR (type <> 'RECOVERY_FORECAST' AND event_id IS NULL)),
    CONSTRAINT fk_notification_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_event FOREIGN KEY (event_id) REFERENCES recovery_events (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE badge_awards (
    member_id BIGINT UNSIGNED NOT NULL,
    badge_code VARCHAR(32) NOT NULL,
    earned_at DATETIME(6) NOT NULL,
    PRIMARY KEY (member_id, badge_code),
    CONSTRAINT fk_badge_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
