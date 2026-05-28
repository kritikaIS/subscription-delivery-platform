-- V12: Create subscriptions table
-- Core subscription table. Never hard deleted.

CREATE TABLE subscriptions (
    id           UUID                NOT NULL DEFAULT gen_random_uuid(),
    customer_id  UUID                NOT NULL,
    product_id   UUID                NOT NULL,
    quantity     INTEGER             NOT NULL,
    start_date   DATE                NOT NULL,
    status       subscription_status NOT NULL DEFAULT 'PENDING_START',
    pause_reason pause_reason        NULL,
    created_by   UUID                NOT NULL,
    created_at   TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ         NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_subscriptions PRIMARY KEY (id),
    CONSTRAINT fk_subscriptions_customers
        FOREIGN KEY (customer_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_subscriptions_products
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    CONSTRAINT fk_subscriptions_created_by
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_subscriptions_quantity_positive CHECK (quantity >= 1),
    CONSTRAINT chk_subscriptions_pause_reason
        CHECK (
            (status = 'PAUSED' AND pause_reason IS NOT NULL) OR
            (status != 'PAUSED' AND pause_reason IS NULL)
        )
);

-- Partial unique index: prevents duplicate active/paused/pending subscriptions per customer per product
CREATE UNIQUE INDEX uq_subscriptions_active_per_customer_product
    ON subscriptions (customer_id, product_id)
    WHERE status IN ('ACTIVE', 'PAUSED', 'PENDING_START');
