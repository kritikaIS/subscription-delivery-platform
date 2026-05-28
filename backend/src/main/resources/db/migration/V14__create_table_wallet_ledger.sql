-- V14: Create wallet_ledger table
-- Append-only financial ledger. Rows are NEVER updated or deleted.

CREATE TABLE wallet_ledger (
    id                    UUID               NOT NULL DEFAULT gen_random_uuid(),
    customer_id           UUID               NOT NULL,
    order_id              UUID               NULL,
    entry_type            wallet_entry_type  NOT NULL,
    source_type           wallet_source_type NOT NULL,
    amount_paise          BIGINT             NOT NULL,
    running_balance_paise BIGINT             NOT NULL,
    description           TEXT               NULL,
    reference             VARCHAR(100)       NULL,
    created_by_user_id    UUID               NULL,
    created_at            TIMESTAMPTZ        NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_wallet_ledger PRIMARY KEY (id),
    CONSTRAINT fk_wallet_ledger_customers
        FOREIGN KEY (customer_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_wallet_ledger_orders
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT,
    CONSTRAINT fk_wallet_ledger_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_wallet_ledger_amount_positive CHECK (amount_paise > 0),
    CONSTRAINT chk_wallet_ledger_system_adjustment_no_actor
        CHECK (
            (source_type = 'SYSTEM_ADJUSTMENT' AND created_by_user_id IS NULL) OR
            (source_type != 'SYSTEM_ADJUSTMENT')
        )
);

-- Idempotency: prevents duplicate deductions for the same order + source type
CREATE UNIQUE INDEX uq_wallet_ledger_order_source
    ON wallet_ledger (order_id, source_type)
    WHERE order_id IS NOT NULL;

-- Performance: balance lookup (most recent entry per customer)
CREATE INDEX idx_wallet_ledger_customer_created_at
    ON wallet_ledger (customer_id, created_at DESC);
