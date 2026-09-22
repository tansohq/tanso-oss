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
- **Operator ceiling on spend mandates.** New account setting
  `agentMaxMandateAmount`: the largest `max_amount` per period an agent may ask
  its principal to approve. A larger ask, at signup or on the new endpoint, is
  a 400 that names the limit (`spend_mandate.max_amount 500.00 is above this
  account's limit of 200.00 USD; ask for 200.00 or less.`); it is rejected,
  never clamped. **Upgrading: set `agentMaxMandateAmount` before mandates work
  again.** Enabling `agentSpendMandateEnabled` without it is refused with 400,
  and an account that already has the flag on answers `unavailable` to every
  mandate request until the ceiling is set.
- **Ask for a new or higher mandate.** `POST /api/v1/client/customers/{ref}/spend-mandate`
  (customer key with `purchase`) opens a new setup page for `max_amount` per
  `period`. Completing it replaces the customer's mandate and claims the
  account, as at signup.
- **The setup page says what the principal approves.** The Stripe page shows
  "You're saving this card so your agent can pay {account name} without asking
  you, up to {amount} {CURRENCY} per {period}. Anything above that comes back
  to you for approval." next to the save button.
- **`raise_mandate` gate action.** A single off-session charge larger than the
  whole mandate answers 403 `spend_cap_exceeded` / `raise_mandate` with
  `retry_after: null` and `url` set to the spend-mandate endpoint.

### Changed

- **A spend mandate is stored once per customer, not copied onto its keys.**
  It used to be written as a money budget on every active key. Spend is summed
  per key, so two keys meant twice the approved amount could be charged, and
  the write overwrote any budget the operator had set on those keys. The
  mandate now lives on the customer (`mandate_amount`, `mandate_period`,
  `mandate_started_at`) and every off-session charge, plus the upgrade
  proration check, must fit both the calling key's budget and the mandate,
  where the mandate counts spend across all of the customer's keys. Hosted
  checkout, where a human pays in person, is not checked against the mandate.
  `/status` reports `spend_mandate` with `spent`, `remaining`, `period` and
  `resets_at` from the customer.
- **Mandates activated before this release stay as the key budgets they were
  written as.** They are not migrated: those rows cannot be told apart from
  budgets an operator set by hand, and the period was never stored outside
  Stripe, so a migration could clear an operator's budget or guess the window
  wrong. Those customers show `spend_mandate.status: "active"` with only
  `max_amount`; asking for a new mandate moves them onto the customer model,
  and the operator can clear the old key budgets from the console.
- **Gate messages.** The per-charge cap message now says "ask the operator to
  raise the cap" (the operator sets `agentMaxTopupAmount`; the owner is the
  person who pays). The `GateError` schema no longer lists the unused `claim`
  gate or `claim_account` action and now lists `nominate_owner`,
  `use_own_reference` and `raise_mandate`.

### Fixed

- **A charge larger than the whole key budget no longer says `wait`.** It
  answered `budget_exceeded` / `wait` with a `retry_after`, but no window reset
  lets it through. It now answers `spend_cap_exceeded` / `raise_spend_cap`
  with `retry_after: null`. A charge that fits the budget but not what is left
  of this window still answers `wait`.

### Fixed

- **Stripe now delivers the checkout and card-setup events Tanso handles.**
  The webhook endpoint Tanso registers left out `checkout.session.completed`,
  `checkout.session.expired`, `setup_intent.succeeded` and
  `payment_intent.succeeded`, so on a real deployment a spend mandate never
  activated, a hosted top-up never granted its credits, and an unused Checkout
  page stayed pending. `stripe listen` forwards every event, which is why
  local testing passed. New Stripe connections register them, and on startup
  Tanso adds any missing ones to each existing connection's event destination
  (events added by hand are kept). Tanso now stores the destination's id
  (`account_settings.stripe_event_destination_id`); for connections made
  before that, it finds the destination by its Tanso name and the webhook URL
  ending in the account id. If none is found, or Stripe refuses the update, the
  log says so for that account and the others still update.
- **An agent can change plans on a Stripe-driven account again.** 0.10.0 held
  a paid plan change made with a customer key until Tanso's adjustment invoice
  was paid, and that applied to Stripe-driven accounts too, where nothing pays
  a Tanso invoice. The agent got a 402 saying no payment processor was
  connected, and an unpayable invoice was left behind. Tanso no longer raises
  its own adjustment invoice on Stripe-driven accounts; Stripe charges the
  difference, and the change counts against the calling key's budget.
- **On a Stripe-driven account, an agent's upgrade is paid for before the plan
  moves.** A plan change made with an API key now asks Stripe to invoice the
  prorated difference and charge the saved card at once
  (`proration_behavior=always_invoice`, `payment_behavior=pending_if_incomplete`).
  If the charge goes through, the plan changes in the same call. If there is
  no card or the charge fails, Stripe keeps the old price, the call answers
  402 with Stripe's hosted invoice as `url` and the customer status URL as
  `poll`, and the plan changes when that invoice is paid (`invoice.paid`).
  If nobody pays before Stripe expires the change (about 23 hours), the
  pending change is cancelled (`customer.subscription.pending_update_expired`).
  Before, the new price was billed at the next renewal, so the agent had the
  paid plan for up to a period before anyone paid, and a failed renewal
  never took it back. Operator changes (no API key) still switch at once.
- **Upgrades on `STRIPE_INTEGRATION` accounts now reach Stripe.** The upgrade
  waits on payment before Tanso swaps the plan, but the Stripe price update
  read the plan off the not-yet-swapped subscription, so Stripe was sent the
  price it already had. No proration was billed, and the pending upgrade only
  completed when some later invoice was paid, at the old price. The price
  update now names the target plan explicitly. When the upgrade completes,
  the customer also gets the new plan's credit difference, which the
  previous fulfilment path did not grant.
- **Concurrent agent signups no longer get past the signup caps.** The
  per-account and per-IP counts ran before, and outside, the transaction that
  inserts the customer, so a burst of simultaneous signups all read a count
  under the cap and all got in. The counts and the insert now run in one
  transaction under a Postgres advisory lock on the account, plus one on the
  address when there is one. The README now says what the per-IP cap needs
  from a proxy: it counts the leftmost `X-Forwarded-For`, so the proxy must
  overwrite that header rather than append to it.
- **Two concurrent plan changes no longer raise two payable invoices.** An
  agent that retried an upgrade after a timeout could send the same request
  twice; both calls found no pending upgrade and each created its own
  adjustment invoice. `upgradeSubscription` now takes a row lock on the
  subscription first, so the second call waits and then returns the first
  call's invoice.
- **One payment reported twice no longer grants upgrade credits twice.**
  Stripe sends both `invoice.paid` and `invoice.payment_succeeded` for one
  payment, with different event ids, so the webhook event-id check let both
  through. Marking an invoice paid now locks the invoice row and does nothing
  if it is already `PAID`, the pending upgrade row is locked while it is
  fulfilled, and the upgrade credit delta's idempotency key is the scheduled
  change id instead of the current time.
- **Marking an upgrade's adjustment invoice paid granted the new plan's full
  credits again.** Mark-paid moved the subscription's billing period to the
  upgrade moment, which changed the key the period credit grant is idempotent
  on, so the whole new-plan allocation landed on top of the upgrade delta and
  the billing cycle shifted. Paying an adjustment invoice now leaves the period
  alone and skips the period grant; the upgrade's credits still come from the
  delta. This also covered operator upgrades made from the console.
- **A free plan retired by a paid plan kept its pending upgrade invoice
  payable.** Paying that leftover invoice later swapped the plan on the retired
  subscription and granted its entitlements again. Retiring now voids the
  subscription's outstanding invoices, including a past-due upgrade invoice, and
  cancels its scheduled changes.
- **Cancelling a subscription left its Stripe invoices payable.** Voiding a
  subscription's outstanding invoices on cancel or downgrade only changed
  Tanso's copy. It now also voids the hosted Stripe invoice, the same way a
  replaced upgrade's invoice already was.
- **`GET /usage` reports a plan as ended only once it has ended.** A paid plan
  waiting on its first payment was listed as `ended`, with an end date in the
  future. A plan cancelled at the end of its period showed the time the cancel
  was asked for as its end, and left out usage recorded between then and the
  real end.

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
