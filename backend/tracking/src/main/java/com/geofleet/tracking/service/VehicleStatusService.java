package com.geofleet.tracking.service;

import com.geofleet.tracking.model.dto.VehicleEventDTO;
import com.geofleet.tracking.model.dto.VehicleStatusDTO;
import com.geofleet.tracking.model.entity.VehicleStatus;
import java.util.List;

public interface VehicleStatusService {
    void updateVehicleStatus(VehicleEventDTO event);

    List<VehicleStatusDTO> getAllVehicleStatuses();

    VehicleStatusDTO getVehicleStatus(String vehicleId);

    void categorizeVehicleStatuses();

    String determineVehicleStatus(VehicleStatus vehicleStatus);
}