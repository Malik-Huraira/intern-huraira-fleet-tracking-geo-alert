package com.geofleet.tracking.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geofleet.tracking.model.dto.GeoFenceDTO;
import com.geofleet.tracking.model.entity.GeoFence;
import com.geofleet.tracking.model.entity.VehicleReading;
import com.geofleet.tracking.repository.GeoFenceRepository;
import com.geofleet.tracking.repository.VehicleReadingRepository;
import com.geofleet.tracking.service.GeoFenceService;
import com.geofleet.tracking.util.GeometryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoFenceServiceImpl implements GeoFenceService {

    private final GeoFenceRepository geoFenceRepository;
    private final VehicleReadingRepository vehicleReadingRepository;
    private final ObjectMapper objectMapper;
    private final GeometryUtil geometryUtil;

    @Override
    @Transactional
    public GeoFence createGeoFence(GeoFenceDTO geoFenceDTO) {
        GeoFence geoFence = new GeoFence();
        geoFence.setName(geoFenceDTO.getName());
        geoFence.setPolygonGeojson(geoFenceDTO.getPolygonGeojson());

        if (geoFenceDTO.getPolygonGeojson() != null) {
            geoFence.setPolygonGeom(geometryUtil.convertGeoJsonToPolygon(
                    geoFenceDTO.getPolygonGeojson()));
        }

        return geoFenceRepository.save(geoFence);
    }

    @Override
    public List<GeoFenceDTO> getAllGeofencesAsGeoJSON() {
        return geoFenceRepository.findAll().stream()
                .map(this::toGeoJsonDTO)
                .toList();
    }

    private GeoFenceDTO toGeoJsonDTO(GeoFence entity) {
        GeoFenceDTO dto = new GeoFenceDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());

        String wkt = entity.getPolygonGeom().toString();
        String coordsPart = wkt.substring(wkt.indexOf("((") + 2, wkt.lastIndexOf("))"));
        List<double[]> ring = Arrays.stream(coordsPart.split(","))
                .map(p -> {
                    String[] parts = p.trim().split(" ");
                    return new double[] { Double.parseDouble(parts[0]), Double.parseDouble(parts[1]) };
                })
                .toList();

        dto.setCoordinates(List.of(ring));
        return dto;
    }

    @Override
    public List<GeoFence> getAllGeoFences() {
        return geoFenceRepository.findAll();
    }

    @Override
    public void deleteGeoFence(Long id) {
        geoFenceRepository.deleteById(id);
    }

    // NEW: Update vehicle reading with PostGIS point
    @Transactional
    public void updateVehicleReadingWithLocation(VehicleReading reading) {
        if (reading.getLat() != null && reading.getLng() != null && reading.getLocation() == null) {
            Point location = geometryUtil.createPoint(reading.getLat(), reading.getLng());
            reading.setLocation(location);
            vehicleReadingRepository.save(reading);
        }
    }

    // Backfill existing data
    @Scheduled(fixedDelay = 3600000) // Every hour
    @Transactional
    public void backfillLocationData() {
        log.info("Backfilling PostGIS location data...");

        List<VehicleReading> readings = vehicleReadingRepository.findAllByLocationIsNull();
        int updated = 0;

        for (VehicleReading reading : readings) {
            if (reading.getLat() != null && reading.getLng() != null) {
                Point location = geometryUtil.createPoint(reading.getLat(), reading.getLng());
                reading.setLocation(location);
                updated++;
            }
        }

        if (updated > 0) {
            vehicleReadingRepository.saveAll(readings);
            log.info("Backfilled {} vehicle readings with PostGIS locations", updated);
        } else {
            log.debug("No readings needed backfill");
        }
    }

    @Override
    public List<GeoFence> findGeofencesContainingPoint(double lat, double lng) {
        return geoFenceRepository.findGeofencesContainingPoint(lat, lng);
    }
}