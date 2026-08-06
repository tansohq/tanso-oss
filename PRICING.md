# Credit pricing: the two dials

Adjust what actions burn and what credits cost — at runtime, without a
deploy. This page is written to port directly to the hosted docs site
(Mintlify); the appendix specifies the matching `@tansohq/sdk` 0.4 changes
for the separately maintained SDK repo.

Credit pricing in Tanso has exactly two controls. Everything a customer pays
for credits is the product of them:

```
usage × weight = credits burned
credits × price = what customers pay
```

| Dial | What it sets | Where |
| ---- | ------------ | ----- |
| **1 · Burn rate (weights)** | How many credits one usage unit burns, per feature and optionally per model | Console **Credits → Weights**, `POST /api/v1/monetization/credits/weights/publish` |
| **2 · Credit price (price book)** | What one credit costs the buyer, per denomination, in an ISO currency | Console **Credits → Pricing**, `POST /api/v1/monetization/credits/prices/publish` |

Both dials share the same safety mechanics: changes are published as a batch
that takes effect at a **future time you pick**, effective rows are
**append-only** (history is never rewritten — everything already charged
keeps the numbers in force at the time), scheduled rows can be deleted until
they take effect, and rows omitted from a publish keep their current values.

## A worked example

You sell `CREDITS` at **$0.10** each, and `ai.chat` on `gpt-large` has a
weight of **2.5**. A customer runs 100 requests:

```
100 requests × 2.5 credits/request = 250 credits burned
250 credits × $0.10 = $25.00 of credit value consumed
```

## When to turn which dial

**Turn the burn-rate dial when the cost of an *action* changes:**

- Your provider raises prices for one model → raise that model's weight
  (`2.5 → 3`). Other models and your credit price stay untouched.
- You launch a cheaper model → add a model-specific weight below the feature
  default.
- A feature is heavier to serve than expected → raise its default weight
  instead of repricing every plan.

**Turn the price dial when the value of a *credit* changes:**

- Promotion or repositioning → publish a lower price per credit. Balances
  customers already hold are unaffected.
- Costs rise across the board → raise the credit price once instead of
  touching every weight.
- An enterprise negotiates a volume deal → leave the book alone and grant
  their credits with an explicit `unitPrice`.

## The price book in the API

**Quote money before billable work.** When a denomination has a published
price, the entitlement `creditQuote` carries it:

```json
"creditQuote": {
  "weight": 8,
  "estimatedCredits": 8,
  "weightMatch": "MODEL",
  "pricePerCredit": 0.10,
  "currency": "USD",
  "estimatedCost": 0.80
}
```

An unpriced denomination quotes credits only — the monetary fields are null;
Tanso never invents a price.

**Show prices on paywall and top-up screens:**

```bash
curl http://localhost:8080/api/v1/client/credits/prices \
  -H "X-API-Key: $TANSO_API_KEY"
# → [{"denomination":"AI_CREDITS","currency":"USD","pricePerCredit":0.10, ...}]
```

**Sell top-ups.** A `PURCHASED` grant without an explicit price is stamped
with the book price in force at the moment of sale, so every sale permanently
records what it happened at even after the book moves:

```bash
curl -X POST http://localhost:8080/api/v1/monetization/credits/grants \
  -H "Authorization: Bearer $JWT" -H 'Content-Type: application/json' \
  -d '{"creditPoolId":"…","amount":1000,"grantType":"PURCHASED"}'
# → "unitPrice": 0.10, "currency": "USD"   (stamped from the book)
```

Pass `unitPrice` (and optionally `currency`) for negotiated deals — it always
wins over the book. Explicit prices are held to the book's bounds (positive,
max 1,000,000, up to 6 decimals), and a `currency` without a `unitPrice` is
rejected.

## Rules that keep the ledger honest

- `effectiveFrom` must be in the future — the settled ledger is never
  repriced.
- Publishing a *different* batch at an instant that already has one returns
  **409**; replaying the *identical* batch is idempotent and returns the
  existing rows.
- Only scheduled (not-yet-effective) rows can be deleted.
- Price entries must reference a denomination that exists as a credit model
  on the account.

---

# Appendix: @tansohq/sdk 0.4 changes

## 1. `EntitlementEvaluationCreditQuote` gains monetary fields

`POST /api/v1/client/entitlements` (`entitlements.evaluate`) now returns,
inside `creditQuote`:

```ts
export interface EntitlementEvaluationCreditQuote {
  weight: number;
  estimatedCredits: number;
  weightId: string | null;
  weightMatch: WeightMatch;
  /** Current price of one credit from the price book. Null when the denomination is unpriced. */
  pricePerCredit: number | null;
  /** ISO 4217 currency for pricePerCredit and estimatedCost. */
  currency: string | null;
  /** estimatedCredits × pricePerCredit. Null when unpriced. */
  estimatedCost: number | null;
}
```

Use case: show end users "this action will use ~3 credits (≈ $0.30)" before
running billable work. `examples/nextjs-ai-credits/app/api/generate/route.ts`
demonstrates this (currently via a local type extension — drop it once the
SDK ships these fields).

## 2. New client endpoint: current credit prices

`GET /api/v1/client/credits/prices` — the price in force right now for each
priced denomination (unpriced denominations omitted). Suggested surface:

```ts
export interface CreditPrice {
  id: string;
  denomination: string;
  currency: string;      // ISO 4217
  pricePerCredit: number;
  effectiveFrom: string; // ISO timestamp
  createdBy: string | null;
  createdAt: string;
}

// client.credits.prices(): Promise<CreditPrice[]>
```

Use case: paywall/top-up screens ("1 credit = $0.10", total = quantity ×
pricePerCredit).

## 3. `CreditGrant` gains price stamping

Grants now carry the price they were sold at:

```ts
export interface CreditGrant {
  // ...existing fields...
  /** Price paid per credit, if this grant was sold. */
  unitPrice: number | null;
  /** ISO 4217 currency for unitPrice. */
  currency: string | null;
}
```

Server behavior worth documenting in the SDK README: a `PURCHASED` grant
created without an explicit `unitPrice` is stamped with the current price
book entry for the pool's denomination; an explicit `unitPrice` (negotiated
top-up) always wins and is never overwritten by later book changes.
