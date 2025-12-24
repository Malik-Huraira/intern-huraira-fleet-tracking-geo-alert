package com.geofleet.tracking.controller;

import com.geofleet.tracking.model.dto.AlertEventDTO;
import com.geofleet.tracking.model.entity.VehicleAlert;
import com.geofleet.tracking.model.enums.AlertType;
import com.geofleet.tracking.repository.VehicleAlertRepository;
import com.geofleet.tracking.sse.AlertSsePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
@Transactional
public class TestAlertController {

    private final AlertSsePublisher alertSsePublisher;
    private final VehicleAlertRepository vehicleAlertRepository;

    @PostMapping("/alerts")
    public ResponseEntity<String> publishTestAlert(@RequestBody AlertEventDTO alert) {
        try {
            if (alert.getTimestamp() == null) {
                alert.setTimestamp(LocalDateTime.now());
            }

            log.debug("Received alert: vehicleId={}, alertType={}, details={}",
                    alert.getVehicleId(), alert.getAlertType(), alert.getDetails());

            // Save to database
            VehicleAlert vehicleAlert = new VehicleAlert();
            vehicleAlert.setVehicleId(alert.getVehicleId());
            vehicleAlert.setAlertType(alert.getAlertType() != null ? alert.getAlertType() : AlertType.SPEEDING);
            vehicleAlert.setDetails(convertToJson(alert.getDetails()));
            vehicleAlert.setDetectedAt(alert.getTimestamp());
            vehicleAlert.setLat(alert.getLat() != null ? alert.getLat() : 0.0);
            vehicleAlert.setLng(alert.getLng() != null ? alert.getLng() : 0.0);

            try {
                VehicleAlert saved = vehicleAlertRepository.save(vehicleAlert);
                log.info("✅ Alert saved to database: ID={}, vehicleId={}", saved.getId(), saved.getVehicleId());
            } catch (Exception dbError) {
                log.error("❌ Failed to save alert to database: {}", dbError.getMessage(), dbError);
                throw dbError;
            }

            // Publish to SSE
            alertSsePublisher.publish(alert);
            log.info("📡 Published test alert for vehicle {} via SSE", alert.getVehicleId());
            return ResponseEntity.ok("alert published");
        } catch (Exception e) {
            log.error("❌ Failed to publish test alert: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("failed: " + e.getMessage());
        }
    }

    @PostMapping("/generate-sample-alerts")
    public ResponseEntity<String> generateSampleAlerts() {
        try {
            String[] vehicles = { "TRK-01", "TRK-02", "TRK-03", "TRK-04", "TRK-05" };

            // Create speeding alert
            for (String vehicleId : vehicles) {
                AlertEventDTO speedingAlert = new AlertEventDTO();
                speedingAlert.setVehicleId(vehicleId);
                speedingAlert.setAlertType(AlertType.SPEEDING);
                speedingAlert.setTimestamp(LocalDateTime.now());

                Map<String, Object> details = new HashMap<>();
                details.put("speedKph", 95 + (Math.random() * 30));
                details.put("threshold", 80);
                speedingAlert.setDetails(details);
                speedingAlert.setLat(24.8607 + (Math.random() * 0.1));
                speedingAlert.setLng(67.0011 + (Math.random() * 0.1));

                publishAlert(speedingAlert);
            }

            // Create idle alert
            AlertEventDTO idleAlert = new AlertEventDTO();
            idleAlert.setVehicleId("TRK-02");
            idleAlert.setAlertType(AlertType.IDLE);
            idleAlert.setTimestamp(LocalDateTime.now());
            Map<String, Object> idleDetails = new HashMap<>();
            idleDetails.put("idleMinutes", 15);
            idleAlert.setDetails(idleDetails);
            idleAlert.setLat(24.9056);
            idleAlert.setLng(67.0822);
            publishAlert(idleAlert);

            // Create geofence alert
            AlertEventDTO geofenceAlert = new AlertEventDTO();
            geofenceAlert.setVehicleId("TRK-06");
            geofenceAlert.setAlertType(AlertType.GEOFENCE_ENTER);
            geofenceAlert.setTimestamp(LocalDateTime.now());
            Map<String, Object> geoDetails = new HashMap<>();
            geoDetails.put("geofence", "Warehouse Zone");
            geoDetails.put("action", "entered");
            geofenceAlert.setDetails(geoDetails);
            geofenceAlert.setLat(33.6844);
            geofenceAlert.setLng(73.0479);
            publishAlert(geofenceAlert);

            return ResponseEntity.ok("Generated 7 sample alerts");
        } catch (Exception e) {
            log.error("Failed to generate sample alerts: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("failed");
        }
    }

    private void publishAlert(AlertEventDTO alert) {
        try {
            VehicleAlert vehicleAlert = new VehicleAlert();
            vehicleAlert.setVehicleId(alert.getVehicleId());
            vehicleAlert.setAlertType(alert.getAlertType());
            vehicleAlert.setDetails(convertToJson(alert.getDetails()));
            vehicleAlert.setDetectedAt(alert.getTimestamp());
            vehicleAlert.setLat(alert.getLat());
            vehicleAlert.setLng(alert.getLng());
            vehicleAlertRepository.save(vehicleAlert);

            alertSsePublisher.publish(alert);
        } catch (Exception e) {
            log.error("Error publishing alert: {}", e.getMessage());
        }
    }

    private String convertToJson(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}
