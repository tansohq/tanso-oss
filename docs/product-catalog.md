# Product Catalog

The catalog is the foundation of your billing configuration. It establishes what you sell, how it is priced, and what it costs you to deliver — a single source of truth that simultaneously drives product access, revenue billing, and margin visibility.

## Features — The Atomic Unit of What You Sell

A **Feature** represents a discrete capability within your product — something a customer either has access to or does not, and which may have measurable usage. Features are the building blocks from which all plans are assembled. Examples include `ai.text-generation`, `api-calls`, `storage-gb`, `premium-reports`, or `team-members`.

Every entitlement check, every usage event, and every billing line item traces back to a feature key.

### Feature Properties

| Field         | Description                                                                                                                   |
| ------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| `name`        | Human-readable display name (e.g., "AI Text Generation")                                                                      |
| `key`         | Machine-readable unique identifier (e.g., `ai.text-generation`) — used in API calls for entitlement checks and event tracking |
| `description` | Free-text explanation of what the feature provides                                                                            |
| `isEnabled`   | A flag controlling whether the feature is active and usable                                                                   |
| `metadata`    | Arbitrary JSON key-value pairs for custom context                                                                             |

The `key` field is the most operationally significant identifier. When your application reports usage or checks entitlements, it references the feature by key. **Invest time in choosing stable, well-structured keys from the start** — a namespace convention like `domain.action` (e.g., `ai.text-generation`, `reports.export`) makes your integration maintainable as your catalog grows.

Feature keys must be unique within your account. **Once a feature key is integrated into your application code, treat it as immutable.**

---

## Plans — Your Pricing Tiers, Declaratively Defined

A **Plan** is a named bundle of features with a base price and a billing configuration. Because plans are declarative, changing your pricing model is a configuration update, not a code change.

### Plan Properties

| Field            | Type / Values                 | Description                                                               |
| ---------------- | ----------------------------- | ------------------------------------------------------------------------- |
| `key`            | String                        | Machine-readable unique identifier (e.g., `starter-monthly`)              |
| `name`           | String                        | Display name (e.g., "Pro")                                                |
| `description`    | String                        | Optional free-text description                                            |
| `priceAmount`    | Decimal                       | Base price charged each billing period                                    |
| `currency`       | ISO 4217 (e.g., `USD`)        | Billing currency for this plan                                            |
| `intervalMonths` | Integer                       | Billing period length in months (1 = monthly, 3 = quarterly, 12 = annual) |
| `billingTiming`  | `IN_ADVANCE` or `IN_ARREARS`  | When the invoice is due relative to the period it covers                  |
| `status`         | `Draft`, `Active`, `Archived` | Lifecycle status of the plan                                              |
| `metadata`       | JSON                          | Arbitrary custom fields                                                   |

### Plan Lifecycle

* **Draft**: Being configured, not yet available for subscriptions.
* **Active**: Accepts new subscriptions.
* **Archived**: Retired. Existing subscribers continue uninterrupted, but no new subscriptions can be created.

### Billing Timing

* **IN_ADVANCE**: Invoiced at the start of the period. Subscription activates once the invoice is paid (or automatically if the plan price is zero). Appropriate for fixed-price plans.

* **IN_ARREARS**: Invoiced at the end of the period for accumulated usage and base charges. Entitlements are granted immediately. **The natural fit for usage-based products.**

---

## Plan Feature Rules — Where Pricing Logic Lives

A **Plan Feature Rule** connects a Feature to a Plan and defines how that feature is priced within that plan.

**The same feature can be priced differently across plans** — `api-calls` can carry a $0.10/call rate on Starter and $0.05/call on Enterprise, with the system handling all of the math automatically.

For each feature attached to a plan, configure:

* **Pricing Model** — `usage` for flat per-unit rate, or `graduated` for tiered rates.
* **Usage unit type** — label for what is counted (e.g., `token`, `api_call`).
* **Price per unit** (usage model) — rate charged per unit consumed.
* **Tiers** (graduated model) — usage thresholds with per-unit rates.
* **Cost model** (optional) — your internal COGS rate. Enables per-customer margin calculations in Analytics.

---

## Pricing Models

### Simple Usage Model (`model: "usage"`)

Flat rate per usage unit.

```json
{
  "model": "usage",
  "usage_unit_type": "token",
  "price_per_unit": 0.0001
}
```

**Calculation:** `Charge = usage_units × price_per_unit`

### Usage Limits

Any pricing model can include a `max_usage` field that caps cumulative consumption. When reached, entitlement checks return `allowed: false`.

```json
{
  "model": "usage",
  "usage_unit_type": "token",
  "price_per_unit": 0.0001,
  "max_usage": 1000000
}
```

The limit is evaluated in real time during every entitlement check.

### Graduated Pricing Model (`model: "graduated"`)

Different per-unit rates for different usage brackets. Each bracket applies only to units within it.

```json
{
  "model": "graduated",
  "usage_unit_type": "api_calls",
  "tiers": [
    { "up_to": 1000,  "price_per_unit": 0.10 },
    { "up_to": 5000,  "price_per_unit": 0.08 },
    { "up_to": "inf", "price_per_unit": 0.05 }
  ]
}
```

**Example for 1,500 units:**
* First 1,000 at $0.10 = $100.00
* Next 500 at $0.08 = $40.00
* **Total: $140.00**

---

## Cost Models — The Engine Behind Margin Analytics

Each plan feature rule can include a cost model (what you pay to deliver it). By tracking COGS at the feature level, Tanso computes per-customer margins and flags profitability risk.

```json
{
  "model": "usage",
  "usage_unit_type": "token",
  "price_per_unit": 0.0001,
  "cost_model": "simple",
  "cost_per_unit": 0.00004,
  "cost_unit": "CURRENCY"
}
```

| Field           | Description                                                         |
| --------------- | ------------------------------------------------------------------- |
| `cost_model`    | Set to `"simple"` to activate                                       |
| `cost_per_unit` | What you pay your provider per unit (e.g., your OpenAI or AWS cost) |
| `cost_unit`     | The denomination: `CURRENCY`, `TOKENS`, or `CREDITS`                |

> **Note:** Cost models do not affect what the customer is charged — only the COGS calculation for your margin analytics.
