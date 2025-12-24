package com.geofleet.tracking.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Polygon; // Add this import

import java.time.LocalDateTime;

@Entity
@Table(name = "geofences")
@Data
public class GeoFence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "polygon_geojson", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String polygonGeojson;

    // NEW: PostGIS geometry column
    @Column(name = "polygon_geom", columnDefinition = "geometry(Polygon, 4326)")
    private Polygon polygonGeom;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}