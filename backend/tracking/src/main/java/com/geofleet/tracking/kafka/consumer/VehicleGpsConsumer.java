package com.geofleet.tracking.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geofleet.tracking.model.dto.VehicleEventDTO;
import com.geofleet.tracking.service.VehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VehicleGpsConsumer {

    private final ObjectMapper objectMapper;
    private final VehicleService vehicleService;

    @KafkaListener(topics = "vehicle-gps", groupId = "vehicle-consumer-group")
    public void consume(String message,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        try {
            log.info("📨 Received GPS message from vehicle-gps topic for key: {}", key);
            VehicleEventDTO event = objectMapper.readValue(message, VehicleEventDTO.class);

            if (event.getVehicleId() == null || event.getLat() == null || event.getLng() == null) {
                log.error("❌ Invalid vehicle event received: {}", message);
                acknowledgment.acknowledge();
                return;
            }

            log.info("🚗 Processing vehicle event: {} at {},{} speed={}kph",
                    event.getVehicleId(), event.getLat(), event.getLng(), event.getSpeedKph());

            vehicleService.processVehicleEvent(event);
            acknowledgment.acknowledge();

            log.debug("✅ Processed Kafka message for vehicle: {}", event.getVehicleId());
        } catch (Exception e) {
            log.error("❌ Error processing Kafka message: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process message", e);
        }
    }
}