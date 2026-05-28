-- V10: Create delivery_addresses table
-- One address per customer. Enforced via UNIQUE(customer_id).

CREATE TABLE delivery_addresses (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    customer_id    UUID         NOT NULL,
    line1          VARCHAR(255) NOT NULL,
    line2          VARCHAR(255) NULL,
    city           VARCHAR(100) NOT NULL,
    state          VARCHAR(100) NOT NULL,
    pincode        VARCHAR(10)  NOT NULL,
    delivery_notes TEXT         NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_delivery_addresses PRIMARY KEY (id),
    CONSTRAINT uq_delivery_addresses_customer_id UNIQUE (customer_id),
    CONSTRAINT fk_delivery_addresses_users
        FOREIGN KEY (customer_id) REFERENCES users(id) ON DELETE RESTRICT
);
