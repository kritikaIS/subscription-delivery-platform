-- V100: Seed the single admin user and credentials.
-- IMPORTANT: Replace the password_hash with a real bcrypt hash before production deployment.
-- The hash below corresponds to the password: "admin123" (bcrypt, cost 10)
-- Generate a new hash with: htpasswd -bnBC 10 "" yourpassword | tr -d ':\n'

-- Insert admin user only if phone does not already exist
INSERT INTO users (
    id, name, email, phone, role, auth_provider, google_id,
    phone_verified, email_verified, is_active, onboarding_completed,
    created_at, updated_at
) VALUES (
    'cccccccc-dddd-eeee-ffff-000000000001',
    'Admin',
    NULL,
    '9999999999',
    'ADMIN',
    'ADMIN_PASSWORD',
    NULL,
    FALSE,
    FALSE,
    TRUE,
    TRUE,
    NOW(),
    NOW()
) ON CONFLICT (phone) DO NOTHING;

-- Insert admin credentials using the actual user id looked up by phone
-- This handles the case where the user row already existed with a different UUID
INSERT INTO admin_credentials (
    id, user_id, password_hash, created_at, updated_at
)
SELECT
    'cccccccc-dddd-eeee-ffff-000000000002',
    u.id,
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    NOW(),
    NOW()
FROM users u
WHERE u.phone = '9999999999'
ON CONFLICT (user_id) DO NOTHING;
