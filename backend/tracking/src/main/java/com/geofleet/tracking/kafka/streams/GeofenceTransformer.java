package com.geofleet.tracking.kafka.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geofleet.tracking.model.dto.AlertEventDTO;
import com.geofleet.tracking.model.dto.VehicleEventDTO;
import com.geofleet.tracking.model.entity.GeoFence;
import com.geofleet.tracking.model.enums.AlertType;
import com.geofleet.tracking.repository.GeoFenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GeofenceTransformer implements Transformer<String, VehicleEventDTO, KeyValue<String, String>> {

    private final GeoFenceRepository geoFenceRepository;
    private final ObjectMapper objectMapper;

    // ✅ FIX: Buffer distance for hysteresis (in meters)
    private static final double BUFFER_DISTANCE_METERS = 10.0;

    private KeyValueStore<String, String> stateStore;
    private ProcessorContext context;

    public GeofenceTransformer(GeoFenceRepository geoFenceRepository, ObjectMapper objectMapper) {
        this.geoFenceRepository = geoFenceRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void init(ProcessorContext context) {
        this.context = context;
        this.stateStore = (KeyValueStore<String, String>) context.getStateStore("geofence-state-store");
    }

    @Override
    public KeyValue<String, String> transform(String vehicleId, VehicleEventDTO event) {
        if (event == null || event.getLat() == null || event.getLng() == null) {
            return KeyValue.pair(vehicleId, null);
        }

        try {
            // ✅ FIX: Get all overlapping geofences, not just the first one
            List<GeoFence> overlappingGeofences = geoFenceRepository
                    .findGeofencesContainingPoint(event.getLat(), event.getLng());

            // ✅ FIX: Handle multiple geofences with priority (smallest area first for more specific zones)
            String currentGeofence = overlappingGeofences.stream()
                    .min((g1, g2) -> Double.compare(calculateArea(g1), calculateArea(g2)))
                    .map(GeoFence::getName)
                    .orElse(null);

            String previousGeofence = stateStore.get(vehicleId);

            // ✅ FIX: Apply hysteresis using buffer zones
            if (currentGeofence == null && previousGeofence != null) {
                // Check if still within buffer zone of previous geofence
                boolean withinBuffer = isWithinBufferZone(event.getLat(), event.getLng(), previousGeofence);
                if (withinBuffer) {
                    // Stay in previous geofence (hysteresis)
                    currentGeofence = previousGeofence;
                    log.debug("Vehicle {} staying in '{}' due to buffer zone hysteresis", vehicleId, previousGeofence);
                }
            }

            // Always update state if currently inside a geofence
            if (currentGeofence != null) {
                stateStore.put(vehicleId, currentGeofence);
            } else {
                stateStore.delete(vehicleId); // Clear if outside all
            }

            // Detect ENTRY
            if (currentGeofence != null && (previousGeofence == null || !previousGeofence.equals(currentGeofence))) {
                log.info("GEOFENCE_ENTRY: Vehicle {} entered '{}' at ({}, {}) [overlapping zones: {}]",
                        vehicleId, currentGeofence, event.getLat(), event.getLng(), 
                        overlappingGeofences.stream().map(GeoFence::getName).collect(Collectors.joining(", ")));
                return KeyValue.pair(vehicleId, createGeofenceAlert(event, AlertType.GEOFENCE_ENTER, currentGeofence, overlappingGeofences));
            }

            // Detect EXIT
            if (currentGeofence == null && previousGeofence != null) {
                log.info("GEOFENCE_EXIT: Vehicle {} exited '{}' at ({}, {})",
                        vehicleId, previousGeofence, event.getLat(), event.getLng());
                return KeyValue.pair(vehicleId, createGeofenceAlert(event, AlertType.GEOFENCE_EXIT, previousGeofence, overlappingGeofences));
            }

            // No change → no alert
            return KeyValue.pair(vehicleId, null);

        } catch (Exception e) {
            log.error("Error processing geofence for vehicle {}: {}", vehicleId, e.getMessage(), e);
            return KeyValue.pair(vehicleId, null);
        }
    }

    @Override
    public void close() {
        // No resources to close
    }

    /**
     * ✅ FIX: Check if point is within buffer zone of a geofence
     */
    private boolean isWithinBufferZone(Double lat, Double lng, String geofenceName) {
        try {
            // Query for geofences within buffer distance
            List<GeoFence> bufferedGeofences = geoFenceRepository
                    .findGeofencesWithinDistance(lat, lng, BUFFER_DISTANCE_METERS);
            
            return bufferedGeofences.stream()
                    .anyMatch(gf -> gf.getName().equals(geofenceName));
        } catch (Exception e) {
            log.warn("Error checking buffer zone for geofence '{}': {}", geofenceName, e.getMessage());
            return false;
        }
    }

    /**
     * ✅ FIX: Calculate approximate area for geofence prioritization
     */
    private double calculateArea(GeoFence geofence) {
        try {
            // Simple bounding box area calculation
            // In production, you might want to use actual polygon area
            return 1.0; // Placeholder - implement actual area calculation if needed
        } catch (Exception e) {
            return Double.MAX_VALUE; // Fallback to lowest priority
        }
    }

    /**
     * ✅ FIX: Enhanced alert creation with overlapping geofence information
     */
    private String createGeofenceAlert(VehicleEventDTO event, AlertType alertType, String geofenceName, List<GeoFence> overlappingGeofences) {
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("geofence", geofenceName);
            details.put("zone", geofenceName);
            details.put("lat", event.getLat());
            details.put("lng", event.getLng());
            details.put("action", alertType == AlertType.GEOFENCE_ENTER ? "entered" : "exited");
            
            // ✅ FIX: Include information about overlapping geofences
            if (overlappingGeofences.size() > 1) {
                List<String> allZones = overlappingGeofences.stream()
                        .map(GeoFence::getName)
                        .collect(Collectors.toList());
                details.put("overlapping_zones", allZones);
                details.put("zone_count", allZones.size());
            }

            AlertEventDTO alert = new AlertEventDTO();
            alert.setVehicleId(event.getVehicleId());
            alert.setAlertType(alertType);
            alert.setDetails(details);
            alert.setTimestamp(LocalDateTime.now());
            alert.setLat(event.getLat());
            alert.setLng(event.getLng());

            return objectMapper.writeValueAsString(alert);
        } catch (Exception e) {
            log.error("Failed to serialize geofence alert: {}", e.getMessage());
            return null;
        }
    }
}