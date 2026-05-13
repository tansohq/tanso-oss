-- Enable pgcrypto for bcrypt + random bytes (safe if already installed)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO
$$
    DECLARE
        v_account_id      uuid;
        v_user_id         uuid;
        v_plan_id         uuid;
        v_feature_id      uuid;
        v_customer_id     uuid;
        v_subscription_id uuid;
        v_invoice_id      uuid;
        v_now             timestamp := now();
        v_period_start    timestamp;
        v_period_end      timestamp;
        v_next_start      timestamp;
        v_next_end        timestamp;
        v_plan_count      int;
        v_feature_count   int       := 10;
        tenant_idx        int;
        cust_idx          int;
        plan_idx          int;
        feat_idx          int;
        event_idx         int;
        plan_ids          uuid[];
        feat_ids          uuid[];
        v_billing_timing  varchar(64);
        v_is_usage        boolean;
        v_plan_price      numeric(18, 2);
        v_feature_key     varchar(255);
    BEGIN
        ----------------------------------------------------------------
        -- Create 4 tenants (accounts) with users, api keys, plans, etc.
        ----------------------------------------------------------------
        FOR tenant_idx IN 1..4
            LOOP
            ------------------------------------------------------------
            -- 1) Account (tenant)
            ------------------------------------------------------------
                INSERT INTO accounts (name, created_at, modified_at)
                VALUES (format('Tenant %s', tenant_idx),
                        v_now,
                        v_now)
                RETURNING account_id INTO v_account_id;

                ------------------------------------------------------------
                -- 2) Owner user for this tenant (bcrypt password)
                ------------------------------------------------------------
                INSERT INTO users (username,
                                   password,
                                   first_name,
                                   last_name,
                                   email,
                                   created_at,
                                   modified_at)
                VALUES (format('tenant%s_owner', tenant_idx),
                        crypt('password', gen_salt('bf')),
                        format('Tenant%s', tenant_idx),
                        'Owner',
                        format('tenant%s.owner@example.com', tenant_idx),
                        v_now,
                        v_now)
                RETURNING user_id INTO v_user_id;

                INSERT INTO users_accounts (user_id, account_id, role, created_at, modified_at)
                VALUES (v_user_id, v_account_id, 'OWNER', v_now, v_now);

                ------------------------------------------------------------
                -- 3) Account API key
                ------------------------------------------------------------
                INSERT INTO account_api_keys (account_id, key_type, key_value, is_active, expires_at, created_at,
                                              modified_at)
                VALUES (v_account_id,
                        'SECRET',
                        'sk_test_' || encode(gen_random_bytes(16), 'hex'),
                        TRUE,
                        v_now + INTERVAL '365 days',
                        v_now,
                        v_now);

                ------------------------------------------------------------
                -- 4) Features (10 per tenant)
                ------------------------------------------------------------
                feat_ids := '{}';
                FOR feat_idx IN 1..v_feature_count
                    LOOP
                        INSERT INTO features (name, key, description, account_id, is_enabled, created_at, modified_at,
                                              is_deleted)
                        VALUES (format('Tenant %s Feature %s', tenant_idx, feat_idx),
                                format('tenant%s_feature_%s', tenant_idx, feat_idx),
                                'Seeded feature',
                                v_account_id,
                                TRUE,
                                v_now,
                                v_now,
                                FALSE)
                        RETURNING feature_id INTO v_feature_id;

                        feat_ids := array_append(feat_ids, v_feature_id);
                    END LOOP;

                ------------------------------------------------------------
                -- 5) Plans (3–7 per tenant)
                --    Mix of FLAT (In Advance) and USAGE (In Arrears)
                ------------------------------------------------------------
                plan_ids := '{}';
                v_plan_count := 3 + floor(random() * 5)::int;

                FOR plan_idx IN 1..v_plan_count
                    LOOP
                        -- Alternate models for testing
                        IF plan_idx % 2 = 0 THEN
                            v_is_usage := TRUE;
                            v_billing_timing := 'IN_ARREARS';
                            v_plan_price := 0.00; -- Base price is 0 for usage-only
                        ELSE
                            v_is_usage := FALSE;
                            v_billing_timing := 'IN_ADVANCE';
                            v_plan_price := (50 + 10 * plan_idx)::numeric(18, 2);
                        END IF;

                        INSERT INTO plans (key, name, price_amount, billing_timing,
                                           description, interval_months, created_at, modified_at, account_id)
                        VALUES (format('tenant%s_plan_%s', tenant_idx, plan_idx),
                                format('Tenant %s %s Plan %s', tenant_idx, CASE WHEN v_is_usage THEN 'USAGE' ELSE 'FLAT' END, plan_idx),
                                v_plan_price,
                                v_billing_timing,
                                'Seeded plan',
                                1,
                                v_now,
                                v_now,
                                v_account_id)
                        RETURNING plan_id INTO v_plan_id;

                        plan_ids := array_append(plan_ids, v_plan_id);

                        --------------------------------------------------------
                        -- 6) Attach features to plans with Pricing Rules
                        --------------------------------------------------------
                        FOR feat_idx IN 1..6
                            LOOP
                                IF (feat_idx + plan_idx) % 2 = 0 THEN
                                    v_feature_id := feat_ids[feat_idx];

                                    -- Pricing Rule: $0.05 per unit if Usage-based
                                    INSERT INTO plan_feature_rules (id, plan_id, feature_id, type, is_enabled,
                                                                    created_at, modified_at, value)
                                    VALUES (gen_random_uuid(),
                                            v_plan_id,
                                            v_feature_id,
                                            'BASE',
                                            TRUE,
                                            v_now,
                                            v_now,
                                            CASE
                                                WHEN v_is_usage
                                                    THEN jsonb_build_object(
                                                            'model', 'usage',
                                                            'price_per_unit', 0.05,
                                                            'usage_unit_type', 'api_calls',
                                                            'cost_unit', 'USD'
                                                         )
                                                ELSE NULL
                                                END);
                                END IF;
                            END LOOP;
                    END LOOP;

                ------------------------------------------------------------
                -- 7) Customers (25 per tenant)
                ------------------------------------------------------------
                FOR cust_idx IN 1..25
                    LOOP
                        INSERT INTO customers (external_client_customer_id, created_at, modified_at, first_name,
                                               last_name, email, phone_number, account_id)
                        VALUES (format('tenant%s_cust_%s', tenant_idx, cust_idx),
                                v_now,
                                v_now,
                                format('Cust%s', cust_idx),
                                format('Tenant%s', tenant_idx),
                                format('cust%s.tenant%s@example.com', cust_idx, tenant_idx),
                                format('+1-555-%04s', cust_idx),
                                v_account_id)
                        RETURNING customer_id INTO v_customer_id;

                        plan_idx := ((cust_idx - 1) % v_plan_count) + 1;
                        v_plan_id := plan_ids[plan_idx];

                        -- Set period end in the past to trigger the SubscriptionCycleJob immediately
                        v_period_start := date_trunc('day', v_now) - INTERVAL '1 month';
                        v_period_end := date_trunc('day', v_now);

                        INSERT INTO subscriptions (subscription_id, customer_id, plan_id, account_id, is_active,
                                                   interval_months,
                                                   current_period_start, current_period_end, billing_anchor_day,
                                                   grace_period_days,
                                                   created_at, modified_at)
                        VALUES (gen_random_uuid(), v_customer_id, v_plan_id, v_account_id, TRUE, 1,
                                v_period_start, v_period_end, extract(day FROM v_period_start)::smallint, 3,
                                v_now, v_now)
                        RETURNING subscription_id INTO v_subscription_id;

                        --------------------------------------------------------
                        -- 8) Entitlements & Usage Events
                        --------------------------------------------------------
                        FOR v_feature_id, v_feature_key IN
                            SELECT f.feature_id, f.key
                            FROM features f
                                     JOIN plan_feature_rules pfr ON f.feature_id = pfr.feature_id
                            WHERE pfr.plan_id = v_plan_id
                            LOOP
                                INSERT INTO entitlements (entitlement_id, feature_key, customer_id, subscription_id,
                                                          created_at, modified_at)
                                VALUES (gen_random_uuid(), v_feature_key, v_customer_id, v_subscription_id, v_now,
                                        v_now);

                                -- Insert 5-10 random usage events in the past month for this feature
                                FOR event_idx IN 1..(5 + floor(random() * 5)::int)
                                    LOOP
                                        DECLARE
                                            v_usage numeric := floor(random() * 10) + 1;
                                            v_cost  numeric := v_usage * 0.05; -- Matching the 0.05 in plan_feature_rules above
                                        BEGIN
                                            INSERT INTO events (event_id, account_id, customer_id, feature_id,
                                                                subscription_id,
                                                                event_type, event_name, event_idempotency_key, occurred_at,
                                                                usage_units, revenue_amount, usage_unit_type, revenue_unit,
                                                                properties, meta, context)
                                            VALUES (gen_random_uuid(),
                                                    v_account_id,
                                                    v_customer_id,
                                                    v_feature_id,
                                                    v_subscription_id,
                                                    'ENTITLEMENT_CHECKED',
                                                    v_feature_key,
                                                    encode(digest(
                                                                   format('seed_%s_%s_%s', v_customer_id, v_feature_id, event_idx),
                                                                   'sha256'), 'hex'),
                                                    v_period_start + (random() * (v_period_end - v_period_start)),
                                                    v_usage,
                                                    CASE WHEN v_is_usage THEN v_cost ELSE 0 END,
                                                    'api_calls',
                                                    'CURRENCY',
                                                    jsonb_build_object('isEntitled', true),
                                                    '{}',
                                                    CASE 
                                                        WHEN v_is_usage 
                                                        THEN jsonb_build_object('sys_pricing_model', 'usage', 'sys_captured_unit_price', 0.05)
                                                        ELSE '{}'::jsonb
                                                    END);
                                        END;
                                    END LOOP;
                            END LOOP;

                        --------------------------------------------------------
                        -- 9) Historical Invoice (PENDING)
                        --------------------------------------------------------
                        INSERT INTO invoices (invoice_id, subscription_id, account_id, amount, status, currency,
                                              invoice_period_start, invoice_period_end, due_date, type, created_at,
                                              modified_at)
                        VALUES (gen_random_uuid(), v_subscription_id, v_account_id, 0, 'PENDING', 'USD',
                                v_period_start, v_period_end, v_period_start, 'REGULAR', v_now, v_now);

                    END LOOP;
                ----------------------------------------------------------------
            END LOOP; -- 4 tenants
    END
$$;
