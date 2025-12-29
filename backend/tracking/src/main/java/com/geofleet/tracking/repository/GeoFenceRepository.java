package com.geofleet.tracking.repository;

import com.geofleet.tracking.model.entity.GeoFence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GeoFenceRepository extends JpaRepository<GeoFence, Long> {

        /**
         * Find all geofences that cover the given point (includes boundary)
         * Uses ST_Covers instead of ST_Contains → no flickering on edges
         */
        @Query(value = """
                        SELECT g.*
                        FROM geofences g
                        WHERE ST_Covers(
                            g.polygon_geom,
                            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)
                        )
                        """, nativeQuery = true)
        List<GeoFence> findGeofencesContainingPoint(@Param("lat") double lat, @Param("lng") double lng);

        /**
         * ✅ FIX: Find geofences within buffer distance for hysteresis
         * Uses ST_DWithin for distance-based queries
         */
        @Query(value = """
                        SELECT g.*
                        FROM geofences g
                        WHERE ST_DWithin(
                            g.polygon_geom::geography,
                            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                            :distanceMeters
                        )
                        """, nativeQuery = true)
        List<GeoFence> findGeofencesWithinDistance(@Param("lat") double lat, @Param("lng") double lng, @Param("distanceMeters") double distanceMeters);

        /**
         * Find the name of the first geofence that covers the point (includes boundary)
         * Used by Kafka Streams (GeofenceTransformer) for entry/exit detection
         */
        @Query(value = """
                        SELECT g.name
                        FROM geofences g
                        WHERE ST_Covers(
                            g.polygon_geom,
                            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)
                        )
                        LIMIT 1
                        """, nativeQuery = true)
        Optional<String> findGeofenceNameByLocation(@Param("lng") Double lng, @Param("lat") Double lat);

        /**
         * Standard findAll — kept for GeoFenceService CRUD
         */
        @Override
        List<GeoFence> findAll();
}