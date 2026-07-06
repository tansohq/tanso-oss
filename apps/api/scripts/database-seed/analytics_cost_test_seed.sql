-- Seed script for testing analytics cost aggregation
-- This script populates the database with an account, users, api keys, customers, features, plans, rules, events, and invoices.

-- Variables (using fixed UUIDs for consistency)
-- Account ID: '00000000-0000-0000-0000-000000000001'
-- User ID: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
-- Plan ID: '11111111-1111-1111-1111-111111111111'
-- Feature 1 (AI Tokens) ID: '22222222-2222-2222-2222-222222222222'
-- Feature 2 (Storage) ID: '33333333-3333-3333-3333-333333333333'
-- Customer 1 ID: '44444444-4444-4444-4444-444444444444'
-- Customer 2 ID: '55555555-5555-5555-5555-555555555555'
-- Customer 3 ID: '66666666-6666-6666-6666-666666666666'
-- Customer 4 ID: '77777777-7777-7777-7777-777777777777'
-- Customer 5 ID: '88888888-8888-8888-8888-888888888888'

-- 1. Create Account
INSERT INTO accounts (account_id, name, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'Test Analytics Account', NOW())
ON CONFLICT (account_id) DO NOTHING;

-- 2. Create User
-- Password is 'password' hashed with a typical BCrypt-like string for testing
INSERT INTO users (user_id, username, password, first_name, last_name, email, created_at)
VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'test', '$2a$12$Gn/o/VXjEYFh1iWrKiTjTuDeaWyMFyESGVZKsxsRV5aheRRJWfBG2', 'Admin', 'User', 'admin@example.com', NOW())
ON CONFLICT (user_id) DO NOTHING;

-- 3. Link User to Account
INSERT INTO users_accounts (id, user_id, account_id, role, created_at)
VALUES (gen_random_uuid(), 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '00000000-0000-0000-0000-000000000001', 'ADMIN', NOW());

-- 4. API keys are hashed at rest and issued through the app (POST /api-key), not seeded.
--    A plaintext key_value here can never match the SHA-256 lookup, so no key is seeded.

-- 5. Create Features
INSERT INTO features (feature_id, name, key, description, account_id, is_enabled, is_deleted, created_at)
VALUES 
('22222222-2222-2222-2222-222222222222', 'AI Tokens', 'ai_tokens', 'Cost per AI token usage', '00000000-0000-0000-0000-000000000001', true, false, NOW()),
('33333333-3333-3333-3333-333333333333', 'Storage', 'storage_gb', 'Cost per GB of storage', '00000000-0000-0000-0000-000000000001', true, false, NOW())
ON CONFLICT (feature_id) DO NOTHING;

-- 6. Create Plan
INSERT INTO plans (plan_id, key, name, price_amount, account_id, created_at, interval_months, billing_timing)
VALUES ('11111111-1111-1111-1111-111111111111', 'pro_plan', 'Pro Plan', 100.00, '00000000-0000-0000-0000-000000000001', NOW(), 1, 'IN_ADVANCE')
ON CONFLICT (plan_id) DO NOTHING;

-- 7. Create Plan Feature Rules (Defining cost_per_unit)
-- Rule for AI Tokens: revenue $0.05/unit, COGS $0.02/unit
INSERT INTO plan_feature_rules (id, plan_id, feature_id, is_enabled, type, value, created_at)
VALUES (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', true, 'BASE', '{"model": "usage", "usage_unit_type": "token", "price_per_unit": 0.05, "cost_unit": "TOKENS", "cost_model": "simple", "cost_per_unit": 0.02}', NOW());

-- Rule for Storage: revenue $0.50/unit, COGS $0.30/unit
INSERT INTO plan_feature_rules (id, plan_id, feature_id, is_enabled, type, value, created_at)
VALUES (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333', true, 'BASE', '{"model": "usage", "usage_unit_type": "gb", "price_per_unit": 0.50, "cost_unit": "CURRENCY", "cost_model": "simple", "cost_per_unit": 0.30}', NOW());

-- 8. Create Customers
INSERT INTO customers (customer_id, first_name, last_name, email, account_id, external_client_customer_id, created_at)
VALUES 
('44444444-4444-4444-4444-444444444444', 'John', 'Doe', 'john@example.com', '00000000-0000-0000-0000-000000000001', 'ext_john', NOW()),
('55555555-5555-5555-5555-555555555555', 'Jane', 'Smith', 'jane@example.com', '00000000-0000-0000-0000-000000000001', 'ext_jane', NOW()),
('66666666-6666-6666-6666-666666666666', 'Bob', 'Johnson', 'bob@example.com', '00000000-0000-0000-0000-000000000001', 'ext_bob', NOW()),
('77777777-7777-7777-7777-777777777777', 'Alice', 'Williams', 'alice@example.com', '00000000-0000-0000-0000-000000000001', 'ext_alice', NOW()),
('88888888-8888-8888-8888-888888888888', 'Charlie', 'Brown', 'charlie@example.com', '00000000-0000-0000-0000-000000000001', 'ext_charlie', NOW())
ON CONFLICT (customer_id) DO NOTHING;

-- 9. Create Subscriptions
-- John's subscription
INSERT INTO subscriptions (subscription_id, customer_id, plan_id, account_id, is_active, current_period_start, current_period_end, created_at)
VALUES ('10000000-0000-0000-0000-000000000001', '44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000001', true, NOW() - INTERVAL '15 days', NOW() + INTERVAL '15 days', NOW());

-- Jane's subscription
INSERT INTO subscriptions (subscription_id, customer_id, plan_id, account_id, is_active, current_period_start, current_period_end, created_at)
VALUES ('20000000-0000-0000-0000-000000000002', '55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000001', true, NOW() - INTERVAL '10 days', NOW() + INTERVAL '20 days', NOW());

-- Bob's subscription
INSERT INTO subscriptions (subscription_id, customer_id, plan_id, account_id, is_active, current_period_start, current_period_end, created_at)
VALUES ('30000000-0000-0000-0000-000000000003', '66666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000001', true, NOW() - INTERVAL '5 days', NOW() + INTERVAL '25 days', NOW());

-- Alice's subscription
INSERT INTO subscriptions (subscription_id, customer_id, plan_id, account_id, is_active, current_period_start, current_period_end, created_at)
VALUES ('40000000-0000-0000-0000-000000000004', '77777777-7777-7777-7777-777777777777', '11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000001', true, NOW() - INTERVAL '20 days', NOW() + INTERVAL '10 days', NOW());

-- Charlie's subscription
INSERT INTO subscriptions (subscription_id, customer_id, plan_id, account_id, is_active, current_period_start, current_period_end, created_at)
VALUES ('50000000-0000-0000-0000-000000000005', '88888888-8888-8888-8888-888888888888', '11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000001', true, NOW() - INTERVAL '30 days', NOW(), NOW());

-- 10. Create Events (Usage units, revenue, and COGS)
-- John: 10 units of AI Tokens (revenue=0.50, cost=0.20)
INSERT INTO events (event_id, account_id, event_idempotency_key, event_name, occurred_at, customer_id, feature_id, usage_units, revenue_amount, cost_amount, event_type, properties, meta, context, created_at)
VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'ik_1', 'token_usage', NOW() - INTERVAL '1 day', '44444444-4444-4444-4444-444444444444', '22222222-2222-2222-2222-222222222222', 10, 0.50, 0.20, 'CLIENT_TRACKED', '{}', '{}', '{"sys_pricing_model": "usage", "sys_captured_unit_price": 0.05, "sys_cost_model": "simple", "sys_cost_params": {"cost_per_unit": 0.02, "cost_unit": "TOKENS"}}', NOW());

-- Jane: 20 units of AI Tokens (revenue=1.00, cost=0.40) and 2 units of Storage (revenue=1.00, cost=0.60)
INSERT INTO events (event_id, account_id, event_idempotency_key, event_name, occurred_at, customer_id, feature_id, usage_units, revenue_amount, cost_amount, event_type, properties, meta, context, created_at)
VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'ik_2', 'token_usage', NOW() - INTERVAL '2 days', '55555555-5555-5555-5555-555555555555', '22222222-2222-2222-2222-222222222222', 20, 1.00, 0.40, 'CLIENT_TRACKED', '{}', '{}', '{"sys_pricing_model": "usage", "sys_captured_unit_price": 0.05, "sys_cost_model": "simple", "sys_cost_params": {"cost_per_unit": 0.02, "cost_unit": "TOKENS"}}', NOW());

