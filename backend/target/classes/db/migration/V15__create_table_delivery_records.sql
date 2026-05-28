-- V15: Create delivery_records table
-- One record per locked order. Created during OrderFreezeJob. Never deleted.

CREATE TABLE delivery_records (
    id              UUID                   NOT NULL DEFAULT gen_random_uuid(),
    order_id        UUID                   NOT NULL,
    delivery_date   DATE                   NOT NULL,
    delivery_window VARCHAR(50)            NOT NULL DEFAULT 'Morning',
    status          delivery_record_status NOT NULL DEFAULT 'PENDING',
    skip_reason     skip_reason            NULL,
    delivered_at    TIMESTAMPTZ            NULL,
    notes           TEXT                   NULL,
    photo_proof_url TEXT                   NULL,

    CONSTRAINT pk_delivery_records PRIMARY KEY (id),
    CONSTRAINT uq_delivery_records_order_id UNIQUE (order_id),
    CONSTRAINT fk_delivery_records_orders
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    CONSTRAINT chk_delivery_records_delivery_window
        CHECK (delivery_window = 'Morning'),
    CONSTRAINT chk_delivery_records_skip_reason
        CHECK (
            (status = 'SKIPPED' AND skip_reason IS NOT NULL) OR
            (status != 'SKIPPED' AND skip_reason IS NULL)
        ),
    CONSTRAINT chk_delivery_records_delivered_at
        CHECK (
            (status = 'DELIVERED' AND delivered_at IS NOT NULL) OR
            (status != 'DELIVERED' AND delivered_at IS NULL)
        )
);

-- Performance index for delivery sheet queries
CREATE INDEX idx_delivery_records_delivery_date_status
    ON delivery_records (delivery_date, status);
