# End-to-end QA runbook

The full pass that was used to validate the console, billing, and both Stripe
modes. Automated parts live in the crash dummies; the Stripe payment loops are
manual because they end on a real (test-mode) Stripe checkout page.

## 0. Prerequisites

```bash
# Stack up + seeded (from deploy/):
docker compose up -d --build && ./setup.sh    # login test/password, API key printed

# Console (from repo root):
npm install && npm run dev:ui                 # http://localhost:3000

# Stripe webhooks on localhost (only needed for sections 3–4):
stripe listen --api-key sk_test_... \
  --forward-to http://localhost:8080/public/stripe/ingest/webhook/<accountId>
# then swap the CLI's whsec_… into the stored secret:
docker exec deploy-postgres-1 psql -U tanso -d tanso -c \
  "UPDATE external_api_keys SET key_value='whsec_…' WHERE key_type='WEBHOOK_SECRET_SIGNING';"
```

The `<accountId>` is in the JWT `setup.sh` prints (or visible in the console
after login).

## 1. Automated: operator persona (console UI)

```bash
cd ~/crash-dummies/tanso-operator   # or wherever the dummies live
set -a; source .env; set +a
npx playwright test                  # 7 flows, all must pass
```

Covers: plan create → feature attach → activation guards → customer with
Reference ID → subscribe → usage ingest → entitlement by reference →
Events/Overview reconciliation → invoice "Mark as paid" activates the
subscription → Overview customer/model detail sheets open.

## 2. Automated: customer persona (client API)

```bash
cd ~/crash-dummies/acme-chat
npm run dev -- -p 3010               # then drive per its README
```

Covers: chat turns burn credits, balance falls, entitlement pre-flight
returns `creditQuote`, exhaustion → 402 paywall (only when the pool is small).

## 3. Manual: "Tanso handles billing" (PAYMENT_PASS_THROUGH)

1. Settings → Stripe: connect a test secret key, Register webhook. Stripe
   mode unlocks — pick **Tanso handles billing**, Save.
2. Create a paid plan (attach a feature, add a description, set ACTIVE),
   a customer **with a Reference ID**, and a subscription. Subscription shows
   *Inactive*; a DUE invoice appears under Invoices.
3. Invoices → open the DUE invoice → **Copy checkout link** → open it →
   pay with `4242 4242 4242 4242` (any future expiry, any CVC/ZIP).
4. Expect within seconds: invoice **PAID**, subscription **Active**, MRR on
   Overview includes the plan price. (`invoice.paid` arrives via
   `stripe listen`.)
5. Alternative to 3–4: **Mark as paid** in the same dialog records an
   out-of-band payment directly.

## 4. Manual: "Stripe drives billing" (STRIPE_INTEGRATION)

1. Settings → switch Stripe mode to **Stripe drives billing**, Save.
2. Create a paid plan **with a usage-priced feature rule** and activate it —
   this is the shape that used to break checkout. A Stripe product appears
   (`stripe products list`).
3. Subscribe a customer to it (console or API). Instead of an immediate
   subscription, the response carries a **Checkout URL** — the console/API
   returns it and no Tanso subscription exists yet.
4. Open the URL: the page must show **both** line items — the flat base price
   and the metered usage price ("$X due today, then per-unit monthly").
5. Pay with the test card. Expect: redirect to the configured success URL,
   and a new **Active** Tanso subscription auto-created by the
   `customer.subscription.created` webhook, linked in `stripe_subscriptions`.
6. Disconnect Stripe in Settings afterwards — mode must reset to `None`.

## 5. Spot checks while you're in there

- Create a customer with an **empty Reference ID** — the form must warn
  (amber) but not block; entitlement checks for that customer 404 by design.
- Click a customer row and a model row on Overview — detail sheets must open
  with cost/revenue/margin and working "view events" links (Events page
  arrives pre-filtered).
- Toasts must be visible above open side panels (error toasts included —
  try activating a plan without a description).
