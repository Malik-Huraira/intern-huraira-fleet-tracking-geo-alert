package com.geofleet.tracking.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.geofleet.tracking.model.enums.AlertType;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StreamsAlertDTO {
    @JsonProperty("vehicleId")
    private String vehicleId;

    @JsonProperty("alertType")
    private AlertType alertType;

    @JsonProperty("details")
    private String details;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("windowStart")
    private LocalDateTime windowStart;

    @JsonProperty("windowEnd")
    private LocalDateTime windowEnd;
}