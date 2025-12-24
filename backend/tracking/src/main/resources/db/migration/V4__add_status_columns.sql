-- Add status columns to vehicle_status_cache table
ALTER TABLE vehicle_status_cache 
ADD COLUMN IF NOT EXISTS status VARCHAR(20),
ADD COLUMN IF NOT EXISTS status_updated_at TIMESTAMP WITH TIME ZONE;

-- Create index for faster status queries
CREATE INDEX IF NOT EXISTS idx_vehicle_status_status 
ON vehicle_status_cache(status);

-- Update existing records with initial status
UPDATE vehicle_status_cache 
SET status = CASE 
    WHEN last_seen >= NOW() - INTERVAL '1 minute' THEN 'ONLINE'
    WHEN last_seen >= NOW() - INTERVAL '5 minutes' THEN 'IDLE'
    ELSE 'OFFLINE'
END,
status_updated_at = NOW()
WHERE status IS NULL;