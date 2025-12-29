package com.geofleet.tracking.kafka.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geofleet.tracking.model.dto.AlertEventDTO;
import com.geofleet.tracking.model.dto.VehicleEventDTO;
import com.geofleet.tracking.model.entity.VehicleAlert;
import com.geofleet.tracking.model.enums.AlertType;
import com.geofleet.tracking.repository.GeoFenceRepository;
import com.geofleet.tracking.repository.VehicleAlertRepository;
import com.geofleet.tracking.sse.AlertSsePublisher;
import com.geofleet.tracking.util.GeometryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class VehicleEventStreamProcessor {

    private final ObjectMapper objectMapper;
    private final GeoFenceRepository geoFenceRepository;
    private final VehicleAlertRepository vehicleAlertRepository;
    private final AlertSsePublisher alertSsePublisher;
    private final GeometryUtil geometryUtil;

    @Value("${speeding.threshold.kph:80}")
    private double speedingThresholdKph;

    @Value("${idle.threshold.minutes:10}")
    private long idleThresholdMinutes;

    private static final String VEHICLE_GPS_TOPIC = "vehicle-gps";
    private static final String VEHICLE_ALERTS_TOPIC = "vehicle-alerts";
    private static final String GEOFENCE_STATE_STORE = "geofence-state-store";
    private static final String IDLE_STATE_STORE = "idle-state-store";

    @Bean
    public KStream<String, String> kStream(StreamsBuilder streamsBuilder) {
        log.error("🔥🔥🔥 KAFKA STREAMS TOPOLOGY CREATED 🔥🔥🔥");
        log.info("🚀 Initializing Kafka Streams topology...");

        // State store for geofence tracking
        StoreBuilder<KeyValueStore<String, String>> geofenceStoreBuilder = Stores
                .keyValueStoreBuilder(
                        Stores.persistentKeyValueStore(GEOFENCE_STATE_STORE),
                        Serdes.String(),
                        Serdes.String());
        streamsBuilder.addStateStore(geofenceStoreBuilder);
        log.info("✅ Added geofence state store: {}", GEOFENCE_STATE_STORE);

        // State store for idle detection - NEW FIXED VERSION
        StoreBuilder<KeyValueStore<String, IdleState>> idleStoreBuilder = Stores
                .keyValueStoreBuilder(
                        Stores.persistentKeyValueStore(IDLE_STATE_STORE),
                        Serdes.String(),
                        new IdleStateSerde(objectMapper));
        streamsBuilder.addStateStore(idleStoreBuilder);
        log.info("✅ Added idle detection state store: {}", IDLE_STATE_STORE);

        // State store for statistics (optional, keep if needed)
        StoreBuilder<KeyValueStore<String, VehicleStatsAggregate>> statsStoreBuilder = Stores
                .keyValueStoreBuilder(
                        Stores.persistentKeyValueStore("stats-state-store"),
                        Serdes.String(),
                        new VehicleStatsAggregateSerde(objectMapper));
        streamsBuilder.addStateStore(statsStoreBuilder);
        log.info("✅ Added statistics state store");

        KStream<String, String> sourceStream = streamsBuilder
                .stream(VEHICLE_GPS_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        log.info("✅ Created source stream from topic: {}", VEHICLE_GPS_TOPIC);

        // Process alerts - FIXED IDLE DETECTION
        processSpeedingAlerts(sourceStream);
        processIdleAlerts(sourceStream); // UPDATED
        processGeofenceAlerts(sourceStream);

        log.info("✅ Kafka Streams topology built: speeding > {} kph, idle > {} minutes",
                speedingThresholdKph, idleThresholdMinutes);

        // Return the stream (Spring will handle starting Kafka Streams)
        return sourceStream;
    }
    
    @Bean
    public Duration idleThreshold(@Value("${idle.threshold.minutes:10}") long minutes) {
        return Duration.ofMinutes(minutes);
    }

    private void processSpeedingAlerts(KStream<String, String> stream) {
        log.info("⚡ Configuring speeding alert processing...");

        KStream<String, String> speedingAlerts = stream
                .mapValues(this::parseVehicleEvent)
                .peek((key, event) -> {
                    if (event != null) {
                        log.debug("Speed check for {}: {} kph (threshold: {})",
                                event.getVehicleId(), event.getSpeedKph(), speedingThresholdKph);
                        if (event.getSpeedKph() > speedingThresholdKph) {
                            log.info("🚨 SPEEDING DETECTED: {} at {} kph",
                                    event.getVehicleId(), event.getSpeedKph());
                        }
                    }
                })
                .filter((key, event) -> event != null && event.getSpeedKph() > speedingThresholdKph)
                .mapValues(this::createSpeedingAlert)
                .peek((key, alertJson) -> {
                    log.info("🚨 SPEEDING ALERT PRODUCED: {}", alertJson);
                    saveAndPublishAlert(alertJson);
                });

        // Send to alerts topic
        speedingAlerts.to(VEHICLE_ALERTS_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

        log.info("✅ Speeding alert stream configured (> {} kph)", speedingThresholdKph);
    }

    private void processIdleAlerts(KStream<String, String> stream) {
        log.info("⚡ Configuring idle alert processing (threshold: {} minutes)...", idleThresholdMinutes);

        KStream<String, String> idleAlerts = stream
                .mapValues(this::parseVehicleEvent)
                .filter((key, event) -> event != null)
                .transform(() -> new IdleTransformer(Duration.ofMinutes(idleThresholdMinutes), objectMapper),
                        IDLE_STATE_STORE)
                .filter((key, alertJson) -> alertJson != null && !alertJson.isEmpty())
                .peek((key, alertJson) -> {
                    log.info("🚨 IDLE ALERT PRODUCED for key {}: {}", key, alertJson);
                    saveAndPublishAlert(alertJson);
                });

        // Send to alerts topic
        idleAlerts.to(VEHICLE_ALERTS_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

        log.info("✅ Idle alert stream configured (> {} minutes of being stationary)", idleThresholdMinutes);
    }

    private void processGeofenceAlerts(KStream<String, String> stream) {
        log.info("⚡ Configuring geofence alert processing...");

        KStream<String, String> geofenceAlerts = stream
                .mapValues(this::parseVehicleEvent)
                .filter((key, event) -> event != null)
                .transform(() -> new GeofenceTransformer(geoFenceRepository, objectMapper),
                        GEOFENCE_STATE_STORE)
                .filter((key, alertJson) -> alertJson != null && !alertJson.isEmpty())
                .peek((key, alertJson) -> {
                    log.info("🚨 GEOFENCE ALERT PRODUCED for key {}: {}", key, alertJson);
                    saveAndPublishAlert(alertJson);
                });

        // Send to alerts topic
        geofenceAlerts.to(VEHICLE_ALERTS_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

        log.info("✅ Geofence alert stream configured");
    }

    /**
     * Save alert to database and publish via SSE
     */
    private void saveAndPublishAlert(String alertJson) {
        try {
            AlertEventDTO alertEvent = objectMapper.readValue(alertJson, AlertEventDTO.class);
            
            // Save to database
            VehicleAlert alert = new VehicleAlert();
            alert.setVehicleId(alertEvent.getVehicleId());
            alert.setAlertType(alertEvent.getAlertType());
            alert.setDetails(objectMapper.writeValueAsString(alertEvent.getDetails()));
            alert.setDetectedAt(alertEvent.getTimestamp());
            alert.setLat(alertEvent.getLat());
            alert.setLng(alertEvent.getLng());
            
            if (alertEvent.getLat() != null && alertEvent.getLng() != null) {
                alert.setGeom(geometryUtil.createPoint(alertEvent.getLng(), alertEvent.getLat()));
            }
            
            vehicleAlertRepository.save(alert);
            log.info("💾 Saved alert to DB: {} for {}", alertEvent.getAlertType(), alertEvent.getVehicleId());
            
            // Publish via SSE
            alertSsePublisher.publish(alertEvent);
            log.info("📡 Published alert via SSE: {}", alertEvent.getAlertType());
            
        } catch (Exception e) {
            log.error("❌ Failed to save/publish alert: {}", e.getMessage(), e);
        }
    }

    private VehicleEventDTO parseVehicleEvent(String json) {
        try {
            return objectMapper.readValue(json, VehicleEventDTO.class);
        } catch (Exception e) {
            log.error("Failed to parse vehicle event: {}", e.getMessage());
            return null;
        }
    }

    private String createSpeedingAlert(VehicleEventDTO event) {
        return createAlert(event, AlertType.SPEEDING, Map.of(
                "speedKph", event.getSpeedKph(),
                "threshold", speedingThresholdKph,
                "excess", event.getSpeedKph() - speedingThresholdKph));
    }

    private String createAlert(VehicleEventDTO event, AlertType type, Map<String, Object> details) {
        try {
            AlertEventDTO alert = new AlertEventDTO();
            alert.setVehicleId(event.getVehicleId());
            alert.setAlertType(type);
            alert.setDetails(details);
            alert.setTimestamp(LocalDateTime.now());
            alert.setLat(event.getLat());
            alert.setLng(event.getLng());
            return objectMapper.writeValueAsString(alert);
        } catch (Exception e) {
            log.error("Failed to create {} alert: {}", type, e.getMessage());
            return "{}";
        }
    }
}