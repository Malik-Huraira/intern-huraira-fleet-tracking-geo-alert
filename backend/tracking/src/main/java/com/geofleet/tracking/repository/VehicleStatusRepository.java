package com.geofleet.tracking.repository;

import com.geofleet.tracking.model.entity.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleStatusRepository extends JpaRepository<VehicleStatus, String> {

    /**
     * Get all vehicle statuses ordered by last seen (most recent first)
     * Used for dashboard stats and vehicle list
     */
    List<VehicleStatus> findAllByOrderByLastSeenDesc();

    /**
     * Find status by vehicleId (primary key is vehicleId)
     */
    Optional<VehicleStatus> findById(String vehicleId);

    /**
     * Optional: Get all online vehicles (for quick stats)
     */
    @Query("SELECT vs FROM VehicleStatus vs WHERE vs.status = 'ONLINE'")
    List<VehicleStatus> findAllOnline();

    /**
     * Optional: Get all idle vehicles
     */
    @Query("SELECT vs FROM VehicleStatus vs WHERE vs.status = 'IDLE'")
    List<VehicleStatus> findAllIdle();
}