-- Additional indexes for performance
CREATE INDEX IF NOT EXISTS idx_vehicle_alerts_detected_at 
ON vehicle_alerts(detected_at DESC);

CREATE INDEX IF NOT EXISTS idx_vehicle_readings_created_at 
ON vehicle_readings(created_at DESC);