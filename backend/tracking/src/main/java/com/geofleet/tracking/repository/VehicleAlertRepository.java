package com.geofleet.tracking.repository;

import com.geofleet.tracking.model.entity.VehicleAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VehicleAlertRepository extends JpaRepository<VehicleAlert, Long> {

    /**
     * Get all alerts for a specific vehicle, most recent first
     * Used for vehicle details/history view
     */
    List<VehicleAlert> findByVehicleIdOrderByDetectedAtDesc(String vehicleId);

    /**
     * Get the 20 most recent alerts across all vehicles
     * Used for "Recent Alerts" panel in dashboard
     */
    List<VehicleAlert> findTop20ByOrderByDetectedAtDesc();

    /**
     * Get recent alerts within a time window (e.g., last hour for stats)
     */
    @Query("SELECT va FROM VehicleAlert va WHERE va.detectedAt >= :since ORDER BY va.detectedAt DESC")
    List<VehicleAlert> findByDetectedAtAfterOrderByDetectedAtDesc(@Param("since") LocalDateTime since);

    /**
     * Count alerts in the last hour (for dashboard stats)
     */
    @Query("SELECT COUNT(va) FROM VehicleAlert va WHERE va.detectedAt >= :since")
    long countByDetectedAtAfter(@Param("since") LocalDateTime since);

    /**
     * Optional: Get alerts by type (SPEEDING, GEOFENCE, IDLE)
     */
    List<VehicleAlert> findByAlertTypeOrderByDetectedAtDesc(String alertType);
}