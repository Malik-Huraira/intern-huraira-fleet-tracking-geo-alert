package com.geofleet.tracking.service.impl;

import com.geofleet.tracking.model.dto.AlertEventDTO;
import com.geofleet.tracking.model.dto.VehicleEventDTO;
import com.geofleet.tracking.model.dto.VehicleStatusDTO;
import com.geofleet.tracking.model.entity.VehicleAlert;
import com.geofleet.tracking.model.entity.VehicleStatus;
import com.geofleet.tracking.model.enums.AlertType;
import com.geofleet.tracking.repository.VehicleAlertRepository;
import com.geofleet.tracking.repository.VehicleStatusRepository;
import com.geofleet.tracking.service.VehicleStatusService;
import com.geofleet.tracking.sse.AlertSsePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleStatusServiceImpl implements VehicleStatusService {

    private final VehicleStatusRepository vehicleStatusRepository;
    private final VehicleAlertRepository vehicleAlertRepository;
    private final AlertSsePublisher alertSsePublisher;
    private final ObjectMapper objectMapper;

    // Track when each vehicle became idle and if alert was sent
    private final Map<String, LocalDateTime> idleStartTimes = new ConcurrentHashMap<>();
    private final Map<String, Boolean> idleAlertSent = new ConcurrentHashMap<>();

    @Value("${vehicle.status.online.threshold.minutes:1}")
    private int onlineThresholdMinutes;

    @Value("${vehicle.status.idle.threshold.minutes:10}")
    private int idleThresholdMinutes;

    @Value("${vehicle.status.offline.threshold.minutes:30}")
    private int offlineThresholdMinutes;

    @Value("${idle.threshold.minutes:2}")
    private int idleAlertThresholdMinutes;

    private static final Map<String, String> STATUS_COLORS = Map.of(
            "ONLINE", "green",
            "IDLE", "orange",
            "OFFLINE", "red");

    private static final Map<String, String> STATUS_TEXTS = Map.of(
            "ONLINE", "Online",
            "IDLE", "Idle",
            "OFFLINE", "Offline");

    @Override
    @Transactional
    public void updateVehicleStatus(VehicleEventDTO event) {
        VehicleStatus status = vehicleStatusRepository.findById(event.getVehicleId())
                .orElse(new VehicleStatus());

        status.setVehicleId(event.getVehicleId());
        status.setLastLat(event.getLat());
        status.setLastLng(event.getLng());
        status.setLastSpeed(event.getSpeedKph());
        status.setLastSeen(LocalDateTime.now());

        String newStatus = determineVehicleStatus(status);
        status.setStatus(newStatus);
        vehicleStatusRepository.save(status);

        // Track idle time and generate alerts
        checkAndGenerateIdleAlert(event, newStatus);

        log.debug("Updated status for vehicle {}: {}", event.getVehicleId(), status.getStatus());
    }

    private void checkAndGenerateIdleAlert(VehicleEventDTO event, String status) {
        String vehicleId = event.getVehicleId();
        
        if ("IDLE".equals(status)) {
            // Vehicle is idle - track start time
            if (!idleStartTimes.containsKey(vehicleId)) {
                idleStartTimes.put(vehicleId, LocalDateTime.now());
                idleAlertSent.put(vehicleId, false);
                log.info("🛑 Vehicle {} became IDLE at {}", vehicleId, idleStartTimes.get(vehicleId));
            }
            
            // Check if we should send alert
            LocalDateTime idleStart = idleStartTimes.get(vehicleId);
            long idleMinutes = Duration.between(idleStart, LocalDateTime.now()).toMinutes();
            
            if (idleMinutes >= idleAlertThresholdMinutes && !idleAlertSent.getOrDefault(vehicleId, false)) {
                generateIdleAlert(event, idleMinutes);
                idleAlertSent.put(vehicleId, true);
                log.info("🚨 IDLE ALERT generated for {} after {} minutes", vehicleId, idleMinutes);
            }
        } else {
            // Vehicle is moving - reset idle tracking
            if (idleStartTimes.containsKey(vehicleId)) {
                log.info("🚗 Vehicle {} resumed movement", vehicleId);
                idleStartTimes.remove(vehicleId);
                idleAlertSent.remove(vehicleId);
            }
        }
    }

    private void generateIdleAlert(VehicleEventDTO event, long idleMinutes) {
        try {
            // Create and save alert to database
            VehicleAlert alert = new VehicleAlert();
            alert.setVehicleId(event.getVehicleId());
            alert.setAlertType(AlertType.IDLE);
            alert.setDetectedAt(LocalDateTime.now());
            alert.setLat(event.getLat());
            alert.setLng(event.getLng());
            
            Map<String, Object> details = new HashMap<>();
            details.put("idleMinutes", idleMinutes);
            details.put("location", String.format("%.6f,%.6f", event.getLat(), event.getLng()));
            details.put("reason", "Vehicle stationary for " + idleMinutes + " minutes");
            alert.setDetails(objectMapper.writeValueAsString(details));
            
            vehicleAlertRepository.save(alert);
            log.info("💾 Saved IDLE alert to database for {}", event.getVehicleId());
            
            // Publish via SSE
            AlertEventDTO alertEvent = new AlertEventDTO();
            alertEvent.setVehicleId(event.getVehicleId());
            alertEvent.setAlertType(AlertType.IDLE);
            alertEvent.setDetails(details);
            alertEvent.setTimestamp(LocalDateTime.now());
            alertEvent.setLat(event.getLat());
            alertEvent.setLng(event.getLng());
            
            alertSsePublisher.publish(alertEvent);
            log.info("📡 Published IDLE alert via SSE for {}", event.getVehicleId());
            
        } catch (Exception e) {
            log.error("❌ Failed to generate idle alert: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<VehicleStatusDTO> getAllVehicleStatuses() {
        return vehicleStatusRepository.findAllByOrderByLastSeenDesc().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public VehicleStatusDTO getVehicleStatus(String vehicleId) {
        return vehicleStatusRepository.findById(vehicleId)
                .map(this::convertToDTO)
                .orElse(null);
    }

    @Scheduled(fixedDelay = 60000) // Every minute
    @Transactional
    public void categorizeVehicleStatuses() {
        List<VehicleStatus> statuses = vehicleStatusRepository.findAll();
        int updated = 0;

        for (VehicleStatus status : statuses) {
            String oldStatus = status.getStatus();
            String newStatus = determineVehicleStatus(status);

            if (!newStatus.equals(oldStatus)) {
                status.setStatus(newStatus);
                vehicleStatusRepository.save(status);
                updated++;
                log.info("Status change: {} → {} ({})", status.getVehicleId(), oldStatus, newStatus);
            }
        }

        if (updated > 0)
            log.info("Categorized statuses: {} vehicles updated", updated);
    }

    @Override
    public String determineVehicleStatus(VehicleStatus status) {
        if (status.getLastSeen() == null)
            return "OFFLINE";

        long minutesSinceLastSeen = Duration.between(status.getLastSeen(), LocalDateTime.now()).toMinutes();

        // Check if offline first (no data for too long)
        if (minutesSinceLastSeen > offlineThresholdMinutes) {
            return "OFFLINE";
        }
        
        // Check if idle based on speed (vehicle is stationary)
        if (status.getLastSpeed() != null && status.getLastSpeed() <= 1.0) {
            return "IDLE";
        }
        
        // Check if idle based on time (no recent updates but not offline)
        if (minutesSinceLastSeen > idleThresholdMinutes) {
            return "IDLE";
        }
        
        return "ONLINE";
    }

    private VehicleStatusDTO convertToDTO(VehicleStatus entity) {
        VehicleStatusDTO dto = new VehicleStatusDTO();
        dto.setVehicleId(entity.getVehicleId());
        dto.setLastLat(entity.getLastLat());
        dto.setLastLng(entity.getLastLng());
        dto.setLastSpeed(entity.getLastSpeed());
        dto.setLastSeen(entity.getLastSeen());

        String status = entity.getStatus() != null ? entity.getStatus() : determineVehicleStatus(entity);
        dto.setStatus(status);
        dto.setStatusColor(STATUS_COLORS.getOrDefault(status, "gray"));
        dto.setStatusText(STATUS_TEXTS.getOrDefault(status, "Unknown"));
        dto.setTimeSinceLastUpdate(formatTimeSince(entity.getLastSeen()));

        return dto;
    }

    private String formatTimeSince(LocalDateTime timestamp) {
        if (timestamp == null)
            return "Never";
        Duration d = Duration.between(timestamp, LocalDateTime.now());
        long minutes = d.toMinutes();
        if (minutes < 1)
            return "Just now";
        if (minutes < 60)
            return minutes + " min ago";
        if (minutes < 1440)
            return d.toHours() + " hr ago";
        return d.toDays() + " days ago";
    }
}