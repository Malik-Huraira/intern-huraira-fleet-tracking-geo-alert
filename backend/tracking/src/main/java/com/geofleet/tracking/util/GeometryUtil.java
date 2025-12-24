package com.geofleet.tracking.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeometryUtil {

    private final ObjectMapper objectMapper;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    // Convert GeoJSON to JTS Polygon
    public Polygon convertGeoJsonToPolygon(String geoJson) {
        try {
            JsonNode root = objectMapper.readTree(geoJson);
            JsonNode coordinates = root.path("geometry").path("coordinates").get(0);

            List<Coordinate> coords = new ArrayList<>();
            for (JsonNode coordNode : coordinates) {
                double lng = coordNode.get(0).asDouble();
                double lat = coordNode.get(1).asDouble();
                coords.add(new Coordinate(lng, lat));
            }

            // Close the polygon
            if (!coords.isEmpty() && !coords.get(0).equals(coords.get(coords.size() - 1))) {
                coords.add(coords.get(0));
            }

            return geometryFactory.createPolygon(coords.toArray(new Coordinate[0]));
        } catch (Exception e) {
            log.error("Error converting GeoJSON to Polygon: {}", e.getMessage());
            throw new RuntimeException("Invalid GeoJSON format", e);
        }
    }

    // Create Point from lat/lng
    public Point createPoint(double lat, double lng) {
        return geometryFactory.createPoint(
                new Coordinate(lng, lat));
    }

}