-- Scale seed script for testing analytics cost aggregation at scale
-- This script generates 100+ customers with subscriptions, events, and invoices.
-- It reuses the same Account ID from the original seed script: '00000000-0000-0000-0000-000000000001'

DO $$
DECLARE
    v_account_id UUID := '00000000-0000-0000-0000-000000000001';
    v_user_id UUID := 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
    v_plan_id UUID := '11111111-1111-1111-1111-111111111111';
    v_feature_ai_id UUID := '22222222-2222-2222-2222-222222222222';
    v_feature_storage_id UUID := '33333333-3333-3333-3333-333333333333';
    
    v_customer_id UUID;
    v_subscription_id UUID;
    v_invoice_id UUID;
    
    i INTEGER;
    j INTEGER;
    
    v_first_names TEXT[] := ARRAY['James', 'Mary', 'Robert', 'Patricia', 'John', 'Jennifer', 'Michael', 'Linda', 'David', 'Elizabeth', 'William', 'Barbara', 'Richard', 'Susan', 'Joseph', 'Jessica', 'Thomas', 'Sarah', 'Christopher', 'Karen'];
    v_last_names TEXT[] := ARRAY['Smith', 'Johnson', 'Williams', 'Brown', 'Jones', 'Garcia', 'Miller', 'Davis', 'Rodriguez', 'Martinez', 'Hernandez', 'Lopez', 'Gonzalez', 'Wilson', 'Anderson', 'Thomas', 'Taylor', 'Moore', 'Jackson', 'Martin'];
    
    v_first_name TEXT;
    v_last_name TEXT;
    v_email TEXT;
BEGIN
    -- 1. Ensure Account exists
    INSERT INTO accounts (account_id, name, created_at)
    VALUES (v_account_id, 'Scale Test Account', NOW())
    ON CONFLICT (account_id) DO NOTHING;

    -- 2. Ensure User exists
    INSERT INTO users (user_id, username, password, first_name, last_name, email, created_at)
    VALUES (v_user_id, 'test', '$2a$12$Gn/o/VXjEYFh1iWrKiTjTuDeaWyMFyESGVZKsxsRV5aheRRJWfBG2', 'Scale', 'Admin', 'scale_admin@example.com', NOW())
    ON CONFLICT (user_id) DO NOTHING;

    -- 3. Ensure User-Account link
    INSERT INTO users_accounts (id, user_id, account_id, role, created_at)
    SELECT gen_random_uuid(), v_user_id, v_account_id, 'ADMIN', NOW()
    WHERE NOT EXISTS (SELECT 1 FROM users_accounts WHERE user_id = v_user_id AND account_id = v_account_id);

    -- 4. Ensure Features exist
    INSERT INTO features (feature_id, name, key, description, account_id, is_enabled, is_deleted, created_at)
    VALUES 
    (v_feature_ai_id, 'AI Tokens', 'ai_tokens', 'Cost per AI token usage', v_account_id, true, false, NOW()),
    (v_feature_storage_id, 'Storage', 'storage_gb', 'Cost per GB of storage', v_account_id, true, false, NOW())
    ON CONFLICT (feature_id) DO NOTHING;

    -- 5. Ensure Plan exists
    INSERT INTO plans (plan_id, key, name, price_amount, account_id, created_at, interval_months, billing_timing)
    VALUES (v_plan_id, 'pro_plan_scale', 'Pro Plan Scale', 100.00, v_account_id, NOW(), 1, 'IN_ADVANCE')
    ON CONFLICT (plan_id) DO NOTHING;

    -- 6. Ensure Plan Feature Rules exist
    INSERT INTO plan_feature_rules (id, plan_id, feature_id, is_enabled, type, value, created_at)
    SELECT gen_random_uuid(), v_plan_id, v_feature_ai_id, true, 'BASE', '{"model": "usage", "usage_unit_type": "token", "price_per_unit": 0.05, "cost_unit": "TOKENS"}', NOW()
    WHERE NOT EXISTS (SELECT 1 FROM plan_feature_rules WHERE plan_id = v_plan_id AND feature_id = v_feature_ai_id);

    INSERT INTO plan_feature_rules (id, plan_id, feature_id, is_enabled, type, value, created_at)
    SELECT gen_random_uuid(), v_plan_id, v_feature_storage_id, true, 'BASE', '{"model": "usage", "usage_unit_type": "gb", "price_per_unit": 0.50, "cost_unit": "CURRENCY"}', NOW()
    WHERE NOT EXISTS (SELECT 1 FROM plan_feature_rules WHERE plan_id = v_plan_id AND feature_id = v_feature_storage_id);

    -- 7. Loop to create 100 customers
    FOR i IN 1..100 LOOP
        v_customer_id := gen_random_uuid();
        v_first_name := v_first_names[1 + (i % 20)];
        v_last_name := v_last_names[1 + ((i / 20) % 20)];
        v_email := lower(v_first_name) || '.' || lower(v_last_name) || '.' || i || '@scale-test.com';
        
        INSERT INTO customers (customer_id, first_name, last_name, email, account_id, external_client_customer_id, created_at)
        VALUES (v_customer_id, v_first_name, v_last_name, v_email, v_account_id, 'ext_scale_' || i, NOW());
        
        -- 8. Create Active Subscription for each customer
        v_subscription_id := gen_random_uuid();
        INSERT INTO subscriptions (subscription_id, customer_id, plan_id, account_id, is_active, current_period_start, current_period_end, created_at)
        VALUES (v_subscription_id, v_customer_id, v_plan_id, v_account_id, true, NOW() - (random() * INTERVAL '30 days'), NOW() + (random() * INTERVAL '30 days'), NOW());
        
        -- 9. Create 5-15 random events for each customer
        FOR j IN 1..(5 + floor(random() * 11)) LOOP
            DECLARE
                v_is_ai BOOLEAN := random() > 0.3;
                v_feat_id UUID := CASE WHEN v_is_ai THEN v_feature_ai_id ELSE v_feature_storage_id END;
                v_usage NUMERIC := (random() * 50)::numeric(18,4);
                v_rate NUMERIC := CASE WHEN v_is_ai THEN 0.05 ELSE 0.50 END;
                v_cost NUMERIC := (v_usage * v_rate)::numeric(18,2);
            BEGIN
                INSERT INTO events (event_id, account_id, event_idempotency_key, event_name, occurred_at, customer_id, feature_id, usage_units, revenue_amount, event_type, properties, meta, context, created_at)
                VALUES (
                    gen_random_uuid(), 
                    v_account_id, 
                    'scale_ik_' || i || '_' || j, 
                    CASE WHEN v_is_ai THEN 'token_usage' ELSE 'storage_usage' END,
                    NOW() - (random() * INTERVAL '30 days'),
                    v_customer_id,
                    v_feat_id,
                    v_usage,
                    v_cost,
                    CASE WHEN random() > 0.5 THEN 'CLIENT_TRACKED' ELSE 'ENTITLEMENT_CHECKED' END,
                    '{}', '{}', 
                    jsonb_build_object('sys_pricing_model', 'usage', 'sys_captured_unit_price', v_rate), 
                    NOW()
                );
            END;
        END LOOP;
        
        -- 10. Create 1-3 Invoices for each customer
        FOR j IN 1..(1 + floor(random() * 3)) LOOP
            v_invoice_id := gen_random_uuid();
            INSERT INTO invoices (invoice_id, subscription_id, amount, due_date, status, currency, account_id, type, invoice_period_start, invoice_period_end, created_at)
            VALUES (
                v_invoice_id, 
                v_subscription_id, 
                100.00, 
                NOW() + ((random() * 40) - 20) * INTERVAL '1 day',
                CASE 
                    WHEN random() > 0.6 THEN 'PAID'
                    WHEN random() > 0.3 THEN 'DUE'
                    ELSE 'PENDING'
                END,
                'USD', 
                v_account_id, 
                'REGULAR', 
                NOW() - INTERVAL '30 days', 
                NOW(), 
                NOW()
            );
        END LOOP;
    END LOOP;
END $$;
