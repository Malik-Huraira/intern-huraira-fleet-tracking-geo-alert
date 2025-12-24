package com.geofleet.tracking.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.locationtech.jts.geom.Point; // Add this import
import java.time.LocalDateTime;

@Entity
@Table(name = "vehicle_readings")
@Data
public class VehicleReading {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false)
    private String vehicleId;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lng")
    private Double lng;

    // NEW: PostGIS geography point column
    @Column(name = "location", columnDefinition = "geography(Point, 4326)")
    private Point location;

    @Column(name = "speed_kph")
    private Double speedKph;

    @Column(name = "heading")
    private Double heading;

    @Column(name = "event_timestamp", nullable = false)
    private LocalDateTime eventTimestamp;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        // Set location from lat/lng
        if (this.lat != null && this.lng != null) {
            // This will be set by service layer using geometryFactory
        }
    }
}