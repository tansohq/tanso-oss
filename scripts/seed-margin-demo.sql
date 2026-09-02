-- Seed a realistic margin story for the admin console.
--
-- The developer demo (seed-developer-demo.sql) is one free plan and one
-- customer, so Overview reads MRR $0.00 with an em-dash under Costs and Avg
-- margin. This adds paid plans, five customers on different margins, ninety
-- days of metered events across four models, three closed billing periods,
-- and the internal-spend side that Feature P&L needs.
--
-- Safe to rerun. Everything it writes is namespaced under the demo UUID
-- prefixes below and removed before reinsert. No other customer is touched.
--
-- Account:  a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467  (from create-test-account.sql)
-- Features: b1......  Plans: b2......  Customers: b3......
-- Subs:     b4......  Entitlements: b5......  Spend units: b6......
-- Vendor:   b7......  Invoices: b8......

BEGIN;

-- ---------------------------------------------------------------------------
-- Reset: drop anything this script created on a previous run.
-- ---------------------------------------------------------------------------

DELETE FROM events
 WHERE account_id = 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467'
   AND event_idempotency_key LIKE 'margin-demo-%';

DELETE FROM invoice_items
 WHERE invoice_id IN (SELECT invoice_id FROM invoices
                       WHERE invoice_id::text LIKE 'b8%');
DELETE FROM invoices WHERE invoice_id::text LIKE 'b8%';

DELETE FROM outcomes WHERE outcome_id::text LIKE 'b6%';
DELETE FROM spend_attribution_rules WHERE spend_attribution_rule_id::text LIKE 'b6%';
DELETE FROM vendor_usage_buckets WHERE vendor_usage_bucket_id::text LIKE 'b7%';
DELETE FROM vendor_invoice_lines
 WHERE vendor_invoice_id IN (SELECT vendor_invoice_id FROM vendor_invoices
                              WHERE vendor_invoice_id::text LIKE 'b7%');
DELETE FROM vendor_invoices WHERE vendor_invoice_id::text LIKE 'b7%';
DELETE FROM vendor_connections WHERE vendor_connection_id::text LIKE 'b7%';
DELETE FROM spend_units WHERE spend_unit_id::text LIKE 'b6%';

DELETE FROM entitlements WHERE entitlement_id::text LIKE 'b5%';
DELETE FROM subscriptions WHERE subscription_id::text LIKE 'b4%';
DELETE FROM plan_feature_rules WHERE id::text LIKE 'b2%';
DELETE FROM customers WHERE customer_id::text LIKE 'b3%';
DELETE FROM plans WHERE plan_id::text LIKE 'b2%';
DELETE FROM features WHERE feature_id::text LIKE 'b1%';

-- ---------------------------------------------------------------------------
-- Features. Three things the demo product sells.
-- ---------------------------------------------------------------------------

INSERT INTO features (feature_id, account_id, name, key, description, is_enabled, is_deleted, metadata, created_at, modified_at)
VALUES
  ('b1111111-1111-4111-8111-111111111111', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467',
   'Document Q&A', 'doc.qa', 'Answer a question against an uploaded document', true, false, '{"demo":"margin"}', NOW() - INTERVAL '120 days', NOW()),
  ('b1111111-1111-4111-8111-222222222222', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467',
   'Contract review', 'contract.review', 'Long-context review of a contract', true, false, '{"demo":"margin"}', NOW() - INTERVAL '120 days', NOW()),
  ('b1111111-1111-4111-8111-333333333333', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467',
   'Bulk classify', 'bulk.classify', 'Cheap high-volume classification', true, false, '{"demo":"margin"}', NOW() - INTERVAL '120 days', NOW());

-- ---------------------------------------------------------------------------
-- Plans. price_amount / interval_months is what the MRR tile sums.
-- ---------------------------------------------------------------------------

INSERT INTO plans (plan_id, account_id, key, name, description, price_amount, interval_months, billing_timing, currency, status, metadata, created_at, modified_at)
VALUES
  ('b2222222-2222-4222-8222-111111111111', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467',
   'starter', 'Starter', 'For a team trying the product on real documents', 149.00, 1, 'IN_ADVANCE', 'USD', 'ACTIVE', '{"demo":"margin"}', NOW() - INTERVAL '120 days', NOW()),
  ('b2222222-2222-4222-8222-222222222222', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467',
   'growth', 'Growth', 'Production usage with room to spike', 899.00, 1, 'IN_ADVANCE', 'USD', 'ACTIVE', '{"demo":"margin"}', NOW() - INTERVAL '120 days', NOW()),
  ('b2222222-2222-4222-8222-333333333333', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467',
   'scale', 'Scale', 'High volume, long context, negotiated', 2400.00, 1, 'IN_ADVANCE', 'USD', 'ACTIVE', '{"demo":"margin"}', NOW() - INTERVAL '120 days', NOW());

-- ---------------------------------------------------------------------------
-- Plan feature rules. The `cost` block is what turns the Costs tile from an
-- em-dash into a number, and the gap between price_per_unit and cost_per_unit
-- is the margin story. Contract review on Scale is deliberately underpriced.
-- ---------------------------------------------------------------------------

INSERT INTO plan_feature_rules (id, plan_id, feature_id, type, value, is_enabled, created_at, modified_at)
VALUES
  -- Starter: healthy margins on cheap work.
  ('b2222222-0000-4000-8000-000000000001', 'b2222222-2222-4222-8222-111111111111', 'b1111111-1111-4111-8111-111111111111', 'BASE',
   '{"pricing":{"model":"usage","price_per_unit":0.05,"usage_unit_type":"requests"},"cost":{"model":"simple","cost_per_unit":0.011}}', true, NOW() - INTERVAL '120 days', NOW()),
  ('b2222222-0000-4000-8000-000000000002', 'b2222222-2222-4222-8222-111111111111', 'b1111111-1111-4111-8111-333333333333', 'BASE',
   '{"pricing":{"model":"usage","price_per_unit":0.004,"usage_unit_type":"requests"},"cost":{"model":"simple","cost_per_unit":0.0004}}', true, NOW() - INTERVAL '120 days', NOW()),

  -- Growth: same shape, slight volume discount on price.
  ('b2222222-0000-4000-8000-000000000003', 'b2222222-2222-4222-8222-222222222222', 'b1111111-1111-4111-8111-111111111111', 'BASE',
   '{"pricing":{"model":"usage","price_per_unit":0.038,"usage_unit_type":"requests"},"cost":{"model":"simple","cost_per_unit":0.011}}', true, NOW() - INTERVAL '120 days', NOW()),
  ('b2222222-0000-4000-8000-000000000004', 'b2222222-2222-4222-8222-222222222222', 'b1111111-1111-4111-8111-222222222222', 'BASE',
   '{"pricing":{"model":"usage","price_per_unit":0.60,"usage_unit_type":"requests"},"cost":{"model":"simple","cost_per_unit":0.42}}', true, NOW() - INTERVAL '120 days', NOW()),
  ('b2222222-0000-4000-8000-000000000005', 'b2222222-2222-4222-8222-222222222222', 'b1111111-1111-4111-8111-333333333333', 'BASE',
   '{"pricing":{"model":"usage","price_per_unit":0.003,"usage_unit_type":"requests"},"cost":{"model":"simple","cost_per_unit":0.0004}}', true, NOW() - INTERVAL '120 days', NOW()),

  -- Scale: contract review priced below what opus costs to serve.
  ('b2222222-0000-4000-8000-000000000006', 'b2222222-2222-4222-8222-333333333333', 'b1111111-1111-4111-8111-111111111111', 'BASE',
   '{"pricing":{"model":"usage","price_per_unit":0.028,"usage_unit_type":"requests"},"cost":{"model":"simple","cost_per_unit":0.011}}', true, NOW() - INTERVAL '120 days', NOW()),
  ('b2222222-0000-4000-8000-000000000007', 'b2222222-2222-4222-8222-333333333333', 'b1111111-1111-4111-8111-222222222222', 'BASE',
   '{"pricing":{"model":"usage","price_per_unit":0.45,"usage_unit_type":"requests"},"cost":{"model":"simple","cost_per_unit":0.78}}', true, NOW() - INTERVAL '120 days', NOW()),
  ('b2222222-0000-4000-8000-000000000008', 'b2222222-2222-4222-8222-333333333333', 'b1111111-1111-4111-8111-333333333333', 'BASE',
   '{"pricing":{"model":"usage","price_per_unit":0.002,"usage_unit_type":"requests"},"cost":{"model":"simple","cost_per_unit":0.0004}}', true, NOW() - INTERVAL '120 days', NOW());

-- ---------------------------------------------------------------------------
-- Customers.
-- ---------------------------------------------------------------------------

INSERT INTO customers (customer_id, account_id, external_client_customer_id, first_name, last_name, email, source, created_at, modified_at)
VALUES
  ('b3333333-3333-4333-8333-111111111111', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'northwind', 'Northwind', 'Legal', 'ops@northwind.example', 'MANUAL', NOW() - INTERVAL '110 days', NOW()),
  ('b3333333-3333-4333-8333-222222222222', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'contoso',   'Contoso',   'Research', 'platform@contoso.example', 'MANUAL', NOW() - INTERVAL '95 days', NOW()),
  ('b3333333-3333-4333-8333-333333333333', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'fabrikam',  'Fabrikam',  'Support', 'eng@fabrikam.example', 'MANUAL', NOW() - INTERVAL '80 days', NOW()),
  ('b3333333-3333-4333-8333-444444444444', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'tailwind',  'Tailwind',  'Ops', 'dev@tailwind.example', 'MANUAL', NOW() - INTERVAL '60 days', NOW()),
  ('b3333333-3333-4333-8333-555555555555', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'globex',    'Globex',    'Data', 'api@globex.example', 'MANUAL', NOW() - INTERVAL '45 days', NOW());

-- ---------------------------------------------------------------------------
-- Subscriptions. Period is the current month so usage lands inside the window
-- the Overview query uses. Fabrikam is cancelling, which drives churn risk.
-- ---------------------------------------------------------------------------

INSERT INTO subscriptions (subscription_id, account_id, customer_id, plan_id, is_active, interval_months, grace_period_days,
                           current_period_start, current_period_end, billing_anchor_day, cancel_mode, cancel_effective_at, created_at, modified_at)
VALUES
  ('b4444444-4444-4444-8444-111111111111', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'b3333333-3333-4333-8333-111111111111', 'b2222222-2222-4222-8222-333333333333',
   true, 1, 3, date_trunc('day', NOW()) - INTERVAL '24 days', date_trunc('day', NOW()) + INTERVAL '6 days', 1, NULL, NULL, NOW() - INTERVAL '110 days', NOW()),
  ('b4444444-4444-4444-8444-222222222222', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'b3333333-3333-4333-8333-222222222222', 'b2222222-2222-4222-8222-222222222222',
   true, 1, 3, date_trunc('day', NOW()) - INTERVAL '24 days', date_trunc('day', NOW()) + INTERVAL '6 days', 1, NULL, NULL, NOW() - INTERVAL '95 days', NOW()),
  ('b4444444-4444-4444-8444-333333333333', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'b3333333-3333-4333-8333-333333333333', 'b2222222-2222-4222-8222-222222222222',
   true, 1, 3, date_trunc('day', NOW()) - INTERVAL '24 days', date_trunc('day', NOW()) + INTERVAL '6 days', 1, 'END_OF_PERIOD', date_trunc('day', NOW()) + INTERVAL '6 days', NOW() - INTERVAL '80 days', NOW()),
  ('b4444444-4444-4444-8444-444444444444', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'b3333333-3333-4333-8333-444444444444', 'b2222222-2222-4222-8222-111111111111',
   true, 1, 3, date_trunc('day', NOW()) - INTERVAL '24 days', date_trunc('day', NOW()) + INTERVAL '6 days', 1, NULL, NULL, NOW() - INTERVAL '60 days', NOW()),
  ('b4444444-4444-4444-8444-555555555555', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'b3333333-3333-4333-8333-555555555555', 'b2222222-2222-4222-8222-111111111111',
   true, 1, 3, date_trunc('day', NOW()) - INTERVAL '24 days', date_trunc('day', NOW()) + INTERVAL '6 days', 1, NULL, NULL, NOW() - INTERVAL '45 days', NOW());

-- ---------------------------------------------------------------------------
-- Entitlements. Churn scoring reads last_accessed on these.
-- ---------------------------------------------------------------------------

INSERT INTO entitlements (entitlement_id, feature_key, customer_id, subscription_id, last_accessed, created_at, modified_at)
VALUES
  ('b5555555-5555-4555-8555-100000000001', 'doc.qa',          'b3333333-3333-4333-8333-111111111111', 'b4444444-4444-4444-8444-111111111111', NOW() - INTERVAL '1 day',  NOW() - INTERVAL '110 days', NOW()),
  ('b5555555-5555-4555-8555-100000000002', 'contract.review', 'b3333333-3333-4333-8333-111111111111', 'b4444444-4444-4444-8444-111111111111', NOW() - INTERVAL '1 day',  NOW() - INTERVAL '110 days', NOW()),
  ('b5555555-5555-4555-8555-100000000003', 'bulk.classify',   'b3333333-3333-4333-8333-111111111111', 'b4444444-4444-4444-8444-111111111111', NOW() - INTERVAL '2 days', NOW() - INTERVAL '110 days', NOW()),
  ('b5555555-5555-4555-8555-200000000001', 'doc.qa',          'b3333333-3333-4333-8333-222222222222', 'b4444444-4444-4444-8444-222222222222', NOW() - INTERVAL '1 day',  NOW() - INTERVAL '95 days', NOW()),
  ('b5555555-5555-4555-8555-200000000002', 'contract.review', 'b3333333-3333-4333-8333-222222222222', 'b4444444-4444-4444-8444-222222222222', NOW() - INTERVAL '3 days', NOW() - INTERVAL '95 days', NOW()),
  ('b5555555-5555-4555-8555-200000000003', 'bulk.classify',   'b3333333-3333-4333-8333-222222222222', 'b4444444-4444-4444-8444-222222222222', NOW() - INTERVAL '1 day',  NOW() - INTERVAL '95 days', NOW()),
  ('b5555555-5555-4555-8555-300000000001', 'doc.qa',          'b3333333-3333-4333-8333-333333333333', 'b4444444-4444-4444-8444-333333333333', NOW() - INTERVAL '19 days', NOW() - INTERVAL '80 days', NOW()),
  ('b5555555-5555-4555-8555-300000000002', 'contract.review', 'b3333333-3333-4333-8333-333333333333', 'b4444444-4444-4444-8444-333333333333', NOW() - INTERVAL '24 days', NOW() - INTERVAL '80 days', NOW()),
  ('b5555555-5555-4555-8555-400000000001', 'doc.qa',          'b3333333-3333-4333-8333-444444444444', 'b4444444-4444-4444-8444-444444444444', NOW() - INTERVAL '1 day',  NOW() - INTERVAL '60 days', NOW()),
  ('b5555555-5555-4555-8555-400000000002', 'bulk.classify',   'b3333333-3333-4333-8333-444444444444', 'b4444444-4444-4444-8444-444444444444', NOW() - INTERVAL '1 day',  NOW() - INTERVAL '60 days', NOW()),
  ('b5555555-5555-4555-8555-500000000001', 'doc.qa',          'b3333333-3333-4333-8333-555555555555', 'b4444444-4444-4444-8444-555555555555', NOW() - INTERVAL '2 days', NOW() - INTERVAL '45 days', NOW()),
  ('b5555555-5555-4555-8555-500000000002', 'bulk.classify',   'b3333333-3333-4333-8333-555555555555', 'b4444444-4444-4444-8444-555555555555', NOW() - INTERVAL '1 day',  NOW() - INTERVAL '45 days', NOW());

-- ---------------------------------------------------------------------------
-- Events. Ninety days, one row per customer / feature / model / day, with the
-- cost of serving next to what was billed. Volumes and per-request economics
-- differ per customer so the margin column is not uniform.
--
-- Columns the console reads: usage_units (must be > 0), cost_amount,
-- revenue_amount, model, model_provider, feature_id, customer_id, event_type,
-- and units left as CURRENCY so Feature P&L includes them.
-- ---------------------------------------------------------------------------

INSERT INTO events (
    event_id, account_id, event_idempotency_key, event_name, event_type, occurred_at,
    customer_id, subscription_id, feature_id, usage_units, usage_unit_type,
    cost_amount, cost_unit, revenue_amount, revenue_unit,
    model, model_provider, input_tokens, output_tokens,
    customer_is_native, feature_is_native, subscription_is_native,
    properties, created_at, modified_at
)
SELECT
    gen_random_uuid(),
    'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467',
    'margin-demo-' || m.ref || '-' || m.fkey || '-' || m.model || '-' || d.day_offset,
    m.fkey,
    'CLIENT_TRACKED',
    date_trunc('day', NOW()) - (d.day_offset || ' days')::interval + INTERVAL '9 hours',
    m.customer_id,
    m.subscription_id,
    m.feature_id,
    units.n,
    'requests',
    ROUND(units.n * m.cost_per_unit, 6),
    'CURRENCY',
    ROUND(units.n * m.price_per_unit, 2),
    'CURRENCY',
    m.model,
    m.provider,
    ROUND(units.n * m.in_tok),
    ROUND(units.n * m.out_tok),
    true, true, true,
    jsonb_build_object('demo', 'margin'),
    NOW(), NOW()
FROM
    (VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9),(10),(11),(12),(13),(14),
            (15),(16),(17),(18),(19),(20),(21),(22),(23),(24),
            (27),(30),(33),(36),(39),(42),(45),(48),(51),(54),(57),
            (60),(63),(66),(69),(72),(75),(78),(81),(84),(87)) AS d(day_offset)
CROSS JOIN (VALUES
    -- customer, subscription, ref, feature, key, model, provider,
    -- units/day, cost/unit, price/unit, in_tok/unit, out_tok/unit
    --
    -- Northwind, Scale $2,400. Runs contract review at volume on opus, and
    -- Scale prices that feature below what it costs to serve. Underwater.
    ('b3333333-3333-4333-8333-111111111111'::uuid,'b4444444-4444-4444-8444-111111111111'::uuid,'northwind','b1111111-1111-4111-8111-111111111111'::uuid,'doc.qa','claude-sonnet-4-5','anthropic',240,0.011,0.028,2600,420),
    ('b3333333-3333-4333-8333-111111111111'::uuid,'b4444444-4444-4444-8444-111111111111'::uuid,'northwind','b1111111-1111-4111-8111-111111111111'::uuid,'doc.qa','gpt-4o','openai',60,0.009,0.028,2100,380),
    ('b3333333-3333-4333-8333-111111111111'::uuid,'b4444444-4444-4444-8444-111111111111'::uuid,'northwind','b1111111-1111-4111-8111-222222222222'::uuid,'contract.review','claude-opus-4','anthropic',150,0.780,0.450,42000,3100),
    ('b3333333-3333-4333-8333-111111111111'::uuid,'b4444444-4444-4444-8444-111111111111'::uuid,'northwind','b1111111-1111-4111-8111-333333333333'::uuid,'bulk.classify','gpt-4o-mini','openai',1800,0.0004,0.002,380,40),

    -- Contoso, Growth $899. Mostly cheap classification. Healthy.
    ('b3333333-3333-4333-8333-222222222222'::uuid,'b4444444-4444-4444-8444-222222222222'::uuid,'contoso','b1111111-1111-4111-8111-111111111111'::uuid,'doc.qa','claude-sonnet-4-5','anthropic',260,0.011,0.038,2600,420),
    ('b3333333-3333-4333-8333-222222222222'::uuid,'b4444444-4444-4444-8444-222222222222'::uuid,'contoso','b1111111-1111-4111-8111-111111111111'::uuid,'doc.qa','gpt-4o','openai',40,0.009,0.038,2100,380),
    ('b3333333-3333-4333-8333-222222222222'::uuid,'b4444444-4444-4444-8444-222222222222'::uuid,'contoso','b1111111-1111-4111-8111-222222222222'::uuid,'contract.review','claude-opus-4','anthropic',12,0.420,0.600,42000,3100),
    ('b3333333-3333-4333-8333-222222222222'::uuid,'b4444444-4444-4444-8444-222222222222'::uuid,'contoso','b1111111-1111-4111-8111-333333333333'::uuid,'bulk.classify','gpt-4o-mini','openai',5200,0.0004,0.003,380,40),

    -- Fabrikam, Growth $899. Contract-heavy on a plan that was not sized for
    -- it, and already cancelling. At risk.
    ('b3333333-3333-4333-8333-333333333333'::uuid,'b4444444-4444-4444-8444-333333333333'::uuid,'fabrikam','b1111111-1111-4111-8111-111111111111'::uuid,'doc.qa','claude-sonnet-4-5','anthropic',90,0.011,0.038,2600,420),
    ('b3333333-3333-4333-8333-333333333333'::uuid,'b4444444-4444-4444-8444-333333333333'::uuid,'fabrikam','b1111111-1111-4111-8111-222222222222'::uuid,'contract.review','claude-opus-4','anthropic',95,0.420,0.600,42000,3100),
    ('b3333333-3333-4333-8333-333333333333'::uuid,'b4444444-4444-4444-8444-333333333333'::uuid,'fabrikam','b1111111-1111-4111-8111-333333333333'::uuid,'bulk.classify','gpt-4o-mini','openai',400,0.0004,0.003,380,40),

    -- Tailwind, Starter $149. Small and clean.
    ('b3333333-3333-4333-8333-444444444444'::uuid,'b4444444-4444-4444-8444-444444444444'::uuid,'tailwind','b1111111-1111-4111-8111-111111111111'::uuid,'doc.qa','claude-sonnet-4-5','anthropic',60,0.011,0.050,2600,420),
    ('b3333333-3333-4333-8333-444444444444'::uuid,'b4444444-4444-4444-8444-444444444444'::uuid,'tailwind','b1111111-1111-4111-8111-333333333333'::uuid,'bulk.classify','gpt-4o-mini','openai',1600,0.0004,0.004,380,40),

    -- Globex, Starter $149. Newest, growing.
    ('b3333333-3333-4333-8333-555555555555'::uuid,'b4444444-4444-4444-8444-555555555555'::uuid,'globex','b1111111-1111-4111-8111-111111111111'::uuid,'doc.qa','claude-sonnet-4-5','anthropic',85,0.011,0.050,2600,420),
    ('b3333333-3333-4333-8333-555555555555'::uuid,'b4444444-4444-4444-8444-555555555555'::uuid,'globex','b1111111-1111-4111-8111-333333333333'::uuid,'bulk.classify','gpt-4o-mini','openai',2400,0.0004,0.004,380,40)
) AS m(customer_id, subscription_id, ref, feature_id, fkey, model, provider,
       units_per_day, cost_per_unit, price_per_unit, in_tok, out_tok)
-- Day-to-day variation so the events table does not read as copy-pasted rows.
CROSS JOIN LATERAL (
    SELECT GREATEST(1, ROUND(m.units_per_day * (0.72 + 0.56 * ((d.day_offset * 37 + LENGTH(m.ref) * 13) % 100) / 100.0))) AS n
) AS units;

-- ---------------------------------------------------------------------------
-- Invoices for the three closed months, so the revenue bridge has history.
-- The description prefixes are what split base revenue from usage revenue.
-- ---------------------------------------------------------------------------

INSERT INTO invoices (invoice_id, account_id, subscription_id, amount, status, currency, type,
                      invoice_period_start, invoice_period_end, due_date, created_at, modified_at)
SELECT
    ('b8000000-0000-4000-8000-' || LPAD((s.idx * 10 + m.months_ago)::text, 12, '0'))::uuid,
    'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467',
    s.subscription_id,
    ROUND(s.base + s.usage * (1.15 - 0.1 * m.months_ago), 2),
    'PAID',
    'USD',
    'STANDARD',
    date_trunc('month', NOW()) - (m.months_ago || ' months')::interval,
    date_trunc('month', NOW()) - ((m.months_ago - 1) || ' months')::interval,
    date_trunc('month', NOW()) - ((m.months_ago - 1) || ' months')::interval,
    NOW(), NOW()
FROM (VALUES (1),(2),(3)) AS m(months_ago)
CROSS JOIN (VALUES
    (1, 'b4444444-4444-4444-8444-111111111111'::uuid, 2400.00, 1180.00),
    (2, 'b4444444-4444-4444-8444-222222222222'::uuid,  899.00,  640.00),
    (3, 'b4444444-4444-4444-8444-333333333333'::uuid,  899.00,  210.00),
    (4, 'b4444444-4444-4444-8444-444444444444'::uuid,  149.00,   95.00),
    (5, 'b4444444-4444-4444-8444-555555555555'::uuid,  149.00,  120.00)
) AS s(idx, subscription_id, base, usage);

INSERT INTO invoice_items (invoice_item_id, account_id, invoice_id, charge_amount, description, created_at, modified_at)
SELECT gen_random_uuid(), i.account_id, i.invoice_id,
       ROUND(p.base, 2),
       'Plan base price: ' || p.plan_name,
       NOW(), NOW()
FROM invoices i
JOIN (VALUES
    ('b4444444-4444-4444-8444-111111111111'::uuid, 2400.00, 'Scale'),
    ('b4444444-4444-4444-8444-222222222222'::uuid,  899.00, 'Growth'),
    ('b4444444-4444-4444-8444-333333333333'::uuid,  899.00, 'Growth'),
    ('b4444444-4444-4444-8444-444444444444'::uuid,  149.00, 'Starter'),
    ('b4444444-4444-4444-8444-555555555555'::uuid,  149.00, 'Starter')
) AS p(subscription_id, base, plan_name) ON p.subscription_id = i.subscription_id
WHERE i.invoice_id::text LIKE 'b8%';

INSERT INTO invoice_items (invoice_item_id, account_id, invoice_id, charge_amount, description, created_at, modified_at)
SELECT gen_random_uuid(), i.account_id, i.invoice_id,
       ROUND(i.amount - p.base, 2),
       'Usage for ' || TO_CHAR(i.invoice_period_start, 'Mon YYYY'),
       NOW(), NOW()
FROM invoices i
JOIN (VALUES
    ('b4444444-4444-4444-8444-111111111111'::uuid, 2400.00),
    ('b4444444-4444-4444-8444-222222222222'::uuid,  899.00),
    ('b4444444-4444-4444-8444-333333333333'::uuid,  899.00),
    ('b4444444-4444-4444-8444-444444444444'::uuid,  149.00),
    ('b4444444-4444-4444-8444-555555555555'::uuid,  149.00)
) AS p(subscription_id, base) ON p.subscription_id = i.subscription_id
WHERE i.invoice_id::text LIKE 'b8%'
  AND i.amount - p.base > 0;

-- ---------------------------------------------------------------------------
-- Internal spend: the AI this team buys. Two vendor connections, projects
-- linked to the features they shipped, and attribution rules that put each
-- vendor bucket on a project.
--
-- admin_key is a placeholder string. These connections exist so the console
-- has rows to show; they will not sync until a real key is entered.
-- ---------------------------------------------------------------------------

INSERT INTO vendor_connections (vendor_connection_id, account_id, provider, label, admin_key, key_hint, status, scope, last_synced_at, created_at, modified_at)
VALUES
  ('b7777777-7777-4777-8777-111111111111', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'ANTHROPIC', 'Anthropic (demo)', 'seeded-placeholder-not-a-key', '…demo', 'ACTIVE', 'ORGANIZATION', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '90 days', NOW()),
  ('b7777777-7777-4777-8777-222222222222', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'OPENAI',    'OpenAI (demo)',    'seeded-placeholder-not-a-key', '…demo', 'ACTIVE', 'ORGANIZATION', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '90 days', NOW());

-- Projects carry feature_id, which is the only link between build cost and
-- the feature that revenue lands on. Docs bot has no feature on purpose so
-- the unlinked-projects row is not empty.
-- Platform is the parent team, so project spend rolls up into one number.
INSERT INTO spend_units (spend_unit_id, account_id, type, name, feature_id, parent_id, created_at)
VALUES
  ('b6666666-6666-4666-8666-aaaaaaaaaaaa', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'TEAM',    'Platform',        NULL,                                   NULL,                                   NOW() - INTERVAL '120 days'),
  ('b6666666-6666-4666-8666-111111111111', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'PROJECT', 'Document Q&A',    'b1111111-1111-4111-8111-111111111111', 'b6666666-6666-4666-8666-aaaaaaaaaaaa', NOW() - INTERVAL '120 days'),
  ('b6666666-6666-4666-8666-222222222222', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'PROJECT', 'Contract review', 'b1111111-1111-4111-8111-222222222222', 'b6666666-6666-4666-8666-aaaaaaaaaaaa', NOW() - INTERVAL '120 days'),
  ('b6666666-6666-4666-8666-333333333333', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'PROJECT', 'Bulk classify',   'b1111111-1111-4111-8111-333333333333', 'b6666666-6666-4666-8666-aaaaaaaaaaaa', NOW() - INTERVAL '120 days'),
  ('b6666666-6666-4666-8666-444444444444', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'PROJECT', 'Docs bot',        NULL,                                   'b6666666-6666-4666-8666-aaaaaaaaaaaa', NOW() - INTERVAL '60 days');

