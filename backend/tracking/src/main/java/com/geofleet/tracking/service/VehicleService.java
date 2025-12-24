package com.geofleet.tracking.service;

import com.geofleet.tracking.model.dto.VehicleEventDTO;
import com.geofleet.tracking.model.entity.VehicleReading;

public interface VehicleService {
    void processVehicleEvent(VehicleEventDTO event);

    VehicleReading saveVehicleReading(VehicleEventDTO event);
}