package com.geofleet.tracking.model.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class VehicleStatsDTO {
    @JsonProperty("vehicleId")
    private String vehicleId;

    @JsonProperty("avgSpeed")
    private Double avgSpeed;

    @JsonProperty("maxSpeed")
    private Double maxSpeed;

    @JsonProperty("totalDistance")
    private Double totalDistance;

    @JsonProperty("readingCount")
    private Integer readingCount;

    @JsonProperty("windowStart")
    private LocalDateTime windowStart;

    @JsonProperty("windowEnd")
    private LocalDateTime windowEnd;
}