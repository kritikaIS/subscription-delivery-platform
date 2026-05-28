-- V9: Create refresh_tokens table
-- Stores hashed refresh tokens for JWT session management.
-- New login revokes previous token (BR-AUTH-04).

CREATE TABLE refresh_tokens (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT fk_refresh_tokens_users
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
