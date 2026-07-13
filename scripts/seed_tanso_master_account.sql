-- =============================================================================
-- Tanso Master Account Seed Script
-- =============================================================================
-- Run this script to initialize the Tanso Platform master account in a new environment.
-- Safe to run multiple times (uses ON CONFLICT DO NOTHING).
--
-- Master Account UUID: 00000000-0000-0000-0000-000000000000
-- =============================================================================

BEGIN;

-- 1. Master Account
INSERT INTO accounts (account_id, name, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000000',
    'Tanso Platform',
    NOW()
)
ON CONFLICT (account_id) DO NOTHING;

-- 2. API Key (for internal context switching)
-- !! CHANGE ME !! Generate your own secret key value before running in any real
-- environment. Anyone who knows this value can act as the master account.
INSERT INTO account_api_keys (api_key_id, account_id, key_type, key_value, is_active, expires_at, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000031',
    '00000000-0000-0000-0000-000000000000',
    'secret',
    'sk_live_CHANGE_ME_generate_your_own_master_key',
    true,
    '2099-01-01 00:00:00',
    NOW()
)
ON CONFLICT (api_key_id) DO NOTHING;

-- 3. Feature: API Access
INSERT INTO features (feature_id, account_id, name, key, description, is_enabled, is_deleted, metadata, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000101',
    '00000000-0000-0000-0000-000000000000',
    'API Access',
    'feature_api_access',
    'Access to the Tanso Platform API and Dashboard',
    true,
    false,
    '{}',
    NOW()
)
ON CONFLICT (feature_id) DO NOTHING;

-- 4. Plan: Free
INSERT INTO plans (plan_id, account_id, key, name, description, price_amount, interval_months, billing_timing, metadata, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000000',
    'tanso_free',
    'Free',
    'Free tier for Tanso Platform',
    0.00,
    1,
    'IN_ADVANCE',
    '{}',
    NOW()
)
ON CONFLICT (plan_id) DO NOTHING;

-- 5. Link Feature to Plan
INSERT INTO plan_feature_rules (id, plan_id, feature_id, value, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000301',
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000101',
    '{"pricingModel": {"type": "usage", "unitAmount": 0}, "entitlement": {"type": "boolean", "value": true}}',
    NOW()
)
ON CONFLICT (id) DO NOTHING;

COMMIT;
