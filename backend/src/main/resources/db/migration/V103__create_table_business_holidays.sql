-- V103: Create business_holidays table
-- Admin-managed holiday table. Supports hard deletion (the only entity in MVP that does — BR-GEN-01).

CREATE TABLE business_holidays (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    holiday_date DATE         NOT NULL,
    name         VARCHAR(100) NOT NULL,
    created_by   UUID         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_business_holidays PRIMARY KEY (id),
    CONSTRAINT uq_business_holidays_date UNIQUE (holiday_date),
    CONSTRAINT fk_business_holidays_users
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT
);
