package com.geofleet.tracking.kafka.streams;

import java.time.Duration;
import lombok.Data;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class IdleState {
    private String vehicleId;
    private Instant firstIdleTime; // When vehicle first went idle
    private Instant lastUpdateTime; // Last time we saw this vehicle
    private boolean isCurrentlyIdle = false;
    private boolean alertSent = false; // Prevent duplicate alerts
    private double lastSpeed = 0.0; // Track last known speed

    public void update(double currentSpeed, Instant timestamp) {
        this.lastSpeed = currentSpeed;
        this.lastUpdateTime = timestamp;

        boolean isIdleNow = currentSpeed <= 1.0; // Consider ≤ 1 kph as idle

        if (isIdleNow) {
            if (!isCurrentlyIdle || firstIdleTime == null) {
                // Vehicle just became idle OR was never properly initialized
                firstIdleTime = timestamp;
                isCurrentlyIdle = true;
                alertSent = false;
                log.debug("Vehicle {} became idle at {}", vehicleId, firstIdleTime);
            }
            // If already idle with valid firstIdleTime, do nothing - wait for threshold
        } else {
            // Vehicle is moving - reset state
            if (isCurrentlyIdle) {
                reset();
                log.debug("Vehicle {} resumed movement", vehicleId);
            }
        }
    }

    public boolean shouldTriggerAlert(Duration idleThreshold) {
        if (!isCurrentlyIdle || alertSent || firstIdleTime == null) {
            return false;
        }

        Duration idleDuration = Duration.between(firstIdleTime, Instant.now());
        boolean shouldAlert = idleDuration.compareTo(idleThreshold) >= 0;

        if (shouldAlert) {
            log.debug("Vehicle {} idle for {} (threshold: {}) - triggering alert",
                    vehicleId, idleDuration, idleThreshold);
        }

        return shouldAlert;
    }

    public void markAlertSent() {
        this.alertSent = true;
        log.debug("Alert marked as sent for vehicle {}", vehicleId);
    }

    public void reset() {
        this.firstIdleTime = null;
        this.isCurrentlyIdle = false;
        this.alertSent = false;
        log.debug("Idle state reset for vehicle {}", vehicleId);
    }
}