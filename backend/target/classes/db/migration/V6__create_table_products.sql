-- V6: Create products table

CREATE TABLE products (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    name                  VARCHAR(100) NOT NULL,
    description           TEXT         NULL,
    price_per_unit_paise  BIGINT       NOT NULL,
    unit_label            VARCHAR(20)  NULL,
    category              VARCHAR(50)  NULL,
    is_available          BOOLEAN      NOT NULL DEFAULT TRUE,
    image_url             TEXT         NULL,
    sort_order            INTEGER      NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT chk_products_price_positive CHECK (price_per_unit_paise > 0)
);
