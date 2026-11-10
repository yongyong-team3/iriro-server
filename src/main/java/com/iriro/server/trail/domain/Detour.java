package com.iriro.server.trail.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "detours")
public class Detour {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_id", nullable = false)
    private Long routeId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "distance_meters", nullable = false, precision = 10, scale = 2)
    private BigDecimal distanceMeters;

    @Column(name = "extra_distance_meters", nullable = false, precision = 10, scale = 2)
    private BigDecimal extraDistanceMeters;

    @Column(name = "geometry_geojson", nullable = false, columnDefinition = "LONGTEXT")
    private String geometryGeojson;

    protected Detour() {}

    public Long getId() { return id; }
    public Long getRouteId() { return routeId; }
    public String getName() { return name; }
    public BigDecimal getDistanceMeters() { return distanceMeters; }
    public BigDecimal getExtraDistanceMeters() { return extraDistanceMeters; }
    public String getGeometryGeojson() { return geometryGeojson; }
}
