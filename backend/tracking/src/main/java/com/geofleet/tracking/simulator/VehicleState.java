package com.geofleet.tracking.simulator;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Data
public class VehicleState {

    private final String vehicleId;
    private double lat;
    private double lng;
    private double speedKph;
    private double heading;
    private LocalDateTime timestamp;

    private int idleCounter = 0;
    private boolean inLongIdleMode = false;
    private int longIdleTicks = 0;

    private static final Random random = new Random();

    // Define major realistic routes in Karachi (approximate waypoints based on real
    // roads)
    private static final List<List<double[]>> ROUTES = new ArrayList<>();

    static {
        // Route 1: Airport → Shahrah-e-Faisal → Warehouse A area
        ROUTES.add(List.of(
                new double[] { 24.8600, 67.1600 }, // Near Airport
                new double[] { 24.8700, 67.1000 },
                new double[] { 24.8850, 67.0500 },
                new double[] { 24.8950, 67.0150 }, // Inside/near Warehouse A (24.89-24.90, 67.01-67.02)
                new double[] { 24.9100, 67.0350 }, // Near Delivery Zone
                new double[] { 24.9200, 67.0700 },
                new double[] { 24.8600, 67.1600 } // Back
        ));

        // Route 2: Direct loop around both geofences
        ROUTES.add(List.of(
                new double[] { 24.8800, 67.0000 }, // Outside Warehouse A
                new double[] { 24.8950, 67.0150 }, // Enter Warehouse A
                new double[] { 24.9050, 67.0200 },
                new double[] { 24.9150, 67.0350 }, // Enter Delivery Zone
                new double[] { 24.9200, 67.0400 },
                new double[] { 24.9100, 67.0500 }, // Exit Delivery Zone
                new double[] { 24.8900, 67.0200 }, // Exit Warehouse A
                new double[] { 24.8800, 67.0000 }));

        // Route 3: Malir → Korangi → Delivery Zone
        ROUTES.add(List.of(
                new double[] { 24.8500, 67.2000 },
                new double[] { 24.8700, 67.1500 },
                new double[] { 24.9000, 67.0800 },
                new double[] { 24.9150, 67.0350 }, // Inside Delivery Zone
                new double[] { 24.8500, 67.2000 }));

        // Route 4: North to South crossing geofences
        ROUTES.add(List.of(
                new double[] { 24.9500, 67.1000 }, // Gulshan/North
                new double[] { 24.9200, 67.0700 },
                new double[] { 24.8950, 67.0400 }, // Cross Warehouse A
                new double[] { 24.8700, 67.0200 },
                new double[] { 24.9500, 67.1000 }));
    }

    private List<double[]> currentRoute;
    private int routeIndex = 0;
    private boolean forward = true;

    public VehicleState(String vehicleId) {
        this.vehicleId = vehicleId;
        assignRoute();
    }

    public void assignRoute() {
        currentRoute = ROUTES.get(random.nextInt(ROUTES.size()));
        routeIndex = 0;
        forward = true;
        double[] start = currentRoute.get(0);
        this.lat = start[0];
        this.lng = start[1];
        this.speedKph = 40 + random.nextDouble() * 40;
        this.heading = random.nextDouble() * 360;

        // For specific vehicles, assign fixed routes for testing purposes
        int routeIdx = vehicleId.endsWith("01") || vehicleId.endsWith("02") ||
                vehicleId.endsWith("03") || vehicleId.endsWith("04") ? 1 : random.nextInt(ROUTES.size());
        currentRoute = ROUTES.get(routeIdx);
    }

    public void move() {
        timestamp = LocalDateTime.now();

        // Idle simulation (realistic stops at traffic)
        if (random.nextDouble() < 0.15) { // 15% chance per update → more frequent stops
            speedKph = 0.0;
            idleCounter++;

            // Higher chance to enter long idle after ~20 seconds of stopping
            if (idleCounter > 10 && !inLongIdleMode && random.nextDouble() < 1.0) { // ← 100% chance
                inLongIdleMode = true;
                longIdleTicks = 0;
                System.out.println("😴 [" + vehicleId + "] ENTERED LONG IDLE – will stay stopped for testing");
            }

            if (inLongIdleMode) {
                longIdleTicks++;
                speedKph = 0.0; // Stay stopped

                // Stay in long idle for at least 10 minutes (300 updates @ 2s = 10 min)
                // Resume only after 12–20 minutes for variety
                if (longIdleTicks > 300 && random.nextDouble() < 0.1) { // 10% chance to resume
                    inLongIdleMode = false;
                    idleCounter = 0;
                    speedKph = 40 + random.nextDouble() * 60;
                    System.out.println("🚙 [" + vehicleId + "] RESUMING after long idle");
                }
                return; // Skip sending event → simulates real device offline
            }
        } else {
            // Normal movement
            if (idleCounter > 0 && !inLongIdleMode) {
                System.out.println("🚙 [" + vehicleId + "] Resuming from short stop");
                idleCounter = 0;
            }

            // Follow route waypoints
            if (currentRoute != null && !currentRoute.isEmpty()) {
                double[] target = currentRoute.get(routeIndex);
                double targetLat = target[0];
                double targetLng = target[1];

                // Calculate heading towards target
                double deltaLat = targetLat - lat;
                double deltaLng = targetLng - lng;
                double distance = Math.sqrt(deltaLat * deltaLat + deltaLng * deltaLng);

                if (distance < 0.0005) { // Close enough to waypoint (~50m)
                    // Move to next waypoint
                    if (forward) {
                        routeIndex++;
                        if (routeIndex >= currentRoute.size()) {
                            forward = false;
                            routeIndex = currentRoute.size() - 2;
                        }
                    } else {
                        routeIndex--;
                        if (routeIndex < 0) {
                            forward = true;
                            routeIndex = 1;
                        }
                    }
                } else {
                    // Move towards target
                    heading = Math.toDegrees(Math.atan2(deltaLng, deltaLat));
                    if (heading < 0)
                        heading += 360;

                    double moveDistance = (speedKph / 3600.0) * 2.0 / 111.0; // 2s update
                    lat += moveDistance * Math.cos(Math.toRadians(heading));
                    lng += moveDistance * Math.sin(Math.toRadians(heading));
                }
            }
            
            // Log current position for debugging
            System.out.println("[" + vehicleId + "] At waypoint " + routeIndex + " (" + lat + ", " + lng + ")");

            // Realistic but allows speeding
            speedKph += (random.nextDouble() - 0.5) * 25.0; // ±12.5 kph change
            speedKph = Math.max(20, Math.min(120, speedKph)); // Min 20 to avoid crawling
            
            // For specific vehicles, enforce higher speeds for testing speeding alerts
            if (vehicleId.equals("TRK-01")) {
                speedKph = 90 + random.nextDouble() * 30; // 90-120 kph
            }
        }
    }

    public void reverseDirection() {
        heading = (heading + 180) % 360;
        System.out.println("📍 [" + vehicleId + "] Reversing direction at boundary");
    }
}