-- V13: Create orders table
-- Daily generated orders from subscriptions. Never hard deleted.

CREATE TABLE orders (
    id                       UUID         NOT NULL DEFAULT gen_random_uuid(),
    customer_id              UUID         NOT NULL,
    subscription_id          UUID         NOT NULL,
    product_id               UUID         NOT NULL,

    -- Immutable address snapshot (copied from customer address at generation time)
    delivery_line1           VARCHAR(255) NOT NULL,
    delivery_line2           VARCHAR(255) NULL,
    delivery_city            VARCHAR(100) NOT NULL,
    delivery_state           VARCHAR(100) NOT NULL,
    delivery_pincode         VARCHAR(10)  NOT NULL,
    delivery_notes           TEXT         NULL,

    delivery_date            DATE         NOT NULL,
    quantity                 INTEGER      NOT NULL,
    unit_price_paise         BIGINT       NOT NULL,
    total_amount_paise       BIGINT       NOT NULL,
    status                   order_status NOT NULL DEFAULT 'SCHEDULED',
    skip_reason              skip_reason  NULL,
    cancellation_comment     TEXT         NULL,
    cancellation_commented_by UUID        NULL,
    cancellation_commented_at TIMESTAMPTZ NULL,
    idempotency_key          VARCHAR(100) NOT NULL,
    notes                    TEXT         NULL,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uq_orders_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_orders_customers
        FOREIGN KEY (customer_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_subscriptions
        FOREIGN KEY (subscription_id) REFERENCES subscriptions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_products
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_cancellation_admin
        FOREIGN KEY (cancellation_commented_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_orders_quantity_positive CHECK (quantity >= 1),
    CONSTRAINT chk_orders_unit_price_positive CHECK (unit_price_paise > 0),
    CONSTRAINT chk_orders_total_positive CHECK (total_amount_paise > 0),
    CONSTRAINT chk_orders_skip_reason
        CHECK (
            (status = 'SKIPPED' AND skip_reason IS NOT NULL) OR
            (status != 'SKIPPED' AND skip_reason IS NULL)
        ),
    CONSTRAINT chk_orders_cancellation_comment_consistency
        CHECK (
            (cancellation_commented_by IS NULL) = (cancellation_commented_at IS NULL)
        )
);

-- Performance indexes
CREATE INDEX idx_orders_delivery_date_status ON orders (delivery_date, status);
CREATE INDEX idx_orders_customer_delivery_date ON orders (customer_id, delivery_date DESC);
CREATE INDEX idx_orders_subscription_id ON orders (subscription_id);
