-- V17: Create admin_audit_log table
-- Immutable audit trail of all admin mutations. Retained forever. Never updated or deleted.

CREATE TABLE admin_audit_log (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    action_type   VARCHAR(100) NOT NULL,
    target_entity VARCHAR(50)  NOT NULL,
    target_id     VARCHAR(255) NOT NULL,
    old_value     JSONB        NULL,
    new_value     JSONB        NULL,
    acting_admin  UUID         NOT NULL,
    notes         TEXT         NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_admin_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_admin_audit_log_acting_admin
        FOREIGN KEY (acting_admin) REFERENCES users(id) ON DELETE RESTRICT
);

-- Performance indexes for admin dashboard queries
CREATE INDEX idx_admin_audit_log_created_at
    ON admin_audit_log (created_at DESC);

CREATE INDEX idx_admin_audit_log_acting_admin
    ON admin_audit_log (acting_admin, created_at DESC);

CREATE INDEX idx_admin_audit_log_target
    ON admin_audit_log (target_entity, target_id);
