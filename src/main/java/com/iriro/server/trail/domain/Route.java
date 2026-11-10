package com.iriro.server.trail.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "routes")
public class Route {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trail_id", nullable = false)
    private Long trailId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "distance_meters", nullable = false, precision = 10, scale = 2)
    private BigDecimal distanceMeters;

    @Column(name = "estimated_duration_seconds", nullable = false)
    private Integer estimatedDurationSeconds;

    @Column(name = "geometry_geojson", nullable = false, columnDefinition = "LONGTEXT")
    private String geometryGeojson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "route_activity_types", joinColumns = @JoinColumn(name = "route_id"))
    @Column(name = "activity_type", nullable = false)
    private Set<String> activityTypes;

    protected Route() {}

    public Long getId() { return id; }
    public Long getTrailId() { return trailId; }
    public String getName() { return name; }
    public BigDecimal getDistanceMeters() { return distanceMeters; }
    public Integer getEstimatedDurationSeconds() { return estimatedDurationSeconds; }
    public String getGeometryGeojson() { return geometryGeojson; }
    public Set<String> getActivityTypes() { return activityTypes; }
}
