package com.geofleet.tracking.controller;

import com.geofleet.tracking.model.dto.GeoFenceDTO;
import com.geofleet.tracking.model.entity.GeoFence;
import com.geofleet.tracking.service.GeoFenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/geofences")
@RequiredArgsConstructor
public class GeoFenceController {

    private final GeoFenceService geoFenceService;

    @GetMapping("/contains")
    public ResponseEntity<List<GeoFence>> getGeofencesContainingPoint(
            @RequestParam double lat,
            @RequestParam double lng) {
        List<GeoFence> containingGeofences = geoFenceService.findGeofencesContainingPoint(lat, lng);
        return ResponseEntity.ok(containingGeofences);
    }

    @GetMapping("/geojson")
    public ResponseEntity<List<GeoFenceDTO>> getAllGeofencesAsGeoJSON() {
        List<GeoFenceDTO> geofences = geoFenceService.getAllGeofencesAsGeoJSON();
        return ResponseEntity.ok(geofences);
    }
    
    @PostMapping
    public ResponseEntity<GeoFence> createGeoFence(@RequestBody GeoFenceDTO geoFenceDTO) {
        try {
            GeoFence created = geoFenceService.createGeoFence(geoFenceDTO);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            log.error("Error creating geofence: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<GeoFence>> getAllGeoFences() {
        List<GeoFence> geofences = geoFenceService.getAllGeoFences();
        return ResponseEntity.ok(geofences);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGeoFence(@PathVariable Long id) {
        try {
            geoFenceService.deleteGeoFence(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting geofence: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}