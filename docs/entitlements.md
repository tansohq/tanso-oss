# Entitlements — Real-Time Feature Gating

An **Entitlement** is the real-time record of a customer's right to access a specific feature. Rather than maintaining your own access control tables and keeping them in sync with billing state, Tanso manages entitlements automatically — granting them on subscription, updating them on plan changes, and revoking them on cancellation. **Your application asks one question and gets a definitive answer.** No custom enforcement logic. No sync jobs. No stale cache.

---

## The Entitlement Check — One Call, Definitive Access Control

Your application checks entitlements at the moment a customer attempts to use a feature — before processing an AI generation request, before opening a premium report, before executing a billable action. The check is a single API call:

```http
GET /api/v1/client/entitlements/{customerReferenceId}/{feature-key}
X-API-Key: sk_live_...
```

The response tells you whether the customer is allowed to access the feature:

```json
{
  "success": true,
  "data": {
    "referenceCustomerId": "org_7f3a9b",
    "featureKey": "ai.text-generation",
    "allowed": true,
    "flowId": "flow_abc123",
    "meta": {
      "reason": {
        "description": "Active subscription entitlement"
      }
    }
  }
}
```

If the customer does not have an entitlement for the feature — no active subscription, subscription cancelled, or plan does not include the feature — `allowed` returns `false`. Your application acts on this value to allow or deny access.

### What the Entitlement Check Evaluates

The entitlement check evaluates three distinct conditions before returning a result. All three must pass for `allowed` to be `true`:

1. **Basic entitlement** — the customer has an active subscription that includes the feature.
2. **Usage limit** — if the feature's pricing model has a `maxUsage` field configured, the customer's cumulative usage since subscription start must not exceed that limit. Exceeding the limit causes `allowed` to return `false` even when the subscription is otherwise active.
3. **Credit hard limit** — if a credit model is linked to the plan feature rule and the credit model has `hardLimit` set, the customer's credit pool must not be exhausted. Depleting a credit pool with a hard limit blocks further access just as a `maxUsage` limit would.

This three-factor evaluation ensures that access control is always derived from a single, authoritative check — you do not need to separately track usage quotas or credit balances in your own system.

> **Note:** If the customer record cannot be located for the given `customerReferenceId`, the API returns an **HTTP 204 No Content** response with an empty body. This distinction lets you differentiate between "customer exists but is not entitled" (`allowed: false`) and "customer not found" (empty 204) — an important debugging signal when troubleshooting access issues.

---

## Usage Simulation — Pre-Flight Quota Checks Before Resources Are Consumed

Most access control systems answer one question: "Is this customer allowed?" Tanso goes further. For scenarios where you need to know whether a specific usage amount would push a customer over their quota, Tanso provides a simulation endpoint. This performs a **dry-run entitlement check** — it evaluates current entitlement state and projects what would happen if the specified usage were consumed, without recording any billable usage.

This is particularly valuable for AI workloads: before kicking off an expensive generation request, pre-flight the quota to avoid processing something you cannot bill for or that would breach the customer's plan limits.

```http
POST /api/v1/client/entitlements
X-API-Key: sk_live_...

{
  "customerReferenceId": "org_7f3a9b",
  "featureKey": "ai.text-generation",
  "usage": {
    "usageUnits": 15000
  },
  "context": {
    "idempotencyKey": "req_abc123",
    "flowId": "flow_chat_turn_42"
  }
}
```

> **Note:** The `usage` field also accepts `track` as an alias for backward compatibility with earlier integrations.

The endpoint records an audit event with zero usage (marked as audit-only) and returns both the customer's current usage state and a projection of what the requested usage would produce:

```json
{
  "success": true,
  "data": {
    "referenceCustomerId": "org_7f3a9b",
    "featureKey": "ai.text-generation",
    "allowed": true,
    "usage": {
      "used": 240000,
      "limit": 500000,
      "remaining": 260000
    },
    "simulation": {
      "requestedUsage": 15000,
      "projectedUsage": 255000,
      "projectedRemaining": 245000,
      "wouldExceedLimit": false
    }
  }
}
```

### Response Fields

**`usage` object — current state:**

| Field | Description |
|---|---|
| `used` | Usage accumulated since subscription start |
| `limit` | The `maxUsage` limit configured on the pricing model, if any |
| `remaining` | Units available before the limit is reached |

**`simulation` object — projected impact:**

| Field | Description |
|---|---|
| `requestedUsage` | The usage amount provided in the request |
| `projectedUsage` | What `used` would become after adding `requestedUsage` |
| `projectedRemaining` | What `remaining` would become after the operation |
| `wouldExceedLimit` | `true` if proceeding would push the customer over their usage limit |

> **Important:** This endpoint does **not** record billable usage. To record actual usage from your application, use `POST /api/v1/client/events`. The simulation endpoint is intended for pre-flight checks, not metering.

---

## Bulk Entitlement Check — All Features for a Customer in One Call

If you need to check multiple features at once — for example, to render a dashboard or settings page — use the bulk entitlement endpoint:

```http
GET /api/v1/client/entitlements/{customerReferenceId}
X-API-Key: sk_live_...
```

Returns all entitlements for the specified customer. Supports pagination via `limit` and `offset` query parameters (e.g., `?limit=20&offset=0`).

---

## Entitlement Lifecycle — Always in Sync with Billing State, Automatically

Entitlements are managed automatically at every stage of the subscription lifecycle. You never need to manually grant or revoke access based on payment events:

| Event | What Happens |
|---|---|
| **Subscription created** (IN_ARREARS or free IN_ADVANCE) | Entitlements granted immediately for all features in the plan |
| **Invoice paid** (paid IN_ADVANCE) | Entitlements granted once the initial invoice is confirmed as paid |
| **Plan upgrade** | Entitlements reconciled immediately — new features granted, removed features revoked |
| **Plan downgrade** (applied at period end) | Entitlements reconcile when the downgrade takes effect |
| **Immediate cancellation** | All entitlements revoked at the moment of cancellation |
| **End-of-period cancellation** | Entitlements remain valid through the end of the current period, then automatically revoked |

---

## Background Reconciliation — One Catalog Change, Instant Fleet-Wide Propagation

When a plan or feature configuration changes in a way that affects multiple customers — disabling a feature, or changing which features are included in a plan — Tanso enqueues reconciliation jobs to update entitlements across all affected subscriptions. **A single catalog change propagates to your entire customer base without manual intervention** — no scripts to run, no customer-by-customer updates.

The reconciliation worker runs continuously with approximately a 10-second polling interval, ensuring entitlement state converges quickly after any configuration change.

---

## What to Read Next

- **[Usage Metering & Events](./usage-metering-and-events.md)** — Report actual usage to drive billing, with built-in idempotency to prevent double-counting
- **[Billing & Invoicing](./billing-and-invoicing.md)** — See how entitlement-tracked events flow automatically into invoice line items
- **[Credits](./credits.md)** — Layer pre-paid credit pools on top of entitlement checks — hard limits enforce quota without custom logic
