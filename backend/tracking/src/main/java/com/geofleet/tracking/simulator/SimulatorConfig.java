package com.geofleet.tracking.simulator;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "simulator")
public class SimulatorConfig {
    private int vehicleCount = 10;
    private int updateIntervalMs = 2000;
    private boolean enabled = true;

    // Extended realistic Karachi area covering major roads
    private double minLat = 24.75;
    private double maxLat = 25.15;
    private double minLng = 66.85;
    private double maxLng = 67.35;
}