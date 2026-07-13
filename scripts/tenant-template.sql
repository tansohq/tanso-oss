-- SQL Template for creating a new tenant
-- Replace the following placeholders:
-- {{ACCOUNT_ID}}: A new UUID
-- {{ACCOUNT_NAME}}: The name of the account
-- {{USER_ID}}: A new UUID
-- {{USERNAME}}: Desired username
-- {{PASSWORD_HASH}}: BCrypt hash (factor 12) of the password
-- {{EMAIL}}: User's email address
-- {{USERS_ACCOUNTS_ID}}: A new UUID
-- {{API_KEY_ID}}: A new UUID
-- {{API_KEY_VALUE}}: Format: sk_prod_<uuid_no_dashes> or sk_test_<uuid_no_dashes>
-- {{EXPIRES_AT}}: Expiration date (e.g., '2036-01-26 19:24:00')

BEGIN;

-- 1. Create Account
INSERT INTO accounts (account_id, name, created_at, modified_at)
VALUES ('{{ACCOUNT_ID}}', '{{ACCOUNT_NAME}}', NOW(), NOW());

-- 2. Create User
INSERT INTO users (user_id, username, password, email, first_name, last_name, address, created_at, modified_at)
VALUES ('{{USER_ID}}', '{{USERNAME}}', '{{PASSWORD_HASH}}', '{{EMAIL}}', '', '', '', NOW(), NOW());

-- 3. Link User to Account (Role: ADMIN)
INSERT INTO users_accounts (id, user_id, account_id, role, created_at, modified_at)
VALUES ('{{USERS_ACCOUNTS_ID}}', '{{USER_ID}}', '{{ACCOUNT_ID}}', 'ADMIN', NOW(), NOW());

-- 4. Initialize Account Settings
INSERT INTO account_settings (account_id, stripe_enabled)
VALUES ('{{ACCOUNT_ID}}', false);

-- 5. Create API Key
INSERT INTO account_api_keys (api_key_id, account_id, key_type, key_value, is_active, expires_at, created_at, modified_at)
VALUES ('{{API_KEY_ID}}', '{{ACCOUNT_ID}}', 'SECRET', '{{API_KEY_VALUE}}', true, '{{EXPIRES_AT}}', NOW(), NOW());

COMMIT;
