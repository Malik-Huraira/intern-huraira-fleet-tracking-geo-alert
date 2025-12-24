package com.geofleet.tracking.controller;

import com.geofleet.tracking.model.dto.VehicleStatusDTO;
import com.geofleet.tracking.model.entity.VehicleAlert;
import com.geofleet.tracking.model.entity.VehicleReading;
import com.geofleet.tracking.model.entity.VehicleStatus;
import com.geofleet.tracking.repository.VehicleAlertRepository;
import com.geofleet.tracking.repository.VehicleReadingRepository;
import com.geofleet.tracking.repository.VehicleStatusRepository;
import com.geofleet.tracking.service.VehicleStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/vehicles")
@RequiredArgsConstructor
public class VehicleQueryController {

    private final VehicleStatusService vehicleStatusService;
    private final VehicleStatusRepository vehicleStatusRepository;
    private final VehicleReadingRepository vehicleReadingRepository;
    private final VehicleAlertRepository vehicleAlertRepository;

    /**
     * Get the latest GPS reading for a specific vehicle
     */
    @GetMapping("/{vehicleId}/latest")
    public ResponseEntity<VehicleReading> getLatestReading(@PathVariable String vehicleId) {
        return vehicleReadingRepository.findLatestByVehicleId(vehicleId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all vehicle statuses (used by frontend for initial load)
     */
    @GetMapping("/status/all")
    public ResponseEntity<List<VehicleStatusDTO>> getAllVehicleStatuses() {
        List<VehicleStatusDTO> statuses = vehicleStatusService.getAllVehicleStatuses();
        return ResponseEntity.ok(statuses);
    }

    /**
     * Get status for a single vehicle
     */
    @GetMapping("/status/{vehicleId}")
    public ResponseEntity<VehicleStatusDTO> getVehicleStatus(@PathVariable String vehicleId) {
        VehicleStatusDTO status = vehicleStatusService.getVehicleStatus(vehicleId);
        return status != null ? ResponseEntity.ok(status) : ResponseEntity.notFound().build();
    }

    /**
     * Get recent GPS history for a vehicle (for trail or details view)
     */
    @GetMapping("/{vehicleId}/history")
    public ResponseEntity<List<VehicleReading>> getVehicleHistory(
            @PathVariable String vehicleId,
            @RequestParam(defaultValue = "50") int limit) {

        List<VehicleReading> readings = vehicleReadingRepository
                .findByVehicleIdOrderByEventTimestampDesc(vehicleId);

        if (readings.size() > limit) {
            readings = readings.subList(0, limit);
        }

        return ResponseEntity.ok(readings);
    }

    /**
     * Get all alerts for a specific vehicle
     */
    @GetMapping("/{vehicleId}/alerts")
    public ResponseEntity<List<VehicleAlert>> getVehicleAlerts(@PathVariable String vehicleId) {
        List<VehicleAlert> alerts = vehicleAlertRepository
                .findByVehicleIdOrderByDetectedAtDesc(vehicleId);
        return ResponseEntity.ok(alerts);
    }

    /**
     * Get the 20 most recent alerts (for dashboard panel)
     */
    @GetMapping("/recent-alerts")
    public ResponseEntity<List<VehicleAlert>> getRecentAlerts() {
        List<VehicleAlert> alerts = vehicleAlertRepository.findTop20ByOrderByDetectedAtDesc();
        return ResponseEntity.ok(alerts);
    }

    /**
     * Fleet statistics for dashboard header
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        List<VehicleStatus> allStatuses = vehicleStatusRepository.findAllByOrderByLastSeenDesc();

        long totalVehicles = allStatuses.size();
        long onlineVehicles = allStatuses.stream()
                .filter(vs -> vs.getLastSeen() != null &&
                        vs.getLastSeen().isAfter(LocalDateTime.now().minusMinutes(1)))
                .count();

        double avgSpeed = allStatuses.stream()
                .filter(vs -> vs.getLastSpeed() != null)
                .mapToDouble(VehicleStatus::getLastSpeed)
                .average()
                .orElse(0.0);

        long alertsLastHour = vehicleAlertRepository.countByDetectedAtAfter(
                LocalDateTime.now().minusHours(1));

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalVehicles", totalVehicles);
        stats.put("onlineVehicles", onlineVehicles);
        stats.put("averageSpeed", Math.round(avgSpeed * 100.0) / 100.0);
        stats.put("alertsLastHour", alertsLastHour);
        stats.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(stats);
    }
}