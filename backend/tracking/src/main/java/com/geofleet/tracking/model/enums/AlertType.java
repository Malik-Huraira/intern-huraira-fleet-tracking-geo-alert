package com.geofleet.tracking.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum AlertType {
    SPEEDING("SPEEDING"),
    GEOFENCE_ENTER("GEOFENCE_ENTER"),
    GEOFENCE_EXIT("GEOFENCE_EXIT"),
    IDLE("IDLE");

    private final String value;

    AlertType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static AlertType fromString(String value) {
        if (value == null) {
            return SPEEDING;
        }
        try {
            return AlertType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SPEEDING;
        }
    }
}