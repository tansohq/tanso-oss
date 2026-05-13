# Credits System

Many modern SaaS products — particularly AI-powered tools — need more than pure monetary billing. Customers want pre-paid credit packs, included monthly allowances, promotional grants, and rollover policies. **Building this infrastructure in-house is a multi-sprint engineering project.** Tanso's Credits system delivers it out of the box.

Credits sit alongside or in place of monetary billing, covering use cases like AI token allowances, purchased credit packs, promotional grants, and configurable rollover behavior. The system is built from four components that layer on top of each other: **Credit Models** define the denomination and policies, **Credit Pools** hold per-customer balances, **Credit Grants** record every deposit, and **Credit Transactions** provide an immutable audit ledger. Each layer serves a distinct role — and together they handle the full credit lifecycle without custom code.

---

## Credit Models — Configure Once, Apply Everywhere

A **Credit Model** is a reusable template that defines the behavior of a class of credits within your account. It specifies the denomination (what kind of credits these are) and the default policies that govern how pools of this type behave.

| Field            | Description                                                                                                  |
| ---------------- | ------------------------------------------------------------------------------------------------------------ |
| `name`           | Human-readable name (e.g., "AI Token Credits")                                                               |
| `denomination`   | The unique identifier for this credit type (e.g., `AI_TOKENS`, `CREDITS`, `MESSAGES`)                        |
| `description`    | Free-text description                                                                                        |
| `hardLimit`      | If `true`, customers cannot consume more credits than their current balance — the deduction will be rejected |
| `rolloverPolicy` | How unused credits at period end are handled: `NONE`, `FULL`, or `CAPPED`                                    |
| `rolloverCap`    | Maximum credits that roll over when policy is `CAPPED`                                                       |

**Denomination** is the key concept that ties credits together. A pool, a grant, and a plan allocation all reference the same denomination string to indicate they are working with the same credit type.

**Hard limit** enforcement means that if `hardLimit = true` and a customer's pool balance would go negative, the deduction API returns an error. Your application can use the entitlement check to gate access before deduction, ensuring customers never exceed their allowance. **This is how you prevent revenue leakage from over-consumption without building custom enforcement logic.**

### Rollover Policies

| Policy   | Behavior                                                             |
| -------- | -------------------------------------------------------------------- |
| `NONE`   | All unused credits expire at period end                              |
| `FULL`   | All unused credits carry forward to the next period                  |
| `CAPPED` | Credits up to the `rolloverCap` carry forward; the remainder expires |

---

## Credit Pools — The Per-Customer Balance Ledger

A **Credit Pool** is the actual credit balance associated with a specific customer. Every customer can have one pool per denomination. Pools maintain the following running balances:

| Field           | Description                                               |
| --------------- | --------------------------------------------------------- |
| `balance`       | Current available credit balance                          |
| `totalGranted`  | Lifetime total credits deposited into this pool           |
| `totalConsumed` | Lifetime total credits deducted from this pool            |
| `totalExpired`  | Lifetime total credits that have expired                  |
| `totalReversed` | Lifetime total credits restored via reversal transactions |

Pools handle concurrent deductions safely. If two requests attempt to deduct from the same pool simultaneously, the system automatically retries without conflict — maximizing throughput without blocking. **High-concurrency AI workloads don't bottleneck on credit deduction.**

Pools are typically created automatically when a customer subscribes to a plan that includes a credit allocation. You can also create pools manually and link them to subscriptions with explicit draw priorities and draw limits.

---

## Credit Grants — Every Balance Increase Has a Paper Trail

A **Credit Grant** is a specific deposit of credits into a pool. Every increase in a pool's balance originates from a grant. Grants carry a type that explains their origin:

| Grant Type      | Description                                                                                  |
| --------------- | -------------------------------------------------------------------------------------------- |
| `PLAN_INCLUDED` | Credits automatically granted when a customer subscribes or when a billing period rolls over |
| `PURCHASED`     | Credits the customer explicitly bought (e.g., a credit top-up)                               |
| `PROMOTIONAL`   | Free credits granted for marketing or retention purposes                                     |
| `REFUND`        | Credits issued as a refund                                                                   |
| `SYSTEM`        | Credits granted by internal system processes                                                 |
| `ROLLOVER`      | Credits that carried over from a previous billing period                                     |
| `MANUAL`        | Credits granted manually by an administrator                                                 |

