-- Seed script to test subscription cycling
-- Anchor: Today (2026-01-29)
-- Features: Flat Plan, Usage Plan, Hybrid Plan
-- Credentials: cycle_test_user / password123

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
DECLARE
    v_account_id      uuid;
    v_user_id         uuid;
    v_feature_id      uuid;
    v_flat_plan_id    uuid;
    v_usage_plan_id   uuid;
    v_hybrid_plan_id  uuid;
    v_customer_id     uuid;
    v_subscription_id uuid;
    v_now             timestamp := '2026-01-29 12:00:00'; -- Fixed anchor based on "today"
    v_period_start    timestamp := '2025-12-29 12:00:00'; -- 1 month ago
    v_period_end      timestamp := '2026-01-29 12:00:00'; -- Today
BEGIN
    ------------------------------------------------------------
    -- 1) Create Account (Tenant)
    ------------------------------------------------------------
    INSERT INTO accounts (name, created_at, modified_at)
    VALUES ('Cycle Test Tenant', v_now, v_now)
    RETURNING account_id INTO v_account_id;

    ------------------------------------------------------------
    -- 2) Create Owner User
    -- Username: cycle_test_user
    -- Password: password123
    ------------------------------------------------------------
    INSERT INTO users (username, password, first_name, last_name, email, created_at, modified_at)
    VALUES ('cycle_test_user', crypt('password123', gen_salt('bf')), 'Cycle', 'Tester', 'cycle@example.com', v_now, v_now)
    RETURNING user_id INTO v_user_id;

    INSERT INTO users_accounts (user_id, account_id, role, created_at, modified_at)
    VALUES (v_user_id, v_account_id, 'OWNER', v_now, v_now);

    ------------------------------------------------------------
    -- 3) Create Features
    ------------------------------------------------------------
    -- Feature for Flat usage (not tracked by units typically, but let's have it)
    INSERT INTO features (name, key, description, account_id, is_enabled, created_at, modified_at)
    VALUES ('Premium Support', 'premium_support', 'Flat plan feature', v_account_id, TRUE, v_now, v_now)
    RETURNING feature_id INTO v_feature_id;

    -- Feature for Usage usage
    INSERT INTO features (name, key, description, account_id, is_enabled, created_at, modified_at)
    VALUES ('API Access', 'api_access', 'Usage plan feature', v_account_id, TRUE, v_now, v_now)
    RETURNING feature_id INTO v_feature_id;

    -- Feature for Hybrid usage
    INSERT INTO features (name, key, description, account_id, is_enabled, created_at, modified_at)
    VALUES ('Data Processing', 'data_processing', 'Hybrid plan feature', v_account_id, TRUE, v_now, v_now)
    RETURNING feature_id INTO v_feature_id;

    ------------------------------------------------------------
    -- 4) Create Plans
    ------------------------------------------------------------
    -- FLAT PLAN: $100 base, no usage fees
    INSERT INTO plans (key, name, price_amount, billing_timing, interval_months, account_id, created_at, modified_at)
    VALUES ('flat_plan', 'FLAT - Standard Plan', 100.00, 'IN_ADVANCE', 1, v_account_id, v_now, v_now)
    RETURNING plan_id INTO v_flat_plan_id;

    -- USAGE PLAN: $0 base, $0.10 per API call
    INSERT INTO plans (key, name, price_amount, billing_timing, interval_months, account_id, created_at, modified_at)
    VALUES ('usage_plan', 'USAGE - Pay As You Go', 0.00, 'IN_ARREARS', 1, v_account_id, v_now, v_now)
    RETURNING plan_id INTO v_usage_plan_id;

    -- HYBRID PLAN: $50 base (in advance) + $0.50 per Data unit (in arrears)
    INSERT INTO plans (key, name, price_amount, billing_timing, interval_months, account_id, created_at, modified_at)
    VALUES ('hybrid_plan', 'HYBRID - Mixed Plan', 50.00, 'IN_ADVANCE', 1, v_account_id, v_now, v_now)
    RETURNING plan_id INTO v_hybrid_plan_id;

    ------------------------------------------------------------
    -- 5) Plan Feature Rules
    ------------------------------------------------------------
    -- Usage Rule for Usage Plan
    INSERT INTO plan_feature_rules (id, plan_id, feature_id, type, is_enabled, value, created_at, modified_at)
    SELECT gen_random_uuid(), v_usage_plan_id, feature_id, 'BASE', TRUE, 
           jsonb_build_object('model', 'usage', 'usage_unit_type', 'api_calls', 'price_per_unit', 0.10, 'cost_unit', 'USD'),
           v_now, v_now
    FROM features WHERE key = 'api_access' AND account_id = v_account_id;

    -- Usage Rule for Hybrid Plan
    INSERT INTO plan_feature_rules (id, plan_id, feature_id, type, is_enabled, value, created_at, modified_at)
    SELECT gen_random_uuid(), v_hybrid_plan_id, feature_id, 'BASE', TRUE, 
           jsonb_build_object('model', 'usage', 'usage_unit_type', 'data_units', 'price_per_unit', 0.50, 'cost_unit', 'USD'),
           v_now, v_now
    FROM features WHERE key = 'data_processing' AND account_id = v_account_id;

    ------------------------------------------------------------
    -- 6) Customers and Subscriptions
    ------------------------------------------------------------
    
    -- CUSTOMER 1: FLAT
    INSERT INTO customers (external_client_customer_id, first_name, last_name, email, account_id, created_at, modified_at)
    VALUES ('flat_cust_001', 'Flat', 'Customer', 'flat.cust@example.com', v_account_id, v_now, v_now)
    RETURNING customer_id INTO v_customer_id;

    INSERT INTO subscriptions (customer_id, plan_id, account_id, is_active, interval_months, current_period_start, current_period_end, billing_anchor_day, created_at, modified_at)
    VALUES (v_customer_id, v_flat_plan_id, v_account_id, TRUE, 1, v_period_start, v_period_end, 29, v_now, v_now)
    RETURNING subscription_id INTO v_subscription_id;

    INSERT INTO entitlements (entitlement_id, feature_key, customer_id, subscription_id, created_at, modified_at)
    VALUES (gen_random_uuid(), 'premium_support', v_customer_id, v_subscription_id, v_now, v_now);


    -- CUSTOMER 2: USAGE
    INSERT INTO customers (external_client_customer_id, first_name, last_name, email, account_id, created_at, modified_at)
    VALUES ('usage_cust_002', 'Usage', 'Customer', 'usage.cust@example.com', v_account_id, v_now, v_now)
    RETURNING customer_id INTO v_customer_id;

    INSERT INTO subscriptions (customer_id, plan_id, account_id, is_active, interval_months, current_period_start, current_period_end, billing_anchor_day, created_at, modified_at)
    VALUES (v_customer_id, v_usage_plan_id, v_account_id, TRUE, 1, v_period_start, v_period_end, 29, v_now, v_now)
    RETURNING subscription_id INTO v_subscription_id;

    INSERT INTO entitlements (entitlement_id, feature_key, customer_id, subscription_id, created_at, modified_at)
    VALUES (gen_random_uuid(), 'api_access', v_customer_id, v_subscription_id, v_now, v_now);

    -- Usage Events for Customer 2: 150 API calls
    INSERT INTO events (event_id, account_id, customer_id, feature_id, subscription_id, event_type, event_name, event_idempotency_key, occurred_at, usage_units, revenue_amount, usage_unit_type, properties, context)
    SELECT gen_random_uuid(), v_account_id, v_customer_id, feature_id, v_subscription_id, 'ENTITLEMENT_CHECKED', 'api_access', 'usage_seed_2_' || i, 
           v_period_start + (INTERVAL '1 hour' * i), 10, 1.00, 'api_calls', '{"isEntitled": true}', '{"sys_pricing_model": "usage", "sys_captured_unit_price": 0.10}'
    FROM features, generate_series(1, 15) i 
    WHERE key = 'api_access' AND account_id = v_account_id;


    -- CUSTOMER 3: HYBRID
    INSERT INTO customers (external_client_customer_id, first_name, last_name, email, account_id, created_at, modified_at)
    VALUES ('hybrid_cust_003', 'Hybrid', 'Customer', 'hybrid.cust@example.com', v_account_id, v_now, v_now)
    RETURNING customer_id INTO v_customer_id;

    INSERT INTO subscriptions (customer_id, plan_id, account_id, is_active, interval_months, current_period_start, current_period_end, billing_anchor_day, created_at, modified_at)
    VALUES (v_customer_id, v_hybrid_plan_id, v_account_id, TRUE, 1, v_period_start, v_period_end, 29, v_now, v_now)
    RETURNING subscription_id INTO v_subscription_id;

    INSERT INTO entitlements (entitlement_id, feature_key, customer_id, subscription_id, created_at, modified_at)
    VALUES (gen_random_uuid(), 'data_processing', v_customer_id, v_subscription_id, v_now, v_now);

    -- Usage Events for Customer 3: 40 units
    INSERT INTO events (event_id, account_id, customer_id, feature_id, subscription_id, event_type, event_name, event_idempotency_key, occurred_at, usage_units, revenue_amount, usage_unit_type, properties, context)
    SELECT gen_random_uuid(), v_account_id, v_customer_id, feature_id, v_subscription_id, 'ENTITLEMENT_CHECKED', 'data_processing', 'hybrid_seed_3_' || i, 
           v_period_start + (INTERVAL '2 hour' * i), 5, 2.50, 'data_units', '{"isEntitled": true}', '{"sys_pricing_model": "usage", "sys_captured_unit_price": 0.50}'
    FROM features, generate_series(1, 8) i 
    WHERE key = 'data_processing' AND account_id = v_account_id;

    ------------------------------------------------------------
    -- 7) Historical Invoices (PENDING)
    ------------------------------------------------------------
    -- All set to PENDING for the period that just ended, so the cycle job can pick them up and process them
    
    -- Invoice for Flat
    INSERT INTO invoices (invoice_id, subscription_id, account_id, amount, status, currency, invoice_period_start, invoice_period_end, due_date, type, created_at, modified_at)
    SELECT gen_random_uuid(), subscription_id, account_id, 100.00, 'PENDING', 'USD', current_period_start, current_period_end, current_period_end, 'REGULAR', v_now, v_now
    FROM subscriptions WHERE customer_id IN (SELECT customer_id FROM customers WHERE external_client_customer_id = 'flat_cust_001');

    -- Invoice for Usage
    INSERT INTO invoices (invoice_id, subscription_id, account_id, amount, status, currency, invoice_period_start, invoice_period_end, due_date, type, created_at, modified_at)
    SELECT gen_random_uuid(), subscription_id, account_id, 0.00, 'PENDING', 'USD', current_period_start, current_period_end, current_period_end, 'REGULAR', v_now, v_now
    FROM subscriptions WHERE customer_id IN (SELECT customer_id FROM customers WHERE external_client_customer_id = 'usage_cust_002');

    -- Invoice for Hybrid
    INSERT INTO invoices (invoice_id, subscription_id, account_id, amount, status, currency, invoice_period_start, invoice_period_end, due_date, type, created_at, modified_at)
    SELECT gen_random_uuid(), subscription_id, account_id, 50.00, 'PENDING', 'USD', current_period_start, current_period_end, current_period_end, 'REGULAR', v_now, v_now
    FROM subscriptions WHERE customer_id IN (SELECT customer_id FROM customers WHERE external_client_customer_id = 'hybrid_cust_003');

END $$;
