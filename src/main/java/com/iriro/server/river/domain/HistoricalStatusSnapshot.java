package com.iriro.server.river.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "historical_status_snapshots")
@Getter
public class HistoricalStatusSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_code", nullable = false, length = 32)
    private String stationCode;
    @Column(name = "observed_at", nullable = false)
    private LocalDateTime observedAt;
    @Column(name = "ai_evaluated_at", nullable = false)
    private LocalDateTime aiEvaluatedAt;
    @Column(name = "snapshot_recorded_at", nullable = false)
    private LocalDateTime snapshotRecordedAt;
    @Column(name = "water_level_m", nullable = false, precision = 8, scale = 3)
    private BigDecimal waterLevelMeters;
    @Column(name = "water_status", nullable = false, length = 32)
    private String waterStatus;
    @Column(name = "predicted_water_level_m", precision = 8, scale = 3)
    private BigDecimal predictedWaterLevelMeters;
    @Column(name = "forecast_target_at")
    private LocalDateTime forecastTargetAt;
    @Column(name = "trend", length = 32)
    private String trend;
    @Column(name = "recovery_status", length = 32)
    private String recoveryStatus;
    @Column(name = "recovery_eta_at_least_minutes")
    private Integer recoveryEtaAtLeastMinutes;
    @Column(name = "forecast_model_version", length = 64)
    private String forecastModelVersion;
    @Column(name = "recovery_model_version", length = 64)
    private String recoveryModelVersion;
    @Column(name = "data_source", nullable = false, length = 32)
    private String dataSource;
    @Column(name = "data_status", nullable = false, length = 32)
    private String dataStatus;
    @Column(name = "official_control_status", length = 32)
    private String officialControlStatus;
}
