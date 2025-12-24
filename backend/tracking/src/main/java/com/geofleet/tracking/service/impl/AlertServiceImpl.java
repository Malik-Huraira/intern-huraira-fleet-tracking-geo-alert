package com.geofleet.tracking.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geofleet.tracking.model.dto.AlertEventDTO;
import com.geofleet.tracking.model.entity.VehicleAlert;
import com.geofleet.tracking.repository.VehicleAlertRepository;
import com.geofleet.tracking.service.AlertService;
import com.geofleet.tracking.sse.AlertSsePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final VehicleAlertRepository vehicleAlertRepository;
    private final AlertSsePublisher alertSsePublisher;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void createAlert(AlertEventDTO alertEvent) {
        try {
            saveAlert(alertEvent);
            alertSsePublisher.publish(alertEvent);

            log.info("Alert created: {} for vehicle {}",
                    alertEvent.getAlertType(), alertEvent.getVehicleId());
        } catch (Exception e) {
            log.error("Error creating alert: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public VehicleAlert saveAlert(AlertEventDTO alertEvent) {
        VehicleAlert alert = new VehicleAlert();
        alert.setVehicleId(alertEvent.getVehicleId());
        alert.setAlertType(alertEvent.getAlertType());
        try {
            alert.setDetails(objectMapper.writeValueAsString(alertEvent.getDetails()));
        } catch (JsonProcessingException e) {
            log.error("Error serializing alert details: {}", e.getMessage(), e);
            e.printStackTrace();
        }
        alert.setDetectedAt(alertEvent.getTimestamp());
        alert.setLat(alertEvent.getLat());
        alert.setLng(alertEvent.getLng());

        return vehicleAlertRepository.save(alert);
    }
}