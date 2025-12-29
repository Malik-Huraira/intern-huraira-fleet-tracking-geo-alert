package com.geofleet.tracking.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geofleet.tracking.model.dto.AlertEventDTO;
import com.geofleet.tracking.model.entity.VehicleAlert;
import com.geofleet.tracking.repository.VehicleAlertRepository;
import com.geofleet.tracking.sse.AlertSsePublisher;
import com.geofleet.tracking.util.GeometryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertConsumer {

    private final ObjectMapper objectMapper;
    private final VehicleAlertRepository vehicleAlertRepository;
    private final AlertSsePublisher alertSsePublisher;
    private final GeometryUtil geometryUtil;

    @KafkaListener(topics = "vehicle-alerts", groupId = "alert-consumer-group")
    @Transactional
    public void consume(String message, Acknowledgment acknowledgment) {
        try {
            log.info("📨 [ALERT-CONSUMER] Received alert from vehicle-alerts topic: {}", message);
            AlertEventDTO alertEvent = objectMapper.readValue(message, AlertEventDTO.class);
            log.info("📨 [ALERT-CONSUMER] Parsed alert: vehicleId={}, type={}",
                    alertEvent.getVehicleId(), alertEvent.getAlertType());

            VehicleAlert alert = new VehicleAlert();
            alert.setVehicleId(alertEvent.getVehicleId());
            alert.setAlertType(alertEvent.getAlertType());
            alert.setDetails(objectMapper.writeValueAsString(alertEvent.getDetails()));
            alert.setDetectedAt(alertEvent.getTimestamp());
            alert.setLat(alertEvent.getLat());
            alert.setLng(alertEvent.getLng());
            
            // ✅ FIX: Populate geometry column for spatial queries
            if (alertEvent.getLat() != null && alertEvent.getLng() != null) {
                alert.setGeom(geometryUtil.createPoint(alertEvent.getLng(), alertEvent.getLat()));
            }

            vehicleAlertRepository.save(alert);
            log.info("✅ Saved alert: {} for vehicle {} with geometry", alertEvent.getAlertType(), alertEvent.getVehicleId());

            alertSsePublisher.publish(alertEvent);
            log.info("📡 Published alert via SSE: {}", alertEvent.getAlertType());

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("❌ Error processing alert: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process alert", e);
        }
    }
}