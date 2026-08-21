# Changelog

Notable changes to Tanso Core. Releases before 0.9.0 are recorded only as git
tags; this file starts where the changelog does.

## 0.9.0

The release where the customer buying from your product stops having to be a
person, and where you can put a ceiling on what a non-person spends.

### Added

- **Per-key spend budgets.** `PUT /api/v1/client/customers/{ref}/keys/{keyId}/budget`
  caps credits and money independently over a rolling `DAY`/`WEEK`/`MONTH`/`TOTAL`
  window; either axis may be left unlimited. The budget bounds the key, not the
  pool, so one agent cannot drain the balance its siblings draw on. Breaching
  returns 403 `budget_exceeded` with the limit, spend, remainder, and reset time.
  Only the tenant may set or clear a budget; a customer key can read its own.
  Also reachable over MCP as `getMyBudget`.
- **Agent-serve surface.** Public machine-readable catalog
  (`/public/v1/catalog/{slug}/pricing.json`), opt-in programmatic signup,
  customer-scoped `ck_` keys with `read`/`purchase` scopes and deny-by-default
  authorization, SetupIntent-based purchasing with a 402-plus-checkout fallback
  and pollable checkout sessions, a usage and burndown forecast endpoint, and a
  curated customer-facing MCP tool set.
- **Two credit pricing dials.** A weight tariff (`CreditFeatureWeight`) mapping
  usage to credits, and a price book (`CreditPrice`) mapping credits to money.
  Both append-only and effective-dated, both with console editors.
- **Admin console** (`ui/`) shipped in the repo: plans, features, customers,
  subscriptions, credits, events, invoices, and an Overview with customer and
  model detail sheets, a first-run checklist, and a Stripe connection card.
- **Anonymous instance telemetry**, opt-out with `TANSO_TELEMETRY_ENABLED=false`.
  One class, payload documented verbatim in the README.
- **OpenAPI spec** committed at the repo root and regenerated with the API surface.
- Stable `error.code` on every response, `Idempotency-Key` replay with a 24-hour
  window, and a second opt-in flag gating the MCP admin tools.

### Fixed

- A paid subscribe could walk straight past a key's spend budget: a key capped at
  $0.00 was refused a $0.50 top-up and then handed a live Stripe Checkout URL for
  a recurring plan.
- The account-wide cap on a single agent-initiated charge covered credit top-ups
  but not subscribe.
- `POST /credits/pools` returned `"id": null`, leaving the caller unable to grant
  into the pool it had just created.
- An unrouted path returned 500 `internal_error` instead of 404 `not_found`, and
  a wrong verb did the same instead of 405.
- An account-cap breach and a key-budget breach returned two different error
  codes for one outcome.
- `POST /monetization/customers` returned 201 with an empty body, so the caller
  had to list every customer to find the id of the one it just made.
- Usage-priced plans in `STRIPE_INTEGRATION` created only the metered price,
  dropping the plan's flat base fee and 500ing every subscribe.
- Disconnecting Stripe left the account's mode pointed at an integration with no
  working key.
- Invoice mark-as-paid was a 501 stub.
- Malformed query params, tariff publish collisions, and entity-validation
  failures returned blank 500s instead of 400, 409, and a named field.
- Entitlement reconciliation failures and legacy cost-metadata parse errors were
  swallowed silently.

### Changed

- API keys are stored as SHA-256 digests with a display hint. Legacy plaintext
  rows upgrade in place on their first successful authentication.
