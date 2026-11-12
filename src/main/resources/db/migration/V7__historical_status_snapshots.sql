-- DATETIME(6) values are UTC. Rows must contain an actual observation and an AI decision stored at that time.
CREATE TABLE historical_status_snapshots (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    station_code VARCHAR(32) NOT NULL,
    observed_at DATETIME(6) NOT NULL,
    ai_evaluated_at DATETIME(6) NOT NULL,
    snapshot_recorded_at DATETIME(6) NOT NULL,
    water_level_m DECIMAL(8,3) NOT NULL,
    water_status VARCHAR(32) NOT NULL,
    predicted_water_level_m DECIMAL(8,3) NULL,
    forecast_target_at DATETIME(6) NULL,
    trend VARCHAR(32) NULL,
    recovery_status VARCHAR(32) NULL,
    recovery_eta_at_least_minutes INT NULL,
    forecast_model_version VARCHAR(64) NULL,
    recovery_model_version VARCHAR(64) NULL,
    data_source VARCHAR(32) NOT NULL,
    data_status VARCHAR(32) NOT NULL,
    official_control_status VARCHAR(32) NULL,
    PRIMARY KEY (id),
    KEY ix_historical_station_observed (station_code, observed_at, ai_evaluated_at),
    CONSTRAINT ck_historical_level CHECK (water_level_m >= 0),
    CONSTRAINT ck_historical_timing CHECK (observed_at <= ai_evaluated_at AND ai_evaluated_at <= snapshot_recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