Grants can have an optional `expiresAt` timestamp. When a grant expires, the remaining balance is zeroed and an `EXPIRATION` transaction is recorded. **Deductions are consumed from grants in FIFO (first-in, first-out) order**, ensuring older credits are used before newer ones.

Idempotency keys on grants prevent duplicate grants from being issued — protecting against double-granting when retry logic is active during subscription renewals.

### Plan-Included Grant Lifecycle

When a customer subscribes to a plan with a credit allocation, Tanso automatically creates a `PLAN_INCLUDED` grant for the full allocation amount. At each period rollover, the system applies the rollover policy first (expiring or capping unused credits), then issues a fresh grant for the next period. When a subscription is cancelled, unused `PLAN_INCLUDED` grants are automatically voided ("clawed back"), while `PURCHASED` and `PROMOTIONAL` grants remain intact — protecting credits the customer has paid for or earned.

### Delta Grants on Upgrade

When a customer moves to a higher-tier plan, only the difference in credits is granted — existing unused credits are preserved rather than replaced.

---

## Credit Transactions — A Complete, Tamper-Proof Audit Ledger

**Credit Transactions** form an append-only financial ledger for each pool. Every change to a pool's balance creates an immutable transaction record. Transactions are never updated or deleted.

| Transaction Type | Description                                                       |
| ---------------- | ----------------------------------------------------------------- |
| `GRANT`          | Credits added to the pool                                         |
| `DEDUCTION`      | Credits consumed from the pool                                    |
| `EXPIRATION`     | Credits expired from the pool                                     |
| `REVERSAL`       | A previous transaction was reversed (restores the inverse amount) |
| `ADJUSTMENT`     | A manual balance correction                                       |
| `FREEZE`         | Pool was frozen (no deductions allowed)                           |
| `UNFREEZE`       | Pool was unfrozen                                                 |

Each transaction records `balanceBefore` and `balanceAfter`, so the complete history of balance changes can be reconstructed and audited at any time. The immutable append-only design ensures the ledger can never be corrupted or inconsistently updated — critical for both internal compliance and customer dispute resolution.

---

## Linking Pools to Subscriptions — Ordered Draw Priority

A **CreditPoolSubscription** record explicitly links a pool to a subscription, controlling which pools are drawn from when billing usage:

- `drawPriority`: Lower numbers are drawn first. If a customer has a promotional pool (priority 0) and a plan-included pool (priority 1), promotional credits are consumed first.
- `drawLimit`: An optional cap on how much can be drawn from this pool per billing period via this subscription.

---

## Plan Credit Allocations — Automatic Provisioning at Every Renewal

A **Plan Credit Allocation** defines how many credits of a given denomination are included with each billing cycle of a plan. When you attach an allocation to a plan, every customer subscribed to that plan automatically receives the specified credit amount at each period start — **no manual provisioning required.** Credits are a first-class plan benefit, not an afterthought.

| Field                | Description                                                                  |
| -------------------- | ---------------------------------------------------------------------------- |
| `creditAmount`       | How many credits to grant per period                                         |
| `creditModel`        | Links to the Credit Model defining denomination and policies                 |
| `grantExpiresMonths` | Optional: how many months after granting the credits expire                  |
| `hardLimit`          | Optional: whether to enforce hard limit on pools created for this allocation |

---

## What to Read Next

- **[Billing & Invoicing](./billing-and-invoicing.md)** — Understand how credit consumption interacts with invoice generation and payment
- **[Analytics](./analytics.md)** — See how credit usage is factored into your portfolio's net effective MRR and margin analysis
- **[Product Catalog](./product-catalog.md)** — Attach credit allocations to plans so every subscriber is provisioned automatically at each renewal
