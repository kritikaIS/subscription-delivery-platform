-- V105: Create recharge_request_log table for database-backed rate limiting.
-- Per API spec §6.3: rate-limited to one request per customer per hour.
-- This table tracks only the timestamp of the last request per customer.
-- It does NOT store business data — no wallet mutation, no audit log entry (BR-NOT-02).
-- Rows are upserted on each request; no append-only requirement.

CREATE TABLE recharge_request_log (
    customer_id  UUID        NOT NULL,
    last_requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_recharge_request_log PRIMARY KEY (customer_id),
    CONSTRAINT fk_recharge_request_log_users
        FOREIGN KEY (customer_id) REFERENCES users(id) ON DELETE CASCADE
);
