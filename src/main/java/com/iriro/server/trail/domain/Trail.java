package com.iriro.server.trail.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "trails")
public class Trail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "section_name", length = 120)
    private String sectionName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "center_latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal centerLatitude;

    @Column(name = "center_longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal centerLongitude;

    @Column(name = "length_meters", nullable = false, precision = 10, scale = 2)
    private BigDecimal lengthMeters;

    @Column(name = "geometry_geojson", nullable = false, columnDefinition = "LONGTEXT")
    private String geometryGeojson;

    @Column(name = "water_station_code", length = 32)
    private String waterStationCode;

    @Column(name = "created_at", nullable = false)
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private java.time.LocalDateTime updatedAt;

    protected Trail() {}

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSectionName() { return sectionName; }
    public String getDescription() { return description; }
    public BigDecimal getCenterLatitude() { return centerLatitude; }
    public BigDecimal getCenterLongitude() { return centerLongitude; }
    public BigDecimal getLengthMeters() { return lengthMeters; }
    public String getGeometryGeojson() { return geometryGeojson; }
    public String getWaterStationCode() { return waterStationCode; }
}
