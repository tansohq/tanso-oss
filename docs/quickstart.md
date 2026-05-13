# Quickstart

Enforce usage limits and gate features with two API calls. No Stripe required.

> **AI coding assistant?** Feed it `https://docs.tansohq.com/llms.txt` or `npm install @tansohq/sdk` for typed autocomplete.

---

## Prerequisites

You need a Tanso API key and at least one active plan with a feature. Get your key from [dashboard.tansohq.com](https://dashboard.tansohq.com) under **Settings > General**.

```bash
export TANSO_API_KEY="sk_test_..."
```

If you haven't set up your catalog yet, create a feature (e.g., `ai-messages`) and a plan (e.g., `Starter`) in the dashboard under **Features** and **Plans**. Set the plan to **Active**. See [Product Catalog](./product-catalog.md) for details.

---

## Register a Customer and Subscribe Them

Use your own IDs. No sync required.

**curl:**
```bash
curl -X POST "https://api.tansohq.com/api/v1/client/customers" \
  -H "X-API-Key: $TANSO_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{ "externalClientCustomerId": "user_123", "email": "jane@acme.com" }'

curl -X POST "https://api.tansohq.com/api/v1/client/subscriptions" \
  -H "X-API-Key: $TANSO_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{ "customerReferenceId": "user_123", "planKey": "starter" }'
```

**TypeScript SDK:**
```typescript
import { TansoClient } from '@tansohq/sdk';
const tanso = new TansoClient(process.env.TANSO_API_KEY);

await tanso.customers.create({ externalClientCustomerId: 'user_123', email: 'jane@acme.com' });
await tanso.subscriptions.create({ customerReferenceId: 'user_123', planKey: 'starter' });
```

---

## Check Entitlements, Report Usage

These are the two calls you'll use in your request path. Check before processing, report after.

**curl:**
```bash
# Check: can this customer use this feature?
curl "https://api.tansohq.com/api/v1/client/entitlements/user_123/ai-messages" \
  -H "X-API-Key: $TANSO_API_KEY"

# Report: they used 1 unit
curl -X POST "https://api.tansohq.com/api/v1/client/events" \
  -H "X-API-Key: $TANSO_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "eventIdempotencyKey": "msg_user123_20260312_001",
    "eventName": "ai_message_sent",
    "featureKey": "ai-messages",
    "customerReferenceId": "user_123",
    "usageUnits": 1,
    "occurredAt": "2026-03-12T10:00:00Z"
  }'
```

**TypeScript SDK:**
```typescript
// Check
const { allowed } = await tanso.entitlements.check('user_123', 'ai-messages');

// Report
await tanso.events.ingest({
  eventIdempotencyKey: 'msg_user123_20260312_001',
  eventName: 'ai_message_sent',
  featureKey: 'ai-messages',
  customerReferenceId: 'user_123',
  usageUnits: 1,
  occurredAt: new Date().toISOString(),
});
```

**Entitlement response:**
```json
{
  "success": true,
  "data": {
    "referenceCustomerId": "user_123",
    "featureKey": "ai-messages",
    "allowed": true,
    "usage": { "used": 0, "limit": 1000, "remaining": 1000 }
  }
}
```

Entitlements fail closed. If Tanso is unreachable, access is denied. Duplicate `eventIdempotencyKey` values return HTTP 409.

---

## In Practice

The pattern is always **check, serve, report**:

```typescript
app.post('/api/chat', async (req, res) => {
  // Check
  const { allowed } = await tanso.entitlements.check(req.userId, 'ai-tokens');
  if (!allowed) return res.status(403).json({ error: 'Usage limit reached' });

  // Serve
  const result = await openai.chat.completions.create(req.body);

  // Report
  await tanso.events.ingest({
    eventIdempotencyKey: `chat_${req.userId}_${Date.now()}`,
    eventName: 'tokens_used',
    featureKey: 'ai-tokens',
    customerReferenceId: req.userId,
    usageUnits: result.usage.total_tokens,
  });

  res.json(result);
});
```

Works the same for SaaS feature gates, data APIs, build minutes, or anything else you meter. For boolean access (no usage tracking), skip the event call:

```typescript
const { allowed } = await tanso.entitlements.check(req.orgId, 'advanced-reports');
if (!allowed) return res.status(403).json({ error: 'Upgrade to access' });
```

---

## Add Stripe (Optional)

**Settings > Integrations** > **Connect Stripe**. Paste your `sk_test_...` key. Tanso auto-registers webhooks and imports existing Stripe data. Usage events flow to Stripe Billing Meters and invoices are collected through Stripe Checkout.

See [Stripe Integration](./stripe-integration.md) for details.

---

## LLM-Friendly Docs

- `https://docs.tansohq.com/llms.txt` - concise API summary for AI assistants
- `https://docs.tansohq.com/llms-full.txt` - complete docs in one file

---

## Next Steps

- [TypeScript SDK](https://www.npmjs.com/package/@tansohq/sdk) - typed client for Node.js 18+
- [Product Catalog](./product-catalog.md) - pricing models, graduated tiers, cost tracking
- [Entitlements](./entitlements.md) - pre-flight checks, credit-aware gating
- [Usage Metering & Events](./usage-metering-and-events.md) - event schema, idempotency
- [Credits](./credits.md) - pre-paid balances with rollover
- [Stripe Integration](./stripe-integration.md) - checkout, webhooks, billing sync
- [API Reference](https://tanso-core.readme.io/) - full endpoint docs
