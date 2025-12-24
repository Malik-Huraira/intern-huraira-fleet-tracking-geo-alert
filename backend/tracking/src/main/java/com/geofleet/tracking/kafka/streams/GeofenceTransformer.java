package com.geofleet.tracking.kafka.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geofleet.tracking.model.dto.AlertEventDTO;
import com.geofleet.tracking.model.dto.VehicleEventDTO;
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
import java.util.Map;

@Slf4j
@Component
public class GeofenceTransformer implements Transformer<String, VehicleEventDTO, KeyValue<String, String>> {

    private final GeoFenceRepository geoFenceRepository;
    private final ObjectMapper objectMapper;

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
            // Find current geofence (take first one if multiple overlap)
            String currentGeofence = geoFenceRepository
                    .findGeofencesContainingPoint(event.getLat(), event.getLng())
                    .stream()
                    .findFirst()
                    .map(geoFence -> geoFence.getName())
                    .orElse(null);

            String previousGeofence = stateStore.get(vehicleId);

            // Always update state if currently inside a geofence
            if (currentGeofence != null) {
                stateStore.put(vehicleId, currentGeofence);
            } else {
                stateStore.delete(vehicleId); // Clear if outside all
            }

            // Detect ENTRY
            if (currentGeofence != null && (previousGeofence == null || !previousGeofence.equals(currentGeofence))) {
                log.info("GEOFENCE_ENTRY: Vehicle {} entered '{}' at ({}, {})",
                        vehicleId, currentGeofence, event.getLat(), event.getLng());
                return KeyValue.pair(vehicleId, createGeofenceAlert(event, AlertType.GEOFENCE_ENTER, currentGeofence));
            }

            // Detect EXIT
            if (currentGeofence == null && previousGeofence != null) {
                log.info("GEOFENCE_EXIT: Vehicle {} exited '{}' at ({}, {})",
                        vehicleId, previousGeofence, event.getLat(), event.getLng());
                return KeyValue.pair(vehicleId, createGeofenceAlert(event, AlertType.GEOFENCE_EXIT, previousGeofence));
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

    private String createGeofenceAlert(VehicleEventDTO event, AlertType alertType, String geofenceName) {
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("geofence", geofenceName);
            details.put("lat", event.getLat());
            details.put("lng", event.getLng());
            details.put("action", alertType == AlertType.GEOFENCE_ENTER ? "entered" : "exited");

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