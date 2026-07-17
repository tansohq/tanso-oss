-- Seed the smallest complete Tanso flow used by examples/nextjs-ai-credits.
--
-- Safe to rerun. Existing demo usage is removed and the fixed demo customer
-- receives a fresh pool of five AI_CREDITS. No non-demo customer is touched.

BEGIN;

-- Stable IDs keep documentation and smoke tests deterministic.
-- Account:      a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467
-- Feature:      11111111-1111-4111-8111-111111111111
-- Plan:         22222222-2222-4222-8222-222222222222
-- Customer:     33333333-3333-4333-8333-333333333333
-- Subscription: 44444444-4444-4444-8444-444444444444
-- Entitlement:  55555555-5555-4555-8555-555555555555
-- Credit model: 66666666-6666-4666-8666-666666666666
-- Credit pool:  77777777-7777-4777-8777-777777777777
-- Credit grant: 88888888-8888-4888-8888-888888888888
-- Pool link:    99999999-9999-4999-8999-999999999999
-- Plan rule:    aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa

INSERT INTO features (
    feature_id,
    account_id,
    name,
    key,
    description,
    is_enabled,
    is_deleted,
    metadata,
    created_at,
    modified_at
)
VALUES (
    '11111111-1111-4111-8111-111111111111',
    'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467',
    'AI chat generation',
    'ai.chat',
    'One provider-backed AI response',
    true,
    false,
    '{"example":"nextjs-ai-credits"}',
    NOW(),
    NOW()
)
ON CONFLICT (feature_id) DO UPDATE SET
    name = EXCLUDED.name,
    key = EXCLUDED.key,
    description = EXCLUDED.description,
    is_enabled = true,
    is_deleted = false,
    deleted_at = NULL,
    archived_at = NULL,
    metadata = EXCLUDED.metadata,
    modified_at = NOW();

INSERT INTO plans (
    plan_id,
    account_id,
    key,
    name,
    description,
    price_amount,
    interval_months,
    billing_timing,
    currency,
    status,
    metadata,
    created_at,
    modified_at
)
VALUES (
    '22222222-2222-4222-8222-222222222222',
    'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467',
    'developer_demo',
    'Developer demo',
    'Five hard-limit AI credits for the runnable Next.js example',
    0.00,
    1,
    'IN_ADVANCE',
    'USD',
    'ACTIVE',
    '{"example":"nextjs-ai-credits"}',
    NOW(),
    NOW()
)
ON CONFLICT (plan_id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    status = 'ACTIVE',
    deleted_at = NULL,
    archived_at = NULL,
    metadata = EXCLUDED.metadata,
    modified_at = NOW();

INSERT INTO credit_models (
    id,
    account_id,
    name,
    denomination,
    description,
    hard_limit,
    rollover_policy,
    metadata,
    created_at,
    modified_at
)
VALUES (
    '66666666-6666-4666-8666-666666666666',
    'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467',
    'AI request credits',
    'AI_CREDITS',
    'One credit per completed AI request',
    true,
    'NONE',
    '{"example":"nextjs-ai-credits"}',
    NOW(),
    NOW()
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    hard_limit = true,
    rollover_policy = 'NONE',
    deleted_at = NULL,
    metadata = EXCLUDED.metadata,
    modified_at = NOW();

INSERT INTO plan_feature_rules (
    id,
    plan_id,
    feature_id,
    credit_model_id,
    type,
    is_enabled,
    value,
    created_at,
    modified_at
)
VALUES (
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    '22222222-2222-4222-8222-222222222222',
    '11111111-1111-4111-8111-111111111111',
    '66666666-6666-4666-8666-666666666666',
    'BASE',
    true,
    '{"model":"usage","price_per_unit":0.02,"usage_unit_type":"requests","cost_unit":"CURRENCY"}',
    NOW(),
    NOW()
)
ON CONFLICT (id) DO UPDATE SET
    credit_model_id = EXCLUDED.credit_model_id,
    type = 'BASE',
    is_enabled = true,
    value = EXCLUDED.value,
    deleted_at = NULL,
    archived_at = NULL,
    modified_at = NOW();

INSERT INTO customers (
    customer_id,
    account_id,
    external_client_customer_id,
    first_name,
    last_name,
    email,
    source,
    created_at,
    modified_at
)
VALUES (
    '33333333-3333-4333-8333-333333333333',
    'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467',
    'demo-user',
    'Demo',
    'Developer',
    'demo@localhost',
    'MANUAL',
    NOW(),
    NOW()
)
ON CONFLICT (customer_id) DO UPDATE SET
    external_client_customer_id = 'demo-user',
    first_name = 'Demo',
    last_name = 'Developer',
    email = 'demo@localhost',
    source = 'MANUAL',
    deleted_at = NULL,
    archived_at = NULL,
    modified_at = NOW();

INSERT INTO subscriptions (
    subscription_id,
    customer_id,
    plan_id,
    account_id,
    is_active,
    interval_months,
    current_period_start,
    current_period_end,
    billing_anchor_day,
    grace_period_days,
    created_at,
    modified_at
)
VALUES (
    '44444444-4444-4444-8444-444444444444',
    '33333333-3333-4333-8333-333333333333',
    '22222222-2222-4222-8222-222222222222',
    'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467',
    true,
    1,
    NOW(),
    NOW() + INTERVAL '1 month',
    EXTRACT(DAY FROM NOW())::smallint,
    3,
    NOW(),
    NOW()
)
ON CONFLICT (subscription_id) DO UPDATE SET
    is_active = true,
    current_period_start = NOW(),
    current_period_end = NOW() + INTERVAL '1 month',
    cancel_mode = NULL,
    cancel_effective_at = NULL,
    cancelled_at = NULL,
    deleted_at = NULL,
    archived_at = NULL,
    modified_at = NOW();

INSERT INTO entitlements (
    entitlement_id,
    feature_key,
    customer_id,
    subscription_id,
    created_at,
    modified_at
)
VALUES (
    '55555555-5555-4555-8555-555555555555',
    'ai.chat',
    '33333333-3333-4333-8333-333333333333',
    '44444444-4444-4444-8444-444444444444',
    NOW(),
    NOW()
)
ON CONFLICT (entitlement_id) DO UPDATE SET
    feature_key = 'ai.chat',
    revoked_at = NULL,
    deleted_at = NULL,
    archived_at = NULL,
    modified_at = NOW();

INSERT INTO credit_pools (
    credit_pool_id,
    account_id,
    customer_id,
    credit_model_id,
    name,
    denomination,
    balance,
    total_granted,
    total_consumed,
    total_expired,
    total_reversed,
    hard_limit,
    status,
    rollover_policy,
    metadata,
    version,
    created_at,
    modified_at
)
VALUES (
    '77777777-7777-4777-8777-777777777777',
    'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467',
    '33333333-3333-4333-8333-333333333333',
    '66666666-6666-4666-8666-666666666666',
    'Next.js demo credits',
    'AI_CREDITS',
    5,
    5,
    0,
    0,
    0,
    true,
    'ACTIVE',
    'NONE',
    '{"example":"nextjs-ai-credits"}',
    0,
    NOW(),
    NOW()
)
ON CONFLICT (credit_pool_id) DO UPDATE SET
    credit_model_id = EXCLUDED.credit_model_id,
    customer_id = EXCLUDED.customer_id,
    balance = 5,
    total_granted = 5,
    total_consumed = 0,
    total_expired = 0,
    total_reversed = 0,
    hard_limit = true,
    status = 'ACTIVE',
    rollover_policy = 'NONE',
    deleted_at = NULL,
    archived_at = NULL,
    metadata = EXCLUDED.metadata,
    version = credit_pools.version + 1,
    modified_at = NOW();

INSERT INTO credit_grants (
    credit_grant_id,
    credit_pool_id,
    account_id,
    subscription_id,
    grant_type,
    amount,
    remaining,
    description,
    idempotency_key,
    metadata,
    created_at,
    modified_at
)
VALUES (
    '88888888-8888-4888-8888-888888888888',
    '77777777-7777-4777-8777-777777777777',
    'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467',
    '44444444-4444-4444-8444-444444444444',
    'PLAN_INCLUDED',
    5,
    5,
    'Five credits for the Next.js developer example',
    'developer-demo-initial-grant',
    '{"example":"nextjs-ai-credits"}',
    NOW(),
    NOW()
)
ON CONFLICT (credit_grant_id) DO UPDATE SET
    amount = 5,
    remaining = 5,
    expires_at = NULL,
    voided_at = NULL,
    deleted_at = NULL,
    archived_at = NULL,
    metadata = EXCLUDED.metadata,
    modified_at = NOW();

INSERT INTO credit_pool_subscriptions (
    id,
    credit_pool_id,
    subscription_id,
    account_id,
    draw_priority,
    total_drawn,
    created_at,
    modified_at
)
VALUES (
    '99999999-9999-4999-8999-999999999999',
    '77777777-7777-4777-8777-777777777777',
    '44444444-4444-4444-8444-444444444444',
    'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467',
    0,
    0,
    NOW(),
    NOW()
)
ON CONFLICT (id) DO UPDATE SET
    draw_priority = 0,
    draw_limit = NULL,
    total_drawn = 0,
    deleted_at = NULL,
    archived_at = NULL,
    modified_at = NOW();

-- Remove only events and ledger rows owned by the fixed developer demo.
DELETE FROM credit_transactions
WHERE credit_pool_id = '77777777-7777-4777-8777-777777777777';

DELETE FROM events
WHERE account_id = 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467'
  AND customer_id = '33333333-3333-4333-8333-333333333333';

INSERT INTO credit_transactions (
    credit_transaction_id,
    credit_pool_id,
    credit_grant_id,
    account_id,
    subscription_id,
    customer_id,
    transaction_type,
    amount,
    balance_before,
    balance_after,
    description,
    idempotency_key,
    metadata,
    created_at
)
VALUES (
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    '77777777-7777-4777-8777-777777777777',
    '88888888-8888-4888-8888-888888888888',
    'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467',
    '44444444-4444-4444-8444-444444444444',
    '33333333-3333-4333-8333-333333333333',
    'GRANT',
    5,
    0,
    5,
    'Developer demo initial grant',
    'developer-demo-grant-transaction',
    '{"example":"nextjs-ai-credits"}',
    NOW()
);

COMMIT;
