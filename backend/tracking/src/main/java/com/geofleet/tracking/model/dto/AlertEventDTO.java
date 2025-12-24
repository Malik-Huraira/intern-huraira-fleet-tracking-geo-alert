package com.geofleet.tracking.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.geofleet.tracking.model.enums.AlertType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertEventDTO {
    @JsonProperty("vehicleId")
    private String vehicleId;

    @JsonProperty("alertType")
    private AlertType alertType;

    @JsonProperty("details")
    private Map<String, Object> details;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("lat")
    private Double lat;

    @JsonProperty("lng")
    private Double lng;

    @JsonProperty("message")
    private String message;

    @JsonProperty("status")
    private String status;
}