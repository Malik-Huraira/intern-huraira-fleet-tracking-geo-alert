package com.geofleet.tracking.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geofleet.tracking.model.dto.VehicleStatsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VehicleStatsConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "vehicle-stats", groupId = "stats-consumer-group")
    public void consume(String message) {
        try {
            VehicleStatsDTO stats = objectMapper.readValue(message, VehicleStatsDTO.class);
            log.info("Vehicle stats: {} - Avg speed: {} kph",
                    stats.getVehicleId(), stats.getAvgSpeed());
            // Could save to DB or update dashboard cache
        } catch (Exception e) {
            log.error("Error processing stats: {}", e.getMessage());
        }
    }
}