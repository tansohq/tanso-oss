# Stripe Integration

Tanso integrates with Stripe so you manage your catalog, customers, entitlements, and usage analytics in one place while Stripe handles payment collection. Connect Stripe once and Tanso handles the rest — webhook registration, catalog sync, and entitlement activation on payment.

## Connect Stripe (Recommended)

This is the standard setup for teams going to production.

### How It Works

- You manage your catalog (plans, features), customers, entitlements, usage, and analytics in Tanso
- Tanso pushes catalog and customer data to Stripe automatically
- Stripe generates invoices, collects payment, and handles dunning
- Stripe sends billing events back to Tanso via webhook
- One active subscription per customer (constraint of Stripe's meter architecture)

### Setup

1. Go to **Settings > Integrations** and click **Connect Stripe**
2. Paste your Stripe secret key:
   - `sk_test_...` for sandbox
   - `sk_live_...` for production
   - Find it in Stripe Dashboard > Developers > API keys
3. Click **Connect** — Tanso auto-registers a webhook endpoint in your Stripe account. You do not need to configure webhooks manually.
4. If you have existing Stripe data (products, customers, subscriptions), Tanso detects it and offers an import flow
5. Set checkout redirect URLs under **Settings > Integrations**:
   - **Success URL** — where customers land after payment (e.g., `/checkout/success`)
   - **Cancel URL** — where customers land if they abandon checkout (e.g., `/pricing`)

### Responsibility Matrix

| Domain | Source of Truth | Direction |
|---|---|---|
| Products/Prices (Plans) | Tanso | Tanso → Stripe |
| Customers | Tanso | Tanso → Stripe |
| Subscriptions (config) | Tanso | Tanso → Stripe |
| Subscription billing | Stripe | Stripe → Tanso (webhooks) |
| Invoices | Stripe | Stripe → Tanso (mirrored) |
| Entitlements | Tanso | — |
| Usage tracking | Tanso | Tanso → Stripe Meters |
| Credits | Tanso | — |

### Stripe Checkout Sessions

Once Stripe is connected, your backend requests a hosted Checkout URL for a specific invoice and redirects your customer to complete payment. Tanso processes the Stripe webhook — entitlements activate automatically once payment clears.

```
POST /api/v1/client/billing/invoices/{invoiceId}/stripe/checkout
```

**Authentication:** `X-API-Key: <your-api-key>`

**Important:** The path parameter is `invoiceId`, not a subscription ID. A deprecated form of this endpoint used the subscription ID and now returns HTTP 403. Retrieve the invoice ID from the customer's invoice list first.

**curl example:**

```bash
curl -X POST https://api.tanso.dev/api/v1/client/billing/invoices/inv_a1b2c3d4-e5f6-7890-abcd-ef1234567890/stripe/checkout \
  -H "X-API-Key: sk_live_tanso_abc123"
```

**Response (success):**

```json
{
  "success": true,
  "data": {
    "url": "https://checkout.stripe.com/pay/cs_live_a1B2c3D4e5F6..."
  }
}
```

**Response (Stripe not enabled):**

```json
{
  "success": false,
  "error": {
    "message": "Stripe is not enabled at the account"
  }
}
```

**Response (invoice not found):**

```json
{
  "success": false,
  "error": {
    "message": "Invoice not found"
  }
}
```

Redirect your customer to `data.url`. Stripe handles PCI compliance, card processing, and receipts.

**Node.js example:**

```js
const response = await fetch(
  `https://api.tanso.dev/api/v1/client/billing/invoices/${invoiceId}/stripe/checkout`,
  {
    method: 'POST',
    headers: { 'X-API-Key': process.env.TANSO_API_KEY },
  }
);

const { success, data, error } = await response.json();

if (!success) {
  throw new Error(error.message);
}

// Redirect customer
res.redirect(data.url);
```

### Re-importing Stripe Data

Click **Import from Stripe** in Settings > Integrations at any time. Tanso scans for new products, customers, and subscriptions. Existing records are not duplicated.

---

## Alternative: Tanso Handles Billing

For teams that want Tanso to drive all billing logic while Stripe only collects payment.

- Tanso generates invoices at billing cycle boundaries
- Your backend requests a Stripe Checkout session via the API (same endpoint as above)
- The customer pays on Stripe's hosted page
- Stripe webhook confirms payment → Tanso marks the invoice paid and activates entitlements
- Webhook is auto-registered when you connect Stripe — no manual Stripe dashboard config needed

**When to use:** you need full control over invoice timing and billing logic, or you need custom invoice types beyond what Stripe generates natively.

---

## Other Options

### No Payment Processor

The default mode. Tanso handles all billing logic without Stripe. Mark invoices paid manually via the dashboard or via the API:

```
POST /api/v1/client/billing/invoices/{invoiceId}/mark-paid
```

**Authentication:** `X-API-Key: <your-api-key>`

**curl example:**

```bash
curl -X POST https://api.tanso.dev/api/v1/client/billing/invoices/inv_a1b2c3d4-e5f6-7890-abcd-ef1234567890/mark-paid \
  -H "X-API-Key: sk_live_tanso_abc123"
```

**Response:**

```json
{
  "success": true
}
```

Good for testing or enterprise customers with custom payment workflows.

### Full Sync (Co-Managed)

Bidirectional sync where both Tanso and Stripe maintain billing records. Tanso creates and maintains Stripe entities (Products, Prices, Subscriptions, Invoices, Meters, Customers) that mirror the Tanso data model. One subscription per customer applies here as well.

This mode is configured and managed by the Tanso team and is read-only in the dashboard. Contact support to enable it.

---

## Troubleshooting

**Invoices not being created in Stripe after reaching Due status:**

1. Verify your account shows **Connected** in Settings > Integrations
2. Confirm the Stripe API key has permissions to create and manage customers, invoices, prices, and webhook endpoints
3. Check that the webhook endpoint Tanso registered appears in Stripe Dashboard > Developers > Webhooks and is active

**Payment confirmed in Stripe but invoice still shows Due in Tanso:**

This usually means the Stripe webhook did not reach Tanso. Check for delivery failures in Stripe Dashboard > Developers > Webhooks. If the endpoint shows errors, verify your Tanso instance is reachable at the registered URL and retry delivery from the Stripe dashboard.

---

## What to Read Next

- **[Billing & Invoicing](./billing-and-invoicing.md)** — Invoice lifecycle, billing timing, and cycle management
- **[Settings & Roles](./settings-and-roles.md)** — API key management and account configuration
- **[FAQ & Troubleshooting](./faq-and-troubleshooting.md)** — Common integration issues and resolutions
