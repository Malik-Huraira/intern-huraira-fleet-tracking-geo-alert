package com.geofleet.tracking.controller;

import com.geofleet.tracking.model.dto.VehicleStatusDTO;
import com.geofleet.tracking.model.entity.VehicleAlert;
import com.geofleet.tracking.model.entity.VehicleReading;
import com.geofleet.tracking.model.entity.VehicleStatus;
import com.geofleet.tracking.model.enums.AlertType;
import com.geofleet.tracking.repository.VehicleAlertRepository;
import com.geofleet.tracking.repository.VehicleReadingRepository;
import com.geofleet.tracking.repository.VehicleStatusRepository;
import com.geofleet.tracking.service.VehicleStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    // ==================== HISTORICAL DATA ENDPOINTS ====================

    /**
     * Get historical alerts with date range filter
     * Example: /vehicles/alerts/history?from=2025-12-01T00:00:00&to=2025-12-29T23:59:59&type=SPEEDING
     */
    @GetMapping("/alerts/history")
    public ResponseEntity<Map<String, Object>> getHistoricalAlerts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String vehicleId,
            @RequestParam(defaultValue = "100") int limit) {

        log.info("📊 Historical alerts query: from={}, to={}, type={}, vehicleId={}", from, to, type, vehicleId);

        List<VehicleAlert> alerts = vehicleAlertRepository.findByDetectedAtBetweenOrderByDetectedAtDesc(from, to);

        // Apply filters
        if (type != null && !type.isEmpty() && !type.equals("all")) {
            alerts = alerts.stream()
                    .filter(a -> a.getAlertType().name().equals(type))
                    .collect(Collectors.toList());
        }

        if (vehicleId != null && !vehicleId.isEmpty()) {
            alerts = alerts.stream()
                    .filter(a -> a.getVehicleId().equals(vehicleId))
                    .collect(Collectors.toList());
        }

        // Calculate summary stats
        Map<String, Long> alertsByType = alerts.stream()
                .collect(Collectors.groupingBy(a -> a.getAlertType().name(), Collectors.counting()));

        Map<String, Long> alertsByVehicle = alerts.stream()
                .collect(Collectors.groupingBy(VehicleAlert::getVehicleId, Collectors.counting()));

        // Limit results
        if (alerts.size() > limit) {
            alerts = alerts.subList(0, limit);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("alerts", alerts);
        response.put("totalCount", alerts.size());
        response.put("alertsByType", alertsByType);
        response.put("alertsByVehicle", alertsByVehicle);
        response.put("from", from);
        response.put("to", to);

        return ResponseEntity.ok(response);
    }

    /**
     * Get historical vehicle readings with date range filter
     * Example: /vehicles/readings/history?vehicleId=TRK-01&from=2025-12-01T00:00:00&to=2025-12-29T23:59:59
     */
    @GetMapping("/readings/history")
    public ResponseEntity<Map<String, Object>> getHistoricalReadings(
            @RequestParam String vehicleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "500") int limit) {

        log.info("📊 Historical readings query: vehicleId={}, from={}, to={}", vehicleId, from, to);

        List<VehicleReading> readings = vehicleReadingRepository
                .findByVehicleIdAndEventTimestampBetweenOrderByEventTimestampDesc(vehicleId, from, to);

        // Calculate stats
        double avgSpeed = readings.stream()
                .filter(r -> r.getSpeedKph() != null)
                .mapToDouble(VehicleReading::getSpeedKph)
                .average()
                .orElse(0.0);

        double maxSpeed = readings.stream()
                .filter(r -> r.getSpeedKph() != null)
                .mapToDouble(VehicleReading::getSpeedKph)
                .max()
                .orElse(0.0);

        // Limit results
        if (readings.size() > limit) {
            readings = readings.subList(0, limit);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("readings", readings);
        response.put("totalCount", readings.size());
        response.put("averageSpeed", Math.round(avgSpeed * 100.0) / 100.0);
        response.put("maxSpeed", Math.round(maxSpeed * 100.0) / 100.0);
        response.put("vehicleId", vehicleId);
        response.put("from", from);
        response.put("to", to);

        return ResponseEntity.ok(response);
    }

    /**
     * Get alert statistics summary for a date range
     * Example: /vehicles/alerts/summary?from=2025-12-01T00:00:00&to=2025-12-29T23:59:59
     */
    @GetMapping("/alerts/summary")
    public ResponseEntity<Map<String, Object>> getAlertsSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        log.info("📊 Alerts summary query: from={}, to={}", from, to);

        List<VehicleAlert> alerts = vehicleAlertRepository.findByDetectedAtBetweenOrderByDetectedAtDesc(from, to);

        Map<String, Long> byType = alerts.stream()
                .collect(Collectors.groupingBy(a -> a.getAlertType().name(), Collectors.counting()));

        Map<String, Long> byVehicle = alerts.stream()
                .collect(Collectors.groupingBy(VehicleAlert::getVehicleId, Collectors.counting()));

        // Top offenders (vehicles with most alerts)
        List<Map.Entry<String, Long>> topOffenders = byVehicle.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("totalAlerts", alerts.size());
        response.put("alertsByType", byType);
        response.put("alertsByVehicle", byVehicle);
        response.put("topOffenders", topOffenders);
        response.put("from", from);
        response.put("to", to);

        return ResponseEntity.ok(response);
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

    // ==================== DELETE ALERT ENDPOINTS ====================

    /**
     * Delete a single alert by ID
     * Example: DELETE /vehicles/alerts/123
     */
    @DeleteMapping("/alerts/{alertId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteAlert(@PathVariable Long alertId) {
        log.info("🗑️ Deleting alert with ID: {}", alertId);
        
        if (!vehicleAlertRepository.existsById(alertId)) {
            return ResponseEntity.notFound().build();
        }
        
        vehicleAlertRepository.deleteById(alertId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Alert deleted successfully");
        response.put("deletedId", alertId);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Delete multiple alerts by IDs
     * Example: DELETE /vehicles/alerts/batch?ids=1,2,3,4,5
     */
    @DeleteMapping("/alerts/batch")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteAlertsBatch(@RequestParam List<Long> ids) {
        log.info("🗑️ Batch deleting {} alerts", ids.size());
        
        vehicleAlertRepository.deleteAllById(ids);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Alerts deleted successfully");
        response.put("deletedCount", ids.size());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Delete alerts by vehicle ID
     * Example: DELETE /vehicles/alerts/by-vehicle/TRK-01
     */
    @DeleteMapping("/alerts/by-vehicle/{vehicleId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteAlertsByVehicle(@PathVariable String vehicleId) {
        log.info("🗑️ Deleting all alerts for vehicle: {}", vehicleId);
        
        int deletedCount = vehicleAlertRepository.deleteByVehicleId(vehicleId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Alerts deleted successfully");
        response.put("vehicleId", vehicleId);
        response.put("deletedCount", deletedCount);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Delete alerts by type
     * Example: DELETE /vehicles/alerts/by-type/SPEEDING
     */
    @DeleteMapping("/alerts/by-type/{alertType}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteAlertsByType(@PathVariable String alertType) {
        log.info("🗑️ Deleting all alerts of type: {}", alertType);
        
        AlertType type = AlertType.valueOf(alertType.toUpperCase());
        int deletedCount = vehicleAlertRepository.deleteByAlertType(type);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Alerts deleted successfully");
        response.put("alertType", alertType);
        response.put("deletedCount", deletedCount);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Delete alerts by date range
     * Example: DELETE /vehicles/alerts/by-date?from=2025-12-01T00:00:00&to=2025-12-28T23:59:59
     */
    @DeleteMapping("/alerts/by-date")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteAlertsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        
        log.info("🗑️ Deleting alerts from {} to {}", from, to);
        
        int deletedCount = vehicleAlertRepository.deleteByDetectedAtBetween(from, to);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Alerts deleted successfully");
        response.put("from", from);
        response.put("to", to);
        response.put("deletedCount", deletedCount);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Delete alerts with multiple filters (most flexible)
     * Example: DELETE /vehicles/alerts/filtered?from=2025-12-01T00:00:00&to=2025-12-28T23:59:59&type=SPEEDING&vehicleId=TRK-01
     */
    @DeleteMapping("/alerts/filtered")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteAlertsFiltered(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String vehicleId) {
        
        log.info("🗑️ Deleting filtered alerts: from={}, to={}, type={}, vehicleId={}", from, to, type, vehicleId);
        
        int deletedCount;
        
        if (vehicleId != null && !vehicleId.isEmpty()) {
            deletedCount = vehicleAlertRepository.deleteByVehicleIdAndDetectedAtBetween(vehicleId, from, to);
        } else if (type != null && !type.isEmpty() && !type.equals("all")) {
            AlertType alertType = AlertType.valueOf(type.toUpperCase());
            deletedCount = vehicleAlertRepository.deleteByAlertTypeAndDetectedAtBetween(alertType, from, to);
        } else {
            deletedCount = vehicleAlertRepository.deleteByDetectedAtBetween(from, to);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Alerts deleted successfully");
        response.put("from", from);
        response.put("to", to);
        response.put("type", type);
        response.put("vehicleId", vehicleId);
        response.put("deletedCount", deletedCount);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Delete ALL alerts (use with extreme caution!)
     * Example: DELETE /vehicles/alerts/all?confirm=true
     */
    @DeleteMapping("/alerts/all")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteAllAlerts(@RequestParam boolean confirm) {
        if (!confirm) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Please confirm deletion by setting confirm=true");
            return ResponseEntity.badRequest().body(response);
        }
        
        log.warn("⚠️ DELETING ALL ALERTS!");
        
        int deletedCount = vehicleAlertRepository.deleteAllAlerts();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "All alerts deleted successfully");
        response.put("deletedCount", deletedCount);
        
        return ResponseEntity.ok(response);
    }
}