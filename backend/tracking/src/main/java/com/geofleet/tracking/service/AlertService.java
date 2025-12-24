package com.geofleet.tracking.service;

import com.geofleet.tracking.model.dto.AlertEventDTO;
import com.geofleet.tracking.model.entity.VehicleAlert;

public interface AlertService {
    void createAlert(AlertEventDTO alertEvent);

    VehicleAlert saveAlert(AlertEventDTO alertEvent);
}