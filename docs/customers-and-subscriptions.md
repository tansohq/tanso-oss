# Customers & Subscriptions

Register a customer once, subscribe them to a plan, and Tanso handles the rest: invoicing, entitlement grants, credit allocation, renewal cycles, upgrades, downgrades, and cancellations — all automatic, all auditable.

## Customer Management — Zero ID-Sync Required

A **Customer** in Tanso is the billing entity you subscribe to plans and track usage for. The design is intentionally frictionless: **you use your own identifiers everywhere**, and Tanso never requires you to store or sync its internal IDs.

### Customer Fields

| Field                      | Description                                                                                                                                          |
| -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `externalClientCustomerId` | **The critical integration field.** Your own system's identifier for this customer. Every API call references this value, not Tanso's internal UUID. |
| `firstName`, `lastName`    | Contact name fields                                                                                                                                  |
| `email`                    | Contact email address                                                                                                                                |
| `phoneNumber`              | Optional phone number                                                                                                                                |
| `address`                  | Optional linked address record                                                                                                                       |

The `externalClientCustomerId` — also referred to as `customerReferenceId` in API requests — is the integration bridge between Tanso and your system. You supply your own identifier (a user ID, organization ID, or account UUID from your database), and that same value drives all subsequent operations. **You never need to store Tanso's internal customer UUID in your own database.**

### Create a Customer

```http
POST /api/v1/client/customers
X-API-Key: sk_live_...

{
  "customerReferenceId": "org_7f3a9b",
  "firstName": "Jane",
  "lastName": "Smith",
  "email": "jane@acmecorp.com"
}
```

Customer records support soft deletion — removing a customer preserves the complete history of invoices and events for auditing and compliance.

### Retrieve a Customer

```http
GET /api/v1/client/customers/{externalClientCustomerId}
X-API-Key: sk_live_...
```

Returns the customer record associated with the given reference ID.

---

## Subscription Lifecycle

A **Subscription** links a Customer to a Plan and drives the entire billing lifecycle. The lifecycle is fully automated: you create the subscription, and Tanso handles renewals, proration, plan changes, and cancellations.

### Create a Subscription

```http
POST /api/v1/client/subscriptions
X-API-Key: sk_live_...

{
  "customerReferenceId": "org_7f3a9b",
  "planId": "plan_uuid_here"
}
```

Subscriptions follow a **Draft → Active** lifecycle for paid plans billed in advance.

When you create a subscription, Tanso:

1. Creates a Subscription record with billing period and anchor day.
2. Generates the initial Invoice according to the plan's billing timing.

For **in-arrears** and **free** plans, the subscription activates immediately — entitlements and credits are granted on creation.

For **paid in-advance** plans, the subscription starts in **Draft** status. Entitlements and credits are not granted until the subscription is activated.

### Activate a Draft Subscription

```http
POST /api/v1/monetization/subscriptions/{subscriptionId}/activate
Authorization: Bearer <jwt>
```

On activation, Tanso:

1. Marks the initial invoice as paid.
2. Sets the subscription to **Active**.
3. Grants Entitlements for all features in the plan.
4. Grants any Credits defined by the plan's credit allocations.
5. Syncs the subscription to Stripe (if connected).

### Billing Anchor Day

The `billingAnchorDay` is the day-of-month on which the subscription was created. It anchors all future billing cycle calculations, ensuring invoices consistently fall on the same calendar day each month.

### Grace Period

Every subscription has a `gracePeriodDays` field (defaulting to **3 days**) that specifies how long after an invoice due date the system waits before treating the subscription as past-due. This buffer absorbs payment processing delays without immediately disrupting the customer's access.

---

## Plan Upgrades — Immediate, Prorated, and Automatic

When a customer upgrades to a higher-tier plan, the change takes effect immediately. For IN_ADVANCE plans, Tanso automatically calculates the prorated adjustment:

```
Unused ratio = Remaining time in period / Total period duration
Adjustment amount = (New plan price − Old plan price) × Unused ratio
```

The proration is issued as an **Adjustment Invoice**. Entitlements are updated immediately and credit allocations are delta-granted.

For IN_ARREARS plans, no proration is needed — the plan swaps immediately and the customer is billed at the new rate at the next cycle close.

---

## Plan Downgrades — Scheduled at Period End

Downgrades are scheduled rather than immediate — protecting the customer's access through the period they've already paid for. At the next billing cycle rollover, the system applies the downgrade automatically.

A scheduled downgrade can be cancelled at any time before its effective date.

---

## Cancellation Modes

### Immediate Cancellation

The subscription ends now. Tanso automatically:

* Sets the subscription to inactive
* Voids any outstanding DUE or PENDING invoices
* For IN_ADVANCE plans, creates a prorated `CREDIT` invoice
* Revokes all entitlements
* Claws back unused `PLAN_INCLUDED` credits (leaving `PURCHASED` or `PROMOTIONAL` credits intact)

### End of Period Cancellation

The subscription continues through the current billing period and cancels automatically when it expires. Entitlements remain valid until the period ends.

A scheduled cancellation can be reversed before the effective date.

---

## Automatic Billing Cycle Rollover

Tanso continuously monitors active subscriptions for billing cycle expiry. When a subscription's period ends:

1. Checks for past-due invoices — if found, rollover is skipped.
2. Applies any pending scheduled downgrades.
3. Advances the period to the next billing cycle.
4. Creates a new invoice (IN_ADVANCE vs. IN_ARREARS).
5. For IN_ARREARS plans, applies credit rollover policies and grants new-period credits.
6. For free plans, automatically marks the new invoice as paid.

This process is distributed-safe, preventing duplicate processing across multiple application instances.

---

## API Endpoints

### Upgrade or Downgrade a Subscription

```http
POST /api/v1/client/subscriptions/{subscriptionId}/plan-change
X-API-Key: sk_live_...

{
  "changeToPlanId": "plan_uuid_here",
  "changeType": "UPGRADE"
}
```

Set `changeType` to `UPGRADE` (immediate with proration) or `DOWNGRADE` (scheduled for end of period).

### Undo a Scheduled Plan Change

```http
DELETE /api/v1/client/subscriptions/{subscriptionId}/plan-change/scheduled
X-API-Key: sk_live_...
```

### Cancel a Subscription

```http
POST /api/v1/client/subscriptions/cancellation/{subscriptionId}?cancelMode=IMMEDIATE|END_OF_PERIOD
X-API-Key: sk_live_...
```

* `IMMEDIATE` — ends instantly; entitlements revoked, invoices voided, prorated credit issued for IN_ADVANCE.
* `END_OF_PERIOD` — continues through current period; access remains active until expiry.

### Undo a Scheduled Cancellation

```http
DELETE /api/v1/client/subscriptions/cancellation/{subscriptionId}/scheduled
X-API-Key: sk_live_...
```
