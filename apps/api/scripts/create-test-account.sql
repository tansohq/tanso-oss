-- Create test account
-- Username: test / Password: password

BEGIN;

-- 1. Create Account
INSERT INTO accounts (account_id, name, created_at, modified_at)
VALUES ('a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'Test Account', NOW(), NOW());

-- 2. Create User
INSERT INTO users (user_id, username, password, email, first_name, last_name, created_at, modified_at)
VALUES ('0ab38d70-120e-4fce-9273-36496d1f2db7', 'test', '$2b$12$3WnK.nlyQHLNEEOGtnblPeFjehktUeFDpQa7WK.EbsAQ51VAzEqci', 'test@test.com', '', '', NOW(), NOW());

-- 3. Link User to Account (Role: ADMIN)
INSERT INTO users_accounts (id, user_id, account_id, role, created_at, modified_at)
VALUES ('d6559146-c789-43f5-b95f-be486ca9b861', '0ab38d70-120e-4fce-9273-36496d1f2db7', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'ADMIN', NOW(), NOW());

-- 4. Initialize Account Settings
INSERT INTO account_settings (account_id, stripe_mode)
VALUES ('a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'NONE');

-- 5. Create API Key
INSERT INTO account_api_keys (api_key_id, account_id, key_type, key_value, is_active, expires_at, created_at, modified_at)
VALUES ('286c570c-8a08-441d-92c0-2fc074a93457', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'SECRET', 'sk_test_828df0fc77874c219f353417fbca1ef4', true, '2036-01-26 19:24:00', NOW(), NOW());

COMMIT;
