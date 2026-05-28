-- V1: Create all PostgreSQL enum types
-- These must precede all table migrations that reference them.

CREATE TYPE user_role AS ENUM ('ADMIN', 'CUSTOMER');

CREATE TYPE auth_provider AS ENUM ('GOOGLE', 'ADMIN_PASSWORD');

CREATE TYPE subscription_status AS ENUM ('PENDING_START', 'ACTIVE', 'PAUSED', 'CANCELLED');

CREATE TYPE pause_reason AS ENUM (
    'USER_PAUSED',
    'SYSTEM_PAUSED_PRODUCT_DISABLED',
    'CUSTOMER_DEACTIVATED'
);

CREATE TYPE change_request_type AS ENUM ('QUANTITY', 'PRODUCT');

CREATE TYPE change_request_status AS ENUM ('APPROVED', 'APPLIED', 'SUPERSEDED');

CREATE TYPE change_request_actor_type AS ENUM ('CUSTOMER', 'ADMIN');

CREATE TYPE order_status AS ENUM ('SCHEDULED', 'LOCKED', 'DELIVERED', 'SKIPPED', 'CANCELLED');

CREATE TYPE skip_reason AS ENUM (
    'CUSTOMER_UNAVAILABLE',
    'PRODUCT_UNAVAILABLE',
    'DAMAGED',
    'OTHER'
);

CREATE TYPE delivery_record_status AS ENUM ('PENDING', 'DELIVERED', 'SKIPPED', 'CANCELLED');

CREATE TYPE wallet_entry_type AS ENUM ('CREDIT', 'DEBIT', 'REFUND');

CREATE TYPE wallet_source_type AS ENUM (
    'ADMIN_CREDIT',
    'DELIVERY_DEBIT',
    'REFUND',
    'MANUAL_DEBIT',
    'MANUAL_ADJUSTMENT',
    'HISTORICAL_CORRECTION',
    'SYSTEM_ADJUSTMENT'
);

CREATE TYPE scheduler_job_status AS ENUM ('RUNNING', 'COMPLETED', 'FAILED');
