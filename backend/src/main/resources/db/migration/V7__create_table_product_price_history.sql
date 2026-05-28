-- V7: Create product_price_history table
-- Append-only audit log of every product price change. Never updated or deleted.

CREATE TABLE product_price_history (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    product_id      UUID        NOT NULL,
    old_price_paise BIGINT      NOT NULL,
    new_price_paise BIGINT      NOT NULL,
    changed_by      UUID        NOT NULL,
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_product_price_history PRIMARY KEY (id),
    CONSTRAINT fk_product_price_history_products
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    CONSTRAINT fk_product_price_history_users
        FOREIGN KEY (changed_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT chk_product_price_history_old_positive CHECK (old_price_paise > 0),
    CONSTRAINT chk_product_price_history_new_positive CHECK (new_price_paise > 0)
);
