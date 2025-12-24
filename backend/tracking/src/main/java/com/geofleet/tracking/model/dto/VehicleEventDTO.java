package com.geofleet.tracking.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class VehicleEventDTO {
    @JsonProperty("vehicleId")
    private String vehicleId;

    @JsonProperty("lat")
    private Double lat;

    @JsonProperty("lng")
    private Double lng;

    @JsonProperty("speedKph")
    private Double speedKph;

    @JsonProperty("heading")
    private Double heading;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("status")
    private String status;

    @JsonProperty("statusColor")
    private String statusColor;

    @JsonProperty("message")
    private String message;

    // ← NEW FIELD: Current geofence region name
    @JsonProperty("region")
    private String region;
}