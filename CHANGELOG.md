# Changelog

Notable changes to Tanso Core. Releases before 0.9.0 are recorded only as git
tags; this file starts where the changelog does.

## Unreleased

### Added

- **The events behind a usage total can be read back.**
  `GET /api/v1/client/customers/{ref}/usage/events` returns the individual
  events a customer recorded, newest first, with the idempotency key each was
  written under, so an aggregate can be checked against its records. Optional
  `featureKey`, page-based paging, a window that defaults to 90 days and is
  capped at 366. The events endpoint was write-only; recorded usage stays
  append-only, and a correction is another event.

### Fixed

- **Stripe now delivers the checkout and card-setup events Tanso handles.**
  The webhook endpoint Tanso registers left out `checkout.session.completed`,
  `checkout.session.expired`, `setup_intent.succeeded` and
  `payment_intent.succeeded`, so on a real deployment a spend mandate never
  activated, a hosted top-up never granted its credits, and an unused Checkout
  page stayed pending. `stripe listen` forwards every event, which is why
  local testing passed. New Stripe connections register them. **Existing
  connections:** add those four events to the "Tanso Webhook StripeController
  Endpoint" destination in the Stripe dashboard.

### Removed

- **Instance telemetry.** The daily anonymous ping and its receiver are gone;
  a self-hosted instance no longer calls out to Tanso. The
  `instance_telemetry` table is dropped on upgrade, and
  `TANSO_TELEMETRY_ENABLED` is ignored.

## 0.10.0 — 2026-09-20

The release where an agent gets all the way through on its own. Everything here
was found by running a real agent at the product with `agent-ready`, watching
where it stopped, and fixing that. It now discovers the catalog, signs up with
no email and no CAPTCHA, checks an entitlement, records usage, asks for a paid
plan, hands its principal one link to pay, and picks the account up once the
money lands. The only human step is the payment itself, which is the one step
that should need a human.

### Fixed

- **Subscribe by the plan key pricing.json publishes.** `POST /api/v1/client/subscriptions`
  demanded the plan UUID, which appears nowhere an agent can read, while
  `pricing.json` publishes `plans[].id` as the plan key. Sending the key reached
  `UUID.fromString` and surfaced as a 500. `planId` (or the alias `planKey`) now
  accepts either; a blank or unknown plan is a 400 that says what to send.
- **Credit purchase for a customer with no pool.** `POST /api/v1/client/credits/purchases`
  required a `creditPoolId` a freshly signed-up customer has no way to obtain.
  `creditPoolId` is now optional: send `denomination` and the pool is created on
  first purchase when the operator has priced it, or send nothing when the
  customer has exactly one pool. Ambiguity and missing prices are 400s that
  name the fix.
- **No payment processor is a 402, not a 500.** On an instance with no Stripe
  key the purchase path reached the Stripe client and threw a
  `NullPointerException`. It now answers 402 with `declineReason` and no
  `checkoutUrl`; `StripeClientFactory` fails with a message for every other
  path that reaches it without a key.
- **Paid subscribe in pass-through mode hands back the invoice link.** When Tanso
  handles billing and Stripe collects, a customer-key subscribe to a paid plan
  created an inactive subscription and a DUE invoice with nothing to pay it
  with; only the operator could mint the link. `POST /api/v1/client/subscriptions`
  now answers 402 with the hosted invoice URL as `checkoutUrl`, alongside the
  inactive subscription and the invoice, so the agent can hand the link to a
  human and poll until `isActive`.
- **Signup `nextSteps` carried a literal `{featureKey}` placeholder.** Replaced by
  `check_entitlement_template` plus a `check_entitlement_example` built from one
  of the plan's own features, and joined by `pricing`, `usage_summary`,
  `buy_credits` and `change_plan`.

- **`deploy/setup.sh` is re-runnable on a used stack.** The account seed inserts
  only what is absent, and the margin demo upserts its features, plans and
  customers instead of deleting them, so real subscriptions that point at the
  demo plans survive a re-seed. A failed seed now stops the script instead of
  being swallowed.

### Added

- **Agent onboarding v2.** Signup creates a provisional customer on the free
  plan with an expiry (`agentProvisionalDays`, 14 by default). Paying is the
  claim: the first completed checkout or paid invoice sets the customer to
  claimed and clears the expiry. Email is optional, stored as the owner contact
  only, never sent to, and never used to find an existing customer. A per-IP
  signup cap (`agentSignupPerIpCap`) sits next to the per-account cap. An
  optional `spend_mandate` on signup opens a Stripe Checkout setup session; on
  completion the cap applies to every active key of the customer. Every 402 and
  every limit or access 403 carries a gate envelope (`error.gate`,
  `error.action`, `error.url`, `error.poll`, `error.retry_after`, `error.detail`).
  New endpoints: `GET /api/v1/client/customers/{ref}/status` and
  `PUT /api/v1/client/customers/{ref}/owner`. A runbook at `/agent-signup.md`
  and a skills index at `/.well-known/agent-skills/index.json`, both linked
  from `llms.txt` and `agent.json`. An agent funnel report at
  `GET /api/v1/tanso/agent-funnel` with a console page. Three new account
  settings: `agentSignupPerIpCap`, `agentProvisionalDays`,
  `agentSpendMandateEnabled`.
- **Agent discovery from the host name alone.** `GET /llms.txt` and
  `GET /.well-known/agent.json` list every account that published a catalog,
  with its `pricing.json` and, when enabled, its signup URL. Before this, every
  non-API path answered 403 and an agent not handed the slug could not find
  the catalog.
- **`usage.unlimited` on entitlement checks.** An absent `limit` read the same as
  an unknown one; the response now says which.
- **402 documented in OpenAPI** on both `POST /api/v1/client/subscriptions` and
  `POST /api/v1/client/credits/purchases`, with the checkout-session polling
  path. The spec previously contained no 402 at all.

### Added

- **Usage survives the plan it was recorded on.**
  `GET /api/v1/client/customers/{ref}/usage` now also reports plans the customer
  has left, marked `status: "ended"` with an `endedAt`, for the last year; usage
  on an ended plan is read from the events themselves, since its entitlements
  are gone. A plan change used to take the period's record out of this response
  with it. Alongside it, `GET .../usage/history?from&to` returns what was
  recorded over an explicit window, grouped by subscription, feature and event
  name, with an event count per group to reconcile a total against, and
  optional `featureKey` and `subscriptionId` filters. Keyed by customer and
  window rather than by subscription, which is what keeps it readable after a
  subscription ends.

### Changed

- **`Idempotency-Key` works again.** The filter read the request body to hash it
  and passed on a wrapper that does not replay what was read, so every POST that
  carried the header reached the controller with an empty body and failed as
  400 "Malformed request body". The 400 was then stored, so retries with the
  same key replayed it. Found by an agent running end to end with agent-ready.
- A 402 whose `url` is a hosted invoice now sets `poll` to the customer's status
  URL instead of null.
- **A plan change an agent has to pay for is now a gate, not a silent grant.**
  `POST /api/v1/client/subscriptions/{id}/plan-change` used to swap the plan and
  grant its entitlements before the adjustment invoice was paid, and it never
  consulted the per-key budget, so a key with no budget could raise its own
  customer onto an expensive plan. An upgrade that costs money and comes from an
  API key now raises the adjustment invoice, leaves the subscription on its
  current plan and answers 402 with the invoice URL and the customer's status
  URL to poll. Paying the invoice completes the change. The proration amount is
  checked against the account's per-charge cap and the calling key's budget
  first, which answer 403 as elsewhere. Operators acting in the console keep the
  immediate upgrade, and Stripe-driven accounts are unchanged.
  Cancelling a plan change now voids the invoice behind it, in Stripe as well as
  in Tanso, so nobody can pay for a change that no longer exists; that covers
  the delete-scheduled-change endpoint, the console, scheduling a downgrade and
  retargeting the upgrade. Paying an invoice that is already void grants
  nothing and is logged for a refund. Paying the upgrade grants the new plan's
  credits, which the period's plan grant would otherwise have swallowed, and
  draws down the budget of the key that asked for the change. A change between
  an in-advance and an in-arrears plan is refused rather than silently doing
  nothing and answering 200.

- A customer key without the `purchase` scope now gets 403 `scope_denied`
  instead of `forbidden`. Cross-customer and role 403s keep the `forbidden`
  code but now carry the gate object (`gate: "scope"`, with `use_own_reference`
  or `request_scope` as the action).
- With Tanso handling billing, a paid subscribe from a customer that has no
  email answers 402 with `action: "nominate_owner"` and the owner endpoint as
  `url`, instead of a 500 from Stripe refusing to send the invoice. Nominating
  an owner also updates the mirrored Stripe customer's email.
- For agent-created customers where Tanso does the billing, retrying a paid
  subscribe is idempotent: before paying it returns the pending subscription
  and its DUE invoice, after paying it returns the active subscription with
  201. Stripe-managed modes and calls with a payment method are unchanged.
- The discovery documents (`/llms.txt`, `/agent-signup.md`, `agent.json`, the
  skills index) no longer set `produces`, so a client sending
  `Accept: application/json` gets the document instead of a 406.

## 0.9.0 — 2026-08-21

The release where the customer buying from your product stops having to be a
person, and where you can put a ceiling on what a non-person spends.

### Added

- **Per-key spend budgets.** `PUT /api/v1/client/customers/{ref}/keys/{keyId}/budget`
  caps credits and money independently over a rolling `DAY`/`WEEK`/`MONTH`/`TOTAL`
  window; either axis may be left unlimited. The budget bounds the key, not the
  pool, so one agent cannot drain the balance its siblings draw on. Breaching
  returns 403 `budget_exceeded` with the limit, spend, remainder, and reset time.
  Only the tenant may set or clear a budget; a customer key can read its own.
  Also reachable over MCP as `getMyBudget`. A budget carries a warning mark
  (80% by default) and reports `percentUsed`/`alerting`/`alertingSince` once
  crossed, so an agent can slow down instead of discovering the ceiling by
  being refused.
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
