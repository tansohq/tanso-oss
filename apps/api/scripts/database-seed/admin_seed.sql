-- Seed script to create admin account and user
-- Account ID: '00000000-0000-0000-0000-000000000001'
-- User ID: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'

-- 1. Create Account
INSERT INTO accounts (account_id, name, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'Admin Account', NOW())
ON CONFLICT (account_id) DO NOTHING;

-- 2. Create User
-- Password is 'password' hashed with BCrypt
INSERT INTO users (user_id, username, password, first_name, last_name, email, created_at)
VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'test', '$2a$12$Gn/o/VXjEYFh1iWrKiTjTuDeaWyMFyESGVZKsxsRV5aheRRJWfBG2', 'Admin', 'User', 'admin@example.com', NOW())
ON CONFLICT (user_id) DO NOTHING;

-- 3. Link User to Account
INSERT INTO users_accounts (id, user_id, account_id, role, created_at)
VALUES (gen_random_uuid(), 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '00000000-0000-0000-0000-000000000001', 'ADMIN', NOW())
ON CONFLICT DO NOTHING;

-- 4. API keys are NOT seeded. Keys are high-entropy secrets that the app stores only as a
-- SHA-256 hash, so no working key can be inserted here. Issue one through the running app
-- (POST /api/v1/account/api-key) after logging in; the raw key is shown once at that time.

-- 5. Initialize Account Settings
INSERT INTO account_settings (account_id, stripe_enabled)
VALUES ('00000000-0000-0000-0000-000000000001', false)
ON CONFLICT (account_id) DO UPDATE SET stripe_enabled = false;