INSERT INTO events (event_id, account_id, event_idempotency_key, event_name, occurred_at, customer_id, feature_id, usage_units, revenue_amount, cost_amount, event_type, properties, meta, context, created_at)
VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'ik_3', 'storage_usage', NOW() - INTERVAL '3 days', '55555555-5555-5555-5555-555555555555', '33333333-3333-3333-3333-333333333333', 2, 1.00, 0.60, 'ENTITLEMENT_CHECKED', '{}', '{}', '{"sys_pricing_model": "usage", "sys_captured_unit_price": 0.50, "sys_cost_model": "simple", "sys_cost_params": {"cost_per_unit": 0.30, "cost_unit": "CURRENCY"}}', NOW());

-- Bob: 100 units of AI Tokens (Big spender, revenue=5.00, cost=2.00)
INSERT INTO events (event_id, account_id, event_idempotency_key, event_name, occurred_at, customer_id, feature_id, usage_units, revenue_amount, cost_amount, event_type, properties, meta, context, created_at)
VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'ik_4', 'token_usage', NOW() - INTERVAL '1 day', '66666666-6666-6666-6666-666666666666', '22222222-2222-2222-2222-222222222222', 100, 5.00, 2.00, 'CLIENT_TRACKED', '{}', '{}', '{"sys_pricing_model": "usage", "sys_captured_unit_price": 0.05, "sys_cost_model": "simple", "sys_cost_params": {"cost_per_unit": 0.02, "cost_unit": "TOKENS"}}', NOW());

-- Alice: 5 units of Storage (revenue=2.50, cost=1.50)
INSERT INTO events (event_id, account_id, event_idempotency_key, event_name, occurred_at, customer_id, feature_id, usage_units, revenue_amount, cost_amount, event_type, properties, meta, context, created_at)
VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'ik_5', 'storage_usage', NOW() - INTERVAL '5 days', '77777777-7777-7777-7777-777777777777', '33333333-3333-3333-3333-333333333333', 5, 2.50, 1.50, 'CLIENT_TRACKED', '{}', '{}', '{"sys_pricing_model": "usage", "sys_captured_unit_price": 0.50, "sys_cost_model": "simple", "sys_cost_params": {"cost_per_unit": 0.30, "cost_unit": "CURRENCY"}}', NOW());

-- 11. Create Invoices
-- John: Paid Invoice
INSERT INTO invoices (invoice_id, subscription_id, amount, due_date, status, currency, account_id, type, invoice_period_start, invoice_period_end, created_at)
VALUES (gen_random_uuid(), '10000000-0000-0000-0000-000000000001', 100.00, NOW() - INTERVAL '30 days', 'PAID', 'USD', '00000000-0000-0000-0000-000000000001', 'REGULAR', NOW() - INTERVAL '60 days', NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days');

-- Jane: Due Invoice
INSERT INTO invoices (invoice_id, subscription_id, amount, due_date, status, currency, account_id, type, invoice_period_start, invoice_period_end, created_at)
VALUES (gen_random_uuid(), '20000000-0000-0000-0000-000000000002', 100.00, NOW() + INTERVAL '5 days', 'DUE', 'USD', '00000000-0000-0000-0000-000000000001', 'REGULAR', NOW() - INTERVAL '25 days', NOW() + INTERVAL '5 days', NOW());

-- Bob: Overdue Invoice
INSERT INTO invoices (invoice_id, subscription_id, amount, due_date, status, currency, account_id, type, invoice_period_start, invoice_period_end, created_at)
VALUES (gen_random_uuid(), '30000000-0000-0000-0000-000000000003', 100.00, NOW() - INTERVAL '2 days', 'DUE', 'USD', '00000000-0000-0000-0000-000000000001', 'REGULAR', NOW() - INTERVAL '32 days', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days');

-- Alice: Paid Invoice
INSERT INTO invoices (invoice_id, subscription_id, amount, due_date, status, currency, account_id, type, invoice_period_start, invoice_period_end, created_at)
VALUES (gen_random_uuid(), '40000000-0000-0000-0000-000000000004', 100.00, NOW() - INTERVAL '10 days', 'PAID', 'USD', '00000000-0000-0000-0000-000000000001', 'REGULAR', NOW() - INTERVAL '40 days', NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days');

-- Charlie: Pending Invoice
INSERT INTO invoices (invoice_id, subscription_id, amount, due_date, status, currency, account_id, type, invoice_period_start, invoice_period_end, created_at)
VALUES (gen_random_uuid(), '50000000-0000-0000-0000-000000000005', 100.00, NOW() + INTERVAL '30 days', 'PENDING', 'USD', '00000000-0000-0000-0000-000000000001', 'REGULAR', NOW() - INTERVAL '30 days', NOW(), NOW());
