package com.geofleet.tracking.service;

import com.geofleet.tracking.model.dto.GeoFenceDTO;
import com.geofleet.tracking.model.entity.GeoFence;

import java.util.List;

public interface GeoFenceService {

    /**
     * Create a new geofence from DTO (used by admin UI)
     */
    GeoFence createGeoFence(GeoFenceDTO geoFenceDTO);

    /**
     * Get all geofences (raw entities)
     */
    List<GeoFence> getAllGeoFences();

    /**
     * Delete geofence by ID
     */
    void deleteGeoFence(Long id);

    /**
     * Get all geofences as GeoJSON for frontend map rendering
     */
    List<GeoFenceDTO> getAllGeofencesAsGeoJSON();

    /**
     * Optional: Helper for queries or testing
     * Returns geofences containing a point (uses PostGIS ST_Covers)
     */
    List<GeoFence> findGeofencesContainingPoint(double lat, double lng);
}