# Introduction to Tanso

Tanso enforces entitlements, usage limits, and credit caps in your application's request path, then bills for what was used. Stripe is optional.

## How It Works

Your backend makes two API calls:

```typescript
// Before processing: can this customer use this feature?
const { allowed } = await tanso.entitlements.check('user_123', 'ai-messages');

// After processing: report what they used
await tanso.events.ingest({
  featureKey: 'ai-messages',
  customerReferenceId: 'user_123',
  usageUnits: 150, // tokens, messages, API calls, whatever you meter
});
```

Tanso handles the rest: enforcing limits, rolling up usage into invoices, syncing with Stripe if connected, and computing per-customer margins.

---

## The Integration Pattern

```
Your Backend                    Tanso                         Stripe (optional)
     |                            |                                |
     |-- check entitlement ------>|                                |
     |<-- allowed: true ----------|                                |
     |                            |                                |
     |   (process the request)    |                                |
     |                            |                                |
     |-- report usage ----------->|                                |
     |                            |-- sync to billing meter ------>|
     |                            |-- update usage counter         |
     |                            |-- enforce limit                |
```

You use your own customer IDs everywhere. No ID sync, no webhook plumbing, no custom billing logic in your codebase.

---

## Core Concepts

| Concept | What It Is |
|---|---|
| **Feature** | Something you gate or meter: `ai-messages`, `seats`, `api-calls` |
| **Plan** | A bundle of features with pricing. What customers subscribe to. |
| **Customer** | A billing entity keyed by your own ID. No sync required. |
| **Subscription** | Links a customer to a plan. Drives billing, entitlements, and invoices. |
| **Entitlement** | Real-time answer to "Can this customer use this feature right now?" |
| **Event** | A usage record from your backend. Powers billing and enforcement. |
| **Invoice** | Generated automatically each billing cycle. |
| **Credit** | Pre-paid balance with rollover policies and hard limits. |

---

## Get Started

```bash
npm install @tansohq/sdk
```

Then follow the **[Quickstart](./quickstart.md)**.

---

## LLM-Friendly Docs

Tanso publishes docs optimized for AI coding assistants:

- `./llms.txt` - concise summary
- `./llms-full.txt` - complete reference

The TypeScript SDK also provides full types for autocomplete.

---

## What to Read Next

- **[Quickstart](./quickstart.md)** - first API calls in 5 minutes
- **[TypeScript SDK](https://www.npmjs.com/package/@tansohq/sdk)** - official Node.js client
- **[Product Catalog](./product-catalog.md)** - features, plans, pricing models
- **[Entitlements](./entitlements.md)** - real-time feature gating
- **[Stripe Integration](./stripe-integration.md)** - add payment collection when ready
- **[API Reference](./clientAPI/openapi.yaml)** - full endpoint docs (OpenAPI spec)
