-- Partial index for high-speed expiration lookup
CREATE INDEX idx_bookings_status_expires_at ON bookings (expires_at) WHERE status = 'PENDING';
