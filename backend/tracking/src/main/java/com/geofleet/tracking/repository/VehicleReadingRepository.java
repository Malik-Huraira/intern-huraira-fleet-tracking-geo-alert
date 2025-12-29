package com.geofleet.tracking.repository;

import com.geofleet.tracking.model.entity.VehicleReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleReadingRepository extends JpaRepository<VehicleReading, Long> {

        /**
         * Get all readings for a vehicle, latest first
         */
        List<VehicleReading> findByVehicleIdOrderByEventTimestampDesc(String vehicleId);

        /**
         * Get the most recent reading for a specific vehicle
         */
        @Query("SELECT vr FROM VehicleReading vr WHERE vr.vehicleId = :vehicleId ORDER BY vr.eventTimestamp DESC LIMIT 1")
        Optional<VehicleReading> findLatestByVehicleId(@Param("vehicleId") String vehicleId);

        /**
         * Find all readings where speed exceeded threshold after a given time
         * Used for speeding reports
         */
        @Query("SELECT vr FROM VehicleReading vr " +
                        "WHERE vr.speedKph > :threshold " +
                        "AND vr.eventTimestamp >= :since " +
                        "ORDER BY vr.eventTimestamp")
        List<VehicleReading> findSpeedingReadings(
                        @Param("threshold") Double threshold,
                        @Param("since") LocalDateTime since);

        /**
         * Find vehicles that have not reported since a given time
         * Used for idle/offline detection in VehicleStatusService
         */
        @Query("SELECT vr.vehicleId " +
                        "FROM VehicleReading vr " +
                        "GROUP BY vr.vehicleId " +
                        "HAVING MAX(vr.eventTimestamp) < :thresholdTime")
        List<String> findVehicleIdsWithLastSeenBefore(@Param("thresholdTime") LocalDateTime thresholdTime);

        /**
         * Optional: Find readings with null location (for backfill)
         */
        List<VehicleReading> findAllByLocationIsNull();

        /**
         * ✅ Historical: Find readings for a vehicle within a date range
         */
        List<VehicleReading> findByVehicleIdAndEventTimestampBetweenOrderByEventTimestampDesc(
                        String vehicleId, LocalDateTime from, LocalDateTime to);

        /**
         * ✅ Historical: Find all readings within a date range
         */
        List<VehicleReading> findByEventTimestampBetweenOrderByEventTimestampDesc(
                        LocalDateTime from, LocalDateTime to);
}