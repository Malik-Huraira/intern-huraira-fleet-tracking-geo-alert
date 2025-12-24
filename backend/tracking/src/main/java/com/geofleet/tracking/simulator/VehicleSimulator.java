package com.geofleet.tracking.simulator;

import com.geofleet.tracking.kafka.producer.VehicleGpsProducer;
import com.geofleet.tracking.model.dto.VehicleEventDTO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class VehicleSimulator {

    private final VehicleGpsProducer vehicleGpsProducer;
    private final SimulatorConfig config;

    private final List<VehicleState> vehicles = new ArrayList<>();
    private final Random random = new Random();
    private ScheduledExecutorService scheduler;

    private volatile boolean shuttingDown = false;

    @PostConstruct
    public void init() {
        if (!config.isEnabled()) {
            log.warn("🚫 Vehicle simulator is disabled in configuration");
            return;
        }

        log.info("🚀 Initializing {} simulated vehicles on realistic Karachi routes", config.getVehicleCount());

        // Assign each vehicle to a predefined route
        for (int i = 0; i < config.getVehicleCount(); i++) {
            VehicleState vehicle = new VehicleState("TRK-" + String.format("%02d", i + 1));
            vehicle.assignRoute(); // Start on a random route
            vehicles.add(vehicle);
        }

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::simulateVehicles,
                0, config.getUpdateIntervalMs(), TimeUnit.MILLISECONDS);

        log.info("✅ Simulator started: {} vehicles updating every {}ms on major roads",
                vehicles.size(), config.getUpdateIntervalMs());
    }

    private void simulateVehicles() {
        if (shuttingDown)
            return;

        for (VehicleState vehicle : vehicles) {
            try {
                vehicle.move();

                // Gentle boundary handling – reverse if out of bounds
                if (vehicle.getLat() < config.getMinLat() || vehicle.getLat() > config.getMaxLat() ||
                        vehicle.getLng() < config.getMinLng() || vehicle.getLng() > config.getMaxLng()) {
                    vehicle.reverseDirection();
                }

                VehicleEventDTO event = new VehicleEventDTO();
                event.setVehicleId(vehicle.getVehicleId());
                event.setLat(vehicle.getLat());
                event.setLng(vehicle.getLng());
                event.setSpeedKph(vehicle.getSpeedKph());
                event.setHeading(vehicle.getHeading());
                event.setTimestamp(LocalDateTime.now());

                vehicleGpsProducer.sendVehicleEvent(event);

            } catch (Exception e) {
                log.error("❌ Error simulating vehicle {}: {}", vehicle.getVehicleId(), e.toString(), e);
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        if (scheduler == null || scheduler.isShutdown())
            return;

        log.info("🛑 Shutting down vehicle simulator...");
        shuttingDown = true;

        try {
            vehicleGpsProducer.flush();
            log.info("📤 Flushed pending Kafka messages");
        } catch (Exception e) {
            log.warn("⚠️ Failed to flush Kafka producer", e);
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }

        log.info("✅ Vehicle simulator stopped cleanly");
    }
}