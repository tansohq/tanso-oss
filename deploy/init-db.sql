-- First-run admin bootstrap for tanso-oss
-- Runs automatically on first docker compose up
-- Default login: admin@example.com / changeme
--
-- To customize, set ADMIN_EMAIL and ADMIN_PASSWORD env vars
-- Password below is BCrypt hash of 'changeme'

INSERT INTO accounts (account_id, name, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'Admin Account', NOW())
ON CONFLICT (account_id) DO NOTHING;

INSERT INTO users (user_id, username, password, first_name, last_name, email, created_at)
VALUES (
  'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  'admin@example.com',
  '$2a$12$LJ3m4ys3GXSF3JFbcGMjiOCqyYHiQnG2NUbqfvYsWMTFp5ZwVXyWu',
  'Admin', 'User', 'admin@example.com', NOW()
)
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO users_accounts (id, user_id, account_id, role, created_at)
VALUES (gen_random_uuid(), 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '00000000-0000-0000-0000-000000000001', 'ADMIN', NOW())
ON CONFLICT DO NOTHING;

INSERT INTO account_api_keys (api_key_id, account_id, key_type, key_value, is_active, expires_at, created_at)
VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'SECRET', 'sk_test_admin_default_key', true, NOW() + INTERVAL '10 years', NOW())
ON CONFLICT DO NOTHING;

INSERT INTO account_settings (account_id, stripe_enabled)
VALUES ('00000000-0000-0000-0000-000000000001', false)
ON CONFLICT (account_id) DO NOTHING;