INSERT INTO spend_attribution_rules (spend_attribution_rule_id, account_id, spend_unit_id, provider, match_kind, match_value, priority, created_at)
VALUES
  ('b6666666-0000-4000-8000-000000000001', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'b6666666-6666-4666-8666-111111111111', 'ANTHROPIC', 'WORKSPACE_ID', 'ws_docqa',    10, NOW()),
  ('b6666666-0000-4000-8000-000000000002', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'b6666666-6666-4666-8666-222222222222', 'ANTHROPIC', 'WORKSPACE_ID', 'ws_contract', 10, NOW()),
  ('b6666666-0000-4000-8000-000000000003', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'b6666666-6666-4666-8666-333333333333', 'OPENAI',    'WORKSPACE_ID', 'ws_classify', 10, NOW()),
  ('b6666666-0000-4000-8000-000000000004', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'b6666666-6666-4666-8666-444444444444', 'ANTHROPIC', 'WORKSPACE_ID', 'ws_docsbot',  10, NOW());

-- Vendor usage buckets. Priced from model_pricing at read time, so only the
-- token counts matter here. One row per workspace / model / day.
INSERT INTO vendor_usage_buckets (
    vendor_usage_bucket_id, account_id, vendor_connection_id, provider, source,
    bucket_start, bucket_end, model, workspace_id,
    uncached_input_tokens, cache_read_tokens, cache_creation_tokens, output_tokens, requests,
    currency, created_at
)
SELECT
    ('b7000000-0000-4000-8000-' || LPAD((w.idx * 100 + d.day_offset)::text, 12, '0'))::uuid,
    'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467',
    w.connection_id,
    w.provider,
    'USAGE_API',
    date_trunc('day', NOW()) - (d.day_offset || ' days')::interval,
    date_trunc('day', NOW()) - ((d.day_offset - 1) || ' days')::interval,
    w.model,
    w.workspace_id,
    ROUND(w.in_tok * (0.7 + 0.6 * ((d.day_offset * 53 % 100) / 100.0))),
    ROUND(w.in_tok * 0.35),
    ROUND(w.in_tok * 0.08),
    ROUND(w.out_tok * (0.7 + 0.6 * ((d.day_offset * 53 % 100) / 100.0))),
    w.reqs,
    'USD',
    NOW()
FROM generate_series(1, 28) AS d(day_offset)
CROSS JOIN (VALUES
    (1, 'b7777777-7777-4777-8777-111111111111'::uuid, 'ANTHROPIC', 'claude-sonnet-4-5', 'ws_docqa',    1050000, 140000, 210),
    (2, 'b7777777-7777-4777-8777-111111111111'::uuid, 'ANTHROPIC', 'claude-opus-4',     'ws_contract',  210000,  31000,  26),
    (3, 'b7777777-7777-4777-8777-222222222222'::uuid, 'OPENAI',    'gpt-4o',            'ws_classify',  560000,  74000, 130),
    (4, 'b7777777-7777-4777-8777-111111111111'::uuid, 'ANTHROPIC', 'claude-sonnet-4-5', 'ws_docsbot',   180000,  28000,  34)
) AS w(idx, connection_id, provider, model, workspace_id, in_tok, out_tok, reqs);

-- The vendor's own cost report for the same days. Tanso prices tokens from the
-- price book; the vendor reports its own number. They drift slightly, which is
-- the point of the Reconcile page.
INSERT INTO vendor_usage_buckets (
    vendor_usage_bucket_id, account_id, vendor_connection_id, provider, source,
    bucket_start, bucket_end, model, workspace_id, description,
    uncached_input_tokens, cache_read_tokens, cache_creation_tokens, output_tokens, requests,
    vendor_cost_cents, currency, created_at
)
SELECT
    ('b7100000-0000-4000-8000-' || LPAD((w.idx * 100 + d.day_offset)::text, 12, '0'))::uuid,
    'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467',
    w.connection_id,
    w.provider,
    'COST_API',
    date_trunc('day', NOW()) - (d.day_offset || ' days')::interval,
    date_trunc('day', NOW()) - ((d.day_offset - 1) || ' days')::interval,
    NULL,
    w.workspace_id,
    w.line_label,
    0, 0, 0, 0, 0,
    ROUND(w.cents_per_day * (0.82 + 0.36 * ((d.day_offset * 29 % 100) / 100.0)), 2),
    'USD',
    NOW()
FROM generate_series(1, 28) AS d(day_offset)
CROSS JOIN (VALUES
    (1, 'b7777777-7777-4777-8777-111111111111'::uuid, 'ANTHROPIC', 'ws_docqa',    'Claude Sonnet 4.5 tokens', 570),
    (2, 'b7777777-7777-4777-8777-111111111111'::uuid, 'ANTHROPIC', 'ws_contract', 'Claude Opus 4 tokens',     640),
    (3, 'b7777777-7777-4777-8777-222222222222'::uuid, 'OPENAI',    'ws_classify', 'gpt-4o tokens',            240),
    (4, 'b7777777-7777-4777-8777-111111111111'::uuid, 'ANTHROPIC', 'ws_docsbot',  'Claude Sonnet 4.5 tokens',  95)
) AS w(idx, connection_id, provider, workspace_id, line_label, cents_per_day);

-- Last month's vendor invoices, so Reconcile has all three numbers to compare.
INSERT INTO vendor_invoices (vendor_invoice_id, account_id, provider, period_start, period_end, currency, total_cents, imported_from, created_at)
VALUES
  ('b7200000-0000-4000-8000-000000000001', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'ANTHROPIC',
   (date_trunc('month', NOW()) - INTERVAL '1 month')::date, (date_trunc('month', NOW()) - INTERVAL '1 day')::date,
   'USD', 41880, 'anthropic-invoice.csv', NOW()),
  ('b7200000-0000-4000-8000-000000000002', 'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467', 'OPENAI',
   (date_trunc('month', NOW()) - INTERVAL '1 month')::date, (date_trunc('month', NOW()) - INTERVAL '1 day')::date,
   'USD', 7420, 'openai-invoice.csv', NOW());

INSERT INTO vendor_invoice_lines (vendor_invoice_line_id, vendor_invoice_id, description, kind, model, quantity, amount_cents)
VALUES
  (gen_random_uuid(), 'b7200000-0000-4000-8000-000000000001', 'Claude Sonnet 4.5 input tokens',  'TOKENS', 'claude-sonnet-4-5', 29400000, 8820),
  (gen_random_uuid(), 'b7200000-0000-4000-8000-000000000001', 'Claude Sonnet 4.5 output tokens', 'TOKENS', 'claude-sonnet-4-5',  3920000, 5880),
  (gen_random_uuid(), 'b7200000-0000-4000-8000-000000000001', 'Claude Opus 4 input tokens',      'TOKENS', 'claude-opus-4',       5880000, 8820),
  (gen_random_uuid(), 'b7200000-0000-4000-8000-000000000001', 'Claude Opus 4 output tokens',     'TOKENS', 'claude-opus-4',        868000, 6510),
  (gen_random_uuid(), 'b7200000-0000-4000-8000-000000000001', 'Prompt caching',                  'TOKENS', 'claude-sonnet-4-5',  9800000, 11850),
  (gen_random_uuid(), 'b7200000-0000-4000-8000-000000000002', 'gpt-4o input tokens',             'TOKENS', 'gpt-4o',            15680000, 3920),
  (gen_random_uuid(), 'b7200000-0000-4000-8000-000000000002', 'gpt-4o output tokens',            'TOKENS', 'gpt-4o',             2072000, 2072),
  (gen_random_uuid(), 'b7200000-0000-4000-8000-000000000002', 'gpt-4o-mini tokens',              'TOKENS', 'gpt-4o-mini',       58000000, 1428);

-- Outcomes: what the build spend bought. Drives cost per merged PR.
INSERT INTO outcomes (outcome_id, account_id, source, kind, external_id, title, spend_unit_id, occurred_at, created_at, ai_assisted, ai_tool)
SELECT
    ('b6000000-0000-4000-8000-' || LPAD((u.idx * 100 + n)::text, 12, '0'))::uuid,
    'a1f0ad9d-8d12-4d2b-95b4-e8964fd4d467',
    'GITHUB',
    CASE WHEN n % 4 = 0 THEN 'ISSUE_DONE' ELSE 'PR_MERGED' END,
    u.repo || '#' || (1400 + u.idx * 50 + n),
    u.title_prefix || ' ' || n,
    u.spend_unit_id,
    NOW() - ((n * 2) || ' days')::interval,
    NOW(),
    true,
    'claude-code'
FROM generate_series(1, 9) AS n
CROSS JOIN (VALUES
    (1, 'b6666666-6666-4666-8666-111111111111'::uuid, 'acme/docqa',    'Improve retrieval for scanned PDFs'),
    (2, 'b6666666-6666-4666-8666-222222222222'::uuid, 'acme/contract', 'Clause extraction pass'),
    (3, 'b6666666-6666-4666-8666-333333333333'::uuid, 'acme/classify', 'Batch throughput'),
    (4, 'b6666666-6666-4666-8666-444444444444'::uuid, 'acme/docsbot',  'Answer freshness')
) AS u(idx, spend_unit_id, repo, title_prefix);

COMMIT;
