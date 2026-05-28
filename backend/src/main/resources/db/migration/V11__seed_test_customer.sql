-- V11: Seed a test customer for development/testing purposes.
-- TEMPORARY: Remove this migration before production deployment.

INSERT INTO users (
    id, name, email, phone, role, auth_provider, google_id,
    phone_verified, email_verified, is_active, onboarding_completed,
    created_at, updated_at
) VALUES (
    'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
    'Test Customer',
    'testcustomer@example.com',
    NULL,
    'CUSTOMER',
    'GOOGLE',
    'google-test-sub-123456',
    FALSE,
    TRUE,
    TRUE,
    FALSE,
    NOW(),
    NOW()
) ON CONFLICT (id) DO NOTHING;
