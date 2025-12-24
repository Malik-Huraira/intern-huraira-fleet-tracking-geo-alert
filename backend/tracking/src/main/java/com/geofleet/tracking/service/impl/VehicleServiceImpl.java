package com.geofleet.tracking.service.impl;

import com.geofleet.tracking.model.dto.VehicleEventDTO;
import com.geofleet.tracking.model.entity.VehicleReading;
import com.geofleet.tracking.repository.VehicleReadingRepository;
import com.geofleet.tracking.service.VehicleService;
import com.geofleet.tracking.service.VehicleStatusService;
import com.geofleet.tracking.sse.VehicleSsePublisher;
import com.geofleet.tracking.util.GeometryUtil;
import org.locationtech.jts.geom.Point;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleReadingRepository vehicleReadingRepository;
    private final VehicleStatusService vehicleStatusService;
    private final GeometryUtil geometryUtil;
    private final VehicleSsePublisher vehicleSsePublisher;

    @Override
    @Transactional
    public void processVehicleEvent(VehicleEventDTO event) {
        try {
            saveVehicleReading(event);
            vehicleStatusService.updateVehicleStatus(event);
            vehicleSsePublisher.publish(event);

            log.debug("✅ Processed vehicle event: {} | Status: {}",
                    event.getVehicleId(), event.getStatus());
        } catch (Exception e) {
            log.error("❌ Error processing vehicle event for {}: {}",
                    event.getVehicleId(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    public VehicleReading saveVehicleReading(VehicleEventDTO event) {
        VehicleReading reading = new VehicleReading();
        reading.setVehicleId(event.getVehicleId());
        reading.setLat(event.getLat());
        reading.setLng(event.getLng());
        reading.setSpeedKph(event.getSpeedKph());
        reading.setHeading(event.getHeading());
        reading.setEventTimestamp(event.getTimestamp());

        if (event.getLat() != null && event.getLng() != null) {
            Point location = geometryUtil.createPoint(event.getLat(), event.getLng());
            reading.setLocation(location);
        }

        return vehicleReadingRepository.save(reading);
    }
}