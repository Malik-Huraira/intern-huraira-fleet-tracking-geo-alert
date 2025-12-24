package com.geofleet.tracking.model.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class VehicleStatusDTO {
    @JsonProperty("vehicleId")
    private String vehicleId;

    @JsonProperty("lastLat")
    private Double lastLat;

    @JsonProperty("lastLng")
    private Double lastLng;

    @JsonProperty("lastSpeed")
    private Double lastSpeed;

    @JsonProperty("lastSeen")
    private LocalDateTime lastSeen;

    @JsonProperty("status")
    private String status;

    @JsonProperty("statusColor")
    private String statusColor;

    @JsonProperty("statusText")
    private String statusText;

    @JsonProperty("timeSinceLastUpdate")
    private String timeSinceLastUpdate;
}