package com.geofleet.tracking.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geofleet.tracking.model.dto.VehicleEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class VehicleGpsProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String TOPIC = "vehicle-gps";

    public void sendVehicleEvent(VehicleEventDTO event) {
        try {
            String payload = objectMapper.writeValueAsString(event);

            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(TOPIC, event.getVehicleId(),
                    payload);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("✅ Sent vehicle event to Kafka: {}", event.getVehicleId());
                } else {
                    log.error("❌ Failed to send vehicle event {}: {}", event.getVehicleId(), ex.getMessage());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("❌ Error serializing vehicle event: {}", e.getMessage());
        }
    }

    // New method to flush pending sends during shutdown
    public void flush() {
        kafkaTemplate.flush();
    }
}