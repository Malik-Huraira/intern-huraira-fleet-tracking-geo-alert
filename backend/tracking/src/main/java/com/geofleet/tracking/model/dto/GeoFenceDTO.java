package com.geofleet.tracking.model.dto;

import lombok.Data;
import java.util.List;
@Data
public class GeoFenceDTO {
    private Long id;
    private String name;
    private String polygonGeojson;
    private String polygonGeom; 
    private List<List<double[]>> coordinates;
}