
-- 1. Enable Spatial Extensions
CREATE EXTENSION IF NOT EXISTS postgis;

-- 2. Raw Readings Table (with Spatial Support)
CREATE TABLE IF NOT EXISTS vehicle_readings (
    id BIGSERIAL PRIMARY KEY,
    vehicle_id VARCHAR(50) NOT NULL,
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    location GEOGRAPHY(Point, 4326), -- Added from spatial schema
    speed_kph DOUBLE PRECISION,
    heading DOUBLE PRECISION,
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes for vehicle_readings
CREATE INDEX IF NOT EXISTS idx_vehicle_readings_vehicle_ts 
    ON vehicle_readings(vehicle_id, event_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_vehicle_readings_location 
    ON vehicle_readings USING GIST (location);

-- 3. Alerts Table (with Spatial Support)
CREATE TABLE IF NOT EXISTS vehicle_alerts (
    id BIGSERIAL PRIMARY KEY,
    vehicle_id VARCHAR(50) NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    details JSONB,
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    geom GEOGRAPHY(Point, 4326) -- Added from spatial schema
);

-- Indexes for vehicle_alerts
CREATE INDEX IF NOT EXISTS idx_vehicle_alerts_vehicle_detected 
    ON vehicle_alerts(vehicle_id, detected_at);
CREATE INDEX IF NOT EXISTS idx_vehicle_alerts_geom 
    ON vehicle_alerts USING GIST (geom);

-- 4. Geofences Table (with Spatial Support)
CREATE TABLE IF NOT EXISTS geofences (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    polygon_geojson JSONB,
    polygon_geom GEOMETRY(Polygon, 4326), -- Added from spatial schema
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes for geofences
CREATE INDEX IF NOT EXISTS idx_geofences_polygon_geom 
    ON geofences USING GIST (polygon_geom);

-- 5. Vehicle Status Cache Table
CREATE TABLE IF NOT EXISTS vehicle_status_cache (
    vehicle_id VARCHAR(50) PRIMARY KEY,
    last_lat DOUBLE PRECISION,
    last_lng DOUBLE PRECISION,
    last_speed DOUBLE PRECISION,
    last_seen TIMESTAMP WITH TIME ZONE NOT NULL
);