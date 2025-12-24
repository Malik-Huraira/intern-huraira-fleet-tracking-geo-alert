package com.geofleet.tracking.kafka.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geofleet.tracking.model.dto.AlertEventDTO;
import com.geofleet.tracking.model.dto.VehicleEventDTO;
import com.geofleet.tracking.model.enums.AlertType;
import com.geofleet.tracking.repository.GeoFenceRepository;
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

    @Value("${speeding.threshold.kph:80}")
    private double speedingThresholdKph;

    @Value("${idle.threshold.minutes:10}")
    private long idleThresholdMinutes;

    private static final String VEHICLE_GPS_TOPIC = "vehicle-gps";
    private static final String VEHICLE_ALERTS_TOPIC = "vehicle-alerts";
    private static final String GEOFENCE_STATE_STORE = "geofence-state-store";

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

        // State store for idle detection
        StoreBuilder<KeyValueStore<String, VehicleStatsAggregate>> idleStoreBuilder = Stores
                .keyValueStoreBuilder(
                        Stores.persistentKeyValueStore("idle-state-store"),
                        Serdes.String(),
                        new VehicleStatsAggregateSerde(objectMapper));
        streamsBuilder.addStateStore(idleStoreBuilder);
        log.info("✅ Added idle detection state store");

        KStream<String, String> sourceStream = streamsBuilder
                .stream(VEHICLE_GPS_TOPIC, Consumed.with(Serdes.String(), Serdes.String()));

        log.info("✅ Created source stream from topic: {}", VEHICLE_GPS_TOPIC);

        // Process alerts
        processSpeedingAlerts(sourceStream);
        processIdleAlerts(sourceStream);
        processGeofenceAlerts(sourceStream);

        log.info("✅ Kafka Streams topology built: speeding > {} kph, idle > {} minutes",
                speedingThresholdKph, idleThresholdMinutes);

        // Return the stream (Spring will handle starting Kafka Streams)
        return sourceStream;
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
                .peek((key, alertJson) -> log.info("🚨 SPEEDING ALERT PRODUCED: {}", alertJson));

        // Send to alerts topic
        speedingAlerts.to(VEHICLE_ALERTS_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

        log.info("✅ Speeding alert stream configured (> {} kph)", speedingThresholdKph);
    }

    private void processIdleAlerts(KStream<String, String> stream) {
        log.info("⚡ Configuring idle alert processing...");

        Duration windowDuration = Duration.ofMinutes(idleThresholdMinutes);

        KStream<String, String> idleAlerts = stream
                .mapValues(this::parseVehicleEvent)
                .filter((key, event) -> event != null)
                .groupByKey()
                .windowedBy(TimeWindows.ofSizeWithNoGrace(windowDuration))
                .aggregate(
                        VehicleStatsAggregate::new,
                        (key, event, agg) -> {
                            VehicleStatsAggregate updatedAgg = agg.add(event);
                            log.debug("Aggregated for key {}: count={}, avgSpeed={}",
                                    key, updatedAgg.getCount(), updatedAgg.getAvgSpeed());
                            return updatedAgg;
                        },
                        Materialized.with(Serdes.String(), new VehicleStatsAggregateSerde(objectMapper)))
                .toStream()
                .peek((windowedKey, agg) -> log.debug("Windowed aggregate for key {}: {}", windowedKey.key(), agg))
                .filter((windowedKey, agg) -> agg != null
                        && agg.getCount() > 1
                        && agg.getAvgSpeed() < 5.0)
                .peek((windowedKey, agg) -> log.info("🚨 IDLE ALERT TRIGGERED for key {}: avgSpeed={}, count={}",
                        windowedKey.key(), agg.getAvgSpeed(), agg.getCount()))
                .selectKey((windowedKey, agg) -> windowedKey.key())
                .mapValues((key, agg) -> {
                    String alert = createIdleAlert(key, agg.getLastLat(), agg.getLastLng());
                    log.info("🚨 Created idle alert for key {}: {}", key, alert);
                    return alert;
                });

        // Send to alerts topic
        idleAlerts.to(VEHICLE_ALERTS_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

        log.info("✅ Idle alert stream configured (> {} minutes of average speed < 5 kph)",
                idleThresholdMinutes);
    }

    private void processGeofenceAlerts(KStream<String, String> stream) {
        log.info("⚡ Configuring geofence alert processing...");

        KStream<String, String> geofenceAlerts = stream
                .mapValues(this::parseVehicleEvent)
                .filter((key, event) -> event != null)
                .transform(() -> new GeofenceTransformer(geoFenceRepository, objectMapper),
                        GEOFENCE_STATE_STORE)
                .filter((key, alertJson) -> alertJson != null && !alertJson.isEmpty())
                .peek((key, alertJson) -> log.info("🚨 GEOFENCE ALERT PRODUCED for key {}: {}", key, alertJson));

        // Send to alerts topic
        geofenceAlerts.to(VEHICLE_ALERTS_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

        log.info("✅ Geofence alert stream configured");
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

    private String createIdleAlert(String vehicleId, double lastLat, double lastLng) {
        Map<String, Object> details = Map.of(
                "idleMinutes", idleThresholdMinutes,
                "lastLat", lastLat,
                "lastLng", lastLng);
        return createAlert(vehicleId, AlertType.IDLE, details, lastLat, lastLng);
    }

    private String createAlert(VehicleEventDTO event, AlertType type, Map<String, Object> details) {
        return createAlert(event.getVehicleId(), type, details, event.getLat(), event.getLng());
    }

    private String createAlert(String vehicleId, AlertType type, Map<String, Object> details,
            Double lat, Double lng) {
        try {
            AlertEventDTO alert = new AlertEventDTO();
            alert.setVehicleId(vehicleId);
            alert.setAlertType(type);
            alert.setDetails(details);
            alert.setTimestamp(LocalDateTime.now());
            alert.setLat(lat);
            alert.setLng(lng);
            return objectMapper.writeValueAsString(alert);
        } catch (Exception e) {
            log.error("Failed to create {} alert: {}", type, e.getMessage());
            return "{}";
        }
    }
}