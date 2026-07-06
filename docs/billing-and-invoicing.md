# Billing & Invoicing

Invoices are generated automatically throughout the subscription lifecycle: initial subscription, mid-cycle upgrades, prorated adjustments, and cancellations. Every invoice type, every status transition, every line-item calculation happens without manual intervention.

---

## Invoice Types

| Type                 | When Generated              | Description                                                                         |
| -------------------- | --------------------------- | ----------------------------------------------------------------------------------- |
| `REGULAR`            | Each billing cycle rollover | The standard recurring invoice for a billing period                                 |
| `IN_ADVANCE_INITIAL` | On subscription creation    | The first invoice for a new IN_ADVANCE subscription                                 |
| `ADJUSTMENT`         | On mid-cycle upgrade        | Captures the prorated difference between old and new plan prices                    |
| `CREDIT`             | On immediate cancellation   | A negative-amount invoice representing a prorated refund for unused IN_ADVANCE time |

---

## Invoice Status Lifecycle

```
PENDING ──────────────────────────────────────► PAID
   │                                              ▲
   │ (due date arrives)                           │
   ▼                                              │
  DUE ──────────────────────────────────────────►│
   │                                              │
   │ (grace period expires)                       │
   ▼                                              │
PAST_DUE ─────────────────────────────────────────┘
   │
   ▼
VOID / CANCELLED / CANCELLED_PROCESSED
```

| Status                | Meaning                                                                                       |
| --------------------- | --------------------------------------------------------------------------------------------- |
| `PENDING`             | Created but not yet due. Typical for IN_ARREARS invoices at period start.                     |
| `DUE`                 | Due now. Typical for IN_ADVANCE invoices at period start or rollover.                         |
| `PAST_DUE`            | Passed its due date without payment.                                                          |
| `PAID`                | Marked as paid, either via API or Stripe.                                                     |
| `VOID`                | Voided (e.g., due to an immediate cancellation).                                              |
| `CANCELLED`           | Cancelled in a standard cancellation flow.                                                    |
| `CANCELLED_PROCESSED` | Cancelled as part of a completed workflow (e.g., synced to Stripe).                           |
| `ADJUSTMENT_OPEN`     | An adjustment invoice that has been created but not yet paid.                                  |
| `ADJUSTMENT_PAID`     | An adjustment invoice that has been paid.                                                      |

---

## Invoice Line Items

Each invoice includes:

- A **base price line item** for the plan's flat fee (if non-zero)
- One **usage line item per feature** that had metered usage during the billing period

Usage line items are calculated by aggregating all billable events for the subscription during the billing period, then applying the pricing model from the feature's plan rule.

---

## Marking Invoices as Paid

Invoices can be marked as paid in three ways:

1. **Via the Client API** — `POST /api/v1/client/billing/invoices/{invoiceId}/mark-paid`
2. **Automatically by Stripe** — when a Stripe webhook confirms successful payment
3. **Through the Tanso dashboard** — for one-off situations

When an IN_ADVANCE invoice is paid, Tanso activates the subscription (if pending), updates the billing period, and grants entitlements and credits.

---

## API Endpoints

### Retrieve Invoices for a Customer

```http
GET /api/v1/client/billing/invoices/{externalClientCustomerId}?limit=20&offset=0
X-API-Key: sk_live_...
```

Returns invoices for the specified customer. Supports pagination via `limit` and `offset` query parameters.

### Mark an Invoice as Paid

```http
POST /api/v1/client/billing/invoices/{invoiceId}/mark-paid
X-API-Key: sk_live_...
```

Use this to confirm payment collected through your own mechanisms, or for testing in Sandbox.

### Generate a Stripe Checkout Session

```http
POST /api/v1/client/billing/subscriptions/{subscriptionId}/stripe/checkout
X-API-Key: sk_live_...
```

Returns a hosted Stripe Checkout URL. Redirect your customer to this URL to complete payment. Tanso resolves the first DUE invoice for the subscription automatically.

```json
{
  "success": true,
  "data": {
    "url": "https://checkout.stripe.com/pay/cs_live_..."
  }
}
```

> **Important:** This endpoint takes a `subscriptionId`, not an invoice ID. An earlier `/invoices/subscription/{subscriptionId}/stripe/checkout` form is deprecated and returns HTTP 403.

---

## What to Read Next

- **[Stripe Integration](./stripe-integration.md)** — Connect Stripe to automate payment collection
- **[Credits](./credits.md)** — Pre-paid credit pools on top of billing
- **Analytics** — Invoice data and cost models combined into per-customer margin intelligence
