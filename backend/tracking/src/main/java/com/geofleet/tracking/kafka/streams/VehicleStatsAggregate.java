package com.geofleet.tracking.kafka.streams;

import com.geofleet.tracking.model.dto.VehicleEventDTO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class VehicleStatsAggregate {
    private String vehicleId;
    private double totalSpeed = 0.0;
    private double maxSpeed = 0.0;
    private double lastLat = 0.0;
    private double lastLng = 0.0;
    private double totalDistance = 0.0;
    private int count = 0;
    private long windowStart = 0L;
    private long windowEnd = 0L;

    public VehicleStatsAggregate add(VehicleEventDTO event) {
        if (count == 0) {
            this.vehicleId = event.getVehicleId();
            this.lastLat = event.getLat();
            this.lastLng = event.getLng();
            this.windowStart = System.currentTimeMillis();
        }

        double speed = event.getSpeedKph() != null ? event.getSpeedKph() : 0.0;
        this.totalSpeed += speed;
        this.maxSpeed = Math.max(this.maxSpeed, speed);
        this.count++;

        if (count > 1) {
            double distance = calculateDistance(lastLat, lastLng, event.getLat(), event.getLng());
            this.totalDistance += distance;
        }

        this.lastLat = event.getLat();
        this.lastLng = event.getLng();
        this.windowEnd = System.currentTimeMillis();

        return this;
    }

    public double getAvgSpeed() {
        return count > 0 ? totalSpeed / count : 0.0;
    }

    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371;

        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
    
    
}