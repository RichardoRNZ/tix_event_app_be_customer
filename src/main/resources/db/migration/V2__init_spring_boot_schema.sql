CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    is_admin BOOLEAN DEFAULT FALSE,
    user_token VARCHAR(255),
    blocked BOOLEAN DEFAULT FALSE,
    blocked_time TIMESTAMP,
    verified_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_by VARCHAR(255),
    deleted_at TIMESTAMP
);

CREATE TABLE bookings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    no_booking VARCHAR(255) NOT NULL UNIQUE,
    date TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    user_name VARCHAR(255),
    idempotency_key VARCHAR(255),
    expires_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_by VARCHAR(255),
    deleted_at TIMESTAMP,
    CONSTRAINT fk_bookings_user_id FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE booking_details (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    seat_details_id BIGINT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_by VARCHAR(255),
    deleted_at TIMESTAMP,
    CONSTRAINT fk_booking_details_booking_id FOREIGN KEY (booking_id) REFERENCES bookings (id)
);

-- Partial unique index as requested: booking_details(seat_details_id) WHERE is_active = true
CREATE UNIQUE INDEX idx_booking_details_seat_active ON booking_details(seat_details_id) WHERE is_active = true;

CREATE TABLE booking_outbox (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    event_id BIGINT,
    event_type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_by VARCHAR(255),
    deleted_at TIMESTAMP,
    CONSTRAINT fk_booking_outbox_booking_id FOREIGN KEY (booking_id) REFERENCES bookings (id)
);

CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    payment_method VARCHAR(100),
    payment_status VARCHAR(50) NOT NULL,
    booking_no VARCHAR(255),
    idempotency_key VARCHAR(255),
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_by VARCHAR(255),
    deleted_at TIMESTAMP,
    CONSTRAINT fk_transactions_booking_id FOREIGN KEY (booking_id) REFERENCES bookings (id)
);
