-- V106: Create subscription_change_requests table
-- Enums (change_request_type, change_request_status, change_request_actor_type)
-- were already created in V1__create_enums.sql

CREATE TABLE subscription_change_requests (
    id                   UUID                      NOT NULL DEFAULT gen_random_uuid(),
    subscription_id      UUID                      NOT NULL,
    change_type          change_request_type       NOT NULL,
    new_value            TEXT                      NOT NULL,
    effective_date       DATE                      NOT NULL,
    status               change_request_status     NOT NULL DEFAULT 'APPROVED',
    requested_by_type    change_request_actor_type NOT NULL,
    requested_by_user_id UUID                      NULL,
    created_at           TIMESTAMPTZ               NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_subscription_change_requests PRIMARY KEY (id),
    CONSTRAINT fk_scr_subscriptions
        FOREIGN KEY (subscription_id) REFERENCES subscriptions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_scr_requested_by
        FOREIGN KEY (requested_by_user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_scr_subscription_effective_status
    ON subscription_change_requests (subscription_id, effective_date, status);
