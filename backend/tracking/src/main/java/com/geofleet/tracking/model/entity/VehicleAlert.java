package com.geofleet.tracking.model.entity;

import com.geofleet.tracking.model.enums.AlertType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;
import java.time.LocalDateTime;

@Entity
@Table(name = "vehicle_alerts")
@Data
public class VehicleAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false)
    private String vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false)
    private AlertType alertType;

    @Column(name = "details", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String details;

    @JsonProperty("timestamp")
    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lng")
    private Double lng;

    @JsonIgnore  // Exclude from JSON serialization to prevent infinite recursion
    @Column(name = "geom", columnDefinition = "geography(Point, 4326)")
    private Point geom;
}