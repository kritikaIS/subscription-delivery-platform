-- V2: Create users table
-- Required as FK target for product_price_history and other tables.

CREATE TABLE users (
    id                   UUID          NOT NULL DEFAULT gen_random_uuid(),
    name                 VARCHAR(100)  NOT NULL,
    email                VARCHAR(150)  NULL,
    phone                VARCHAR(15)   NULL,
    role                 user_role     NOT NULL,
    auth_provider        auth_provider NOT NULL,
    google_id            VARCHAR(255)  NULL,
    phone_verified       BOOLEAN       NOT NULL DEFAULT FALSE,
    email_verified       BOOLEAN       NOT NULL DEFAULT FALSE,
    is_active            BOOLEAN       NOT NULL DEFAULT TRUE,
    onboarding_completed BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_google_id UNIQUE (google_id),
    CONSTRAINT uq_users_phone UNIQUE (phone),
    CONSTRAINT chk_users_google_auth CHECK (
        (auth_provider = 'GOOGLE' AND google_id IS NOT NULL) OR
        (auth_provider = 'ADMIN_PASSWORD' AND google_id IS NULL)
    )
);
