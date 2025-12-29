-- V5: Enhanced Geofencing Indexes and Optimizations
-- ✅ FIX: Ensure all spatial indexes are properly created

-- 1. Ensure vehicle_alerts geom column has proper spatial index
DROP INDEX IF EXISTS idx_vehicle_alerts_geom;
CREATE INDEX IF NOT EXISTS idx_vehicle_alerts_geom 
    ON vehicle_alerts USING GIST (geom);

-- 2. Add composite index for alert queries by type and time
CREATE INDEX IF NOT EXISTS idx_vehicle_alerts_type_time 
    ON vehicle_alerts(alert_type, detected_at DESC);

-- 3. Add composite index for vehicle-specific alert queries
CREATE INDEX IF NOT EXISTS idx_vehicle_alerts_vehicle_type_time 
    ON vehicle_alerts(vehicle_id, alert_type, detected_at DESC);

-- 4. Ensure geofences polygon_geom index is optimal
DROP INDEX IF EXISTS idx_geofences_polygon_geom;
CREATE INDEX IF NOT EXISTS idx_geofences_polygon_geom 
    ON geofences USING GIST (polygon_geom);

-- 5. Add index for geofence name lookups
CREATE INDEX IF NOT EXISTS idx_geofences_name 
    ON geofences(name);

-- 6. Add statistics update for better query planning
ANALYZE geofences;
ANALYZE vehicle_alerts;

-- 7. Add comments for documentation
COMMENT ON INDEX idx_vehicle_alerts_geom IS 'Spatial index for alert location queries';
COMMENT ON INDEX idx_vehicle_alerts_type_time IS 'Composite index for alert type and time queries';
COMMENT ON INDEX idx_geofences_polygon_geom IS 'Spatial index for geofence polygon queries';
COMMENT ON INDEX idx_geofences_name IS 'Index for geofence name lookups';