-- V3: Create admin_credentials table
-- Stores bcrypt-hashed password for the admin account.
-- Separated from users table for security isolation.

CREATE TABLE admin_credentials (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_admin_credentials PRIMARY KEY (id),
    CONSTRAINT uq_admin_credentials_user_id UNIQUE (user_id),
    CONSTRAINT fk_admin_credentials_users
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);
