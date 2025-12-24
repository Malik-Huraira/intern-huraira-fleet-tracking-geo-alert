package com.geofleet.tracking.service.impl;

import com.geofleet.tracking.model.dto.VehicleEventDTO;
import com.geofleet.tracking.model.dto.VehicleStatusDTO;
import com.geofleet.tracking.model.entity.VehicleStatus;
import com.geofleet.tracking.repository.VehicleStatusRepository;
import com.geofleet.tracking.service.VehicleStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleStatusServiceImpl implements VehicleStatusService {

    private final VehicleStatusRepository vehicleStatusRepository;

    @Value("${vehicle.status.online.threshold.minutes:1}")
    private int onlineThresholdMinutes;

    @Value("${vehicle.status.idle.threshold.minutes:10}")
    private int idleThresholdMinutes;

    @Value("${vehicle.status.offline.threshold.minutes:30}")
    private int offlineThresholdMinutes;

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

        status.setStatus(determineVehicleStatus(status));
        vehicleStatusRepository.save(status);

        log.debug("Updated status for vehicle {}: {}", event.getVehicleId(), status.getStatus());
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

        if (minutesSinceLastSeen <= onlineThresholdMinutes) {
            return "ONLINE";
        } else if (minutesSinceLastSeen <= idleThresholdMinutes) {
            return "IDLE";
        } else {
            return "OFFLINE";
        }
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