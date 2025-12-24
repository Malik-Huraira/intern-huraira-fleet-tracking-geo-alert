-- Add sample geofences (optional)
INSERT INTO geofences (name, polygon_geojson, polygon_geom) VALUES
('Warehouse A', '{"type": "Feature", "geometry": {"type": "Polygon", "coordinates": [[[67.01, 24.89], [67.02, 24.89], [67.02, 24.90], [67.01, 24.90], [67.01, 24.89]]]}}',
 ST_GeomFromGeoJSON('{"type": "Polygon", "coordinates": [[[67.01, 24.89], [67.02, 24.89], [67.02, 24.90], [67.01, 24.90], [67.01, 24.89]]]}')),
('Delivery Zone', '{"type": "Feature", "geometry": {"type": "Polygon", "coordinates": [[[67.03, 24.91], [67.04, 24.91], [67.04, 24.92], [67.03, 24.92], [67.03, 24.91]]]}}',
 ST_GeomFromGeoJSON('{"type": "Polygon", "coordinates": [[[67.03, 24.91], [67.04, 24.91], [67.04, 24.92], [67.03, 24.92], [67.03, 24.91]]]}'))
ON CONFLICT DO NOTHING;

