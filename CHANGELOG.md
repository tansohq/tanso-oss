# Changelog

Notable changes to Tanso Core. Releases before 0.9.0 are recorded only as git
tags; this file starts where the changelog does.

## Unreleased

### Fixed

- **`limits.spend_cap` is written out when it is null.** The runbook says a
  null `spend_cap` means no per-charge limit and that null fields are never
  omitted, but the signup and status bodies dropped the field when the operator
  had set no `agentMaxTopupAmount`. It now comes back as `"spend_cap": null`.
- **Every 402 carries its error id in `error.detail`.** The runbook promises
  it, but the 402s built by the subscription, plan-change and credit-purchase
  endpoints (`complete_checkout`, `nominate_owner`, no payment processor)
  answered `detail: null`, so an agent had nothing to quote to the operator.
  They now carry `errorId=<uuid>`, and the id is logged.
- **Tanso records a Stripe invoice at Stripe's amount.** On `STRIPE_DRIVEN`,
  and for non-accumulate plans on `STRIPE_INTEGRATION`, the webhook copy of a
  Stripe invoice went through `createNewInvoice`. On a plan with a
  usage-priced feature, that reset the amount to the subscription's current plan
  price plus Tanso's usage. A $30.00 upgrade proration was stored as `0.00` when
  the plan had not moved yet, which also left the customer unclaimed, and as
  `60.00` (the new plan's full price) when it had. On `STRIPE_INTEGRATION`, a
  $29.99 proration was stored as `30.00`. The copy now takes Stripe's
  `amount_due` and Stripe's invoice lines as its items. Accumulate-mode invoices,
  where Tanso computes the charge itself, are unchanged.
- **An agent customer on `STRIPE_DRIVEN` is claimed when a saved card pays.**
  Stripe charges a saved card while it creates the invoice, so `invoice.created`
  already reports it paid. Tanso mirrored it straight to `PAID`, which skipped
  `markInvoiceAsPaid`: the customer stayed `provisional` after paying, and
  `invoice.paid` found the invoice already `PAID` and did nothing. The mirror is
  now written `DUE` and marked paid in the same transaction. When
  `invoice.paid` arrived before `invoice.created`, it created the mirror and
  then marked it paid in a new transaction that could not see it yet, so the
  webhook failed with `Invoice not found` and answered 400; it now marks the
  mirror it created in its own transaction.
- **Upgrading between plans with a usage-priced feature no longer fails in
  Stripe.** A plan with a usage-priced feature has a metered Stripe price, and
  a paid one also has a licensed base price. A plan change put the plan's newest
  price on the subscription's first item, so moving from a free plan (one
  metered item) to a paid plan asked Stripe to turn a metered item into a
  licensed one. Stripe refused ("You cannot change the usage type of the price
  attached to your subscription item") and the upgrade answered 500, on
  `STRIPE_DRIVEN` and `STRIPE_INTEGRATION`, for agent and tenant keys alike.
  Each item now moves to the new plan's price of its own usage type, a price
  with no matching item is added, and an item whose type the new plan lacks is
  removed. The same applies to the non-charging plan-change sync and to
  restoring the price after a dropped `send_invoice` upgrade. A retry of an
  upgrade Stripe already applied reads the subscription's latest invoice
  instead of repeating the update under the same idempotency key.

## 0.11.0 — 2026-09-22

The release where money moves safely. A review of 0.10.0 found places where an
agent could reach a paid plan before anyone paid, a customer could be charged
twice or granted credits twice, a Stripe charge could land while Tanso rolled
back, and Stripe never delivered some of the events Tanso waits for. This
release fixes those, stores the human's spend approval once per customer under
an operator ceiling, and makes upgrades charge the prorated amount when they
happen.

### Upgrading from 0.10.0

- **Set `agentMaxMandateAmount` if you use spend mandates.** Mandates answer
  `unavailable` until the account has a ceiling, and turning them on without
  one is refused.
- **Tanso edits your Stripe webhook destination on startup.** It adds the
  events it handles that are missing. It never removes any. Turn it off with
  `app.stripe-event-destination-sync-enabled=false`.
- **Upgrades on Stripe-billed accounts charge immediately.** On
  `STRIPE_INTEGRATION` this applies to every caller, and a tenant-key upgrade
  that waits on payment now answers `202` with `status: "payment_pending"`
  instead of `200`.
- **Check your proxy.** The per-IP signup cap reads the leftmost
  `X-Forwarded-For` entry; the proxy must overwrite that header.
- **Instance telemetry is gone.** `TANSO_TELEMETRY_ENABLED` is ignored and the
  `instance_telemetry` table is dropped.

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

- **STRIPE_INTEGRATION upgrades now charge the prorated amount immediately,
  for every caller.** Agent keys, tenant (`sk_`) keys and the MCP tool all go
  through the charge-first path STRIPE_DRIVEN agent upgrades already used:
  Stripe invoices the proration during the call and the plan moves only once
  it is paid. Before, Tanso recorded a pending change, sent Stripe a
  `create_prorations` price change and answered 200, while the plan only moved
  when the next renewal invoice was paid. Applies to in-advance to in-advance
  upgrades that cost money; downgrades stay end of period and in-arrears
  upgrades are unchanged. There is no setting for it. Operators on
  STRIPE_DRIVEN keep the immediate swap.
- **What a plan-change call answers while an upgrade waits on payment.** A
  customer (`ck_`) key gets the 402 `complete_checkout` gate with Stripe's
  hosted invoice. A tenant (`sk_`) key gets `202` with
  `data: { "status": "payment_pending", "paymentUrl": ... }`, not a gate.
- **Accumulate-mode plans (billed by emailed invoice) upgrade too.** Their
  Stripe subscriptions use `send_invoice`, where Stripe does not support
  pending updates. The upgrade is invoiced without `pending_if_incomplete`:
  Stripe moves to the new price and sends the prorated invoice, and Tanso
  leaves the plan where it is until that invoice is paid. Cancelling or
  replacing such an upgrade voids the invoice and puts the Stripe price back.
- **`nominate_owner` only where an email is needed.** An agent upgrade on
  STRIPE_DRIVEN or STRIPE_INTEGRATION no longer asks for an owner email when
  the customer has a saved card, since Stripe charges the card. Pass-through
  accounts, and Stripe-billed customers without a card, still get it.
- **A completed spend mandate records the owner email.** When the customer has
  no email, the one the principal typed on the Checkout page is stored as the
  owner and pushed to the Stripe customer, so receipts and dunning reach them.
  An email already on the customer is kept.
- **A spend mandate is stored once per customer, not copied onto its keys.**
  It used to be written as a money budget on every active key. Spend is summed
  per key, so two keys meant twice the approved amount could be charged, and
  the write overwrote any budget the operator had set on those keys. The
  mandate now lives on the customer (`mandate_amount`, `mandate_period`,
  `mandate_started_at`) and every off-session charge, plus the upgrade
  proration check, must fit both the calling key's budget and the mandate,
  where the mandate counts off-session spend across all of the customer's keys.
  Hosted checkout, where a human pays in person, is not checked against the
  mandate.
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

- **A paid subscribe with a saved card can no longer be lost to a failed
  database commit.** On STRIPE_INTEGRATION, subscribing with a saved card
  created and charged the Stripe subscription inside the database transaction.
  A commit that failed after Stripe charged lost the spend record against the
  key, and a retry could create and charge a second Stripe subscription. Tanso
  now commits a pending charge first (a `checkout_sessions` row with purpose
  `DIRECT_SUBSCRIPTION`), calls Stripe with no transaction open and an
  idempotency key made from that row's id, then records the subscription in a
  separate transaction. If that last step fails,
  `customer.subscription.created` creates the subscription and records the
  spend from the pending row. A retry reuses the row, so Stripe returns the
  subscription it already created. A card Stripe declines marks the row
  FAILED, so the next attempt starts fresh. Callers and responses are
  unchanged.
- **A voided or written-off upgrade invoice now ends the upgrade even when
  its id was never recorded.** `invoice.voided` and
  `invoice.marked_uncollectible` matched a waiting upgrade only by the
  recorded Stripe invoice id. If recording failed after Stripe raised the
  invoice, the upgrade stayed pending and Stripe kept the new price. They now
  use the same fallback as `invoice.paid`: an upgrade invoice
  (`billing_reason: subscription_update`) for the subscription ends its
  pending charge-first upgrade.
- **A charged upgrade can no longer be lost to a failed database commit.**
  Charge-first upgrades called Stripe inside the database transaction, so a
  commit that failed after Stripe charged left the customer paying with no
  change in Tanso for `invoice.paid` to complete. The pending change is now
  committed before Stripe is called, Stripe is called with no transaction
  open, and its answer is recorded in a separate transaction. If that last
  step fails, `invoice.paid` for the upgrade invoice still completes the
  change, and a retry gets Stripe's first answer back: the Stripe call carries
  an idempotency key made from the change's id. A change Stripe refused is
  marked FAILED so the next attempt starts fresh. Schema: new column
  `subscription_scheduled_changes.stripe_charge_first` (changelog
  `2026.09.23.20`).
- **A hosted page a human pays is no longer refused by the key budget or the
  mandate.** Buying credits without a card, subscribing to a paid plan without
  a card, and a subscribe or upgrade on pass-through all hand the agent a
  Stripe page a human pays in person. The key budget used to run first, so an
  exhausted budget answered `budget_exceeded` or `spend_cap_exceeded` instead
  of the page. Now only the operator's per-charge cap (`agentMaxTopupAmount`)
  applies there. Off-session charges (a saved card charged by credit top-up,
  subscribe, or an upgrade where Stripe runs the billing) still have to fit the
  key budget and the mandate.
- **Money paid on a hosted page no longer uses up the mandate.** Spend records
  now say how the money moved: `OFF_SESSION` or `HOSTED` (new column
  `api_key_spend_records.channel`, existing rows are `OFF_SESSION`). The
  mandate, and `spend_mandate.spent` on `/status`, count only `OFF_SESSION`.
  The key budget, and `spend.spent`, still count both, since the key caused the
  spend either way. A Stripe invoice paid later for an upgrade counts as
  `HOSTED` only when Stripe emails invoices for that subscription; otherwise
  Stripe may have retried the saved card, so it counts as `OFF_SESSION`.
- **An unpaid upgrade ends when its Stripe invoice is voided or written off.**
  On STRIPE_DRIVEN and STRIPE_INTEGRATION, a charge-first upgrade on a
  send_invoice subscription waited on `invoice.paid` forever, and Stripe stayed
  on the new price. `invoice.voided` now cancels the waiting upgrade and puts
  Stripe back on the old price. `invoice.marked_uncollectible` does the same
  and also voids the invoice, since Stripe still accepts payment on it. Tanso's
  copy of the invoice follows: VOID, or PAST_DUE when written off. Both events
  are registered on new connections and added to existing ones at startup.
- **A past-due invoice for the current period is voided on cancel, downgrade
  and free-plan retirement.** Only DUE and PENDING invoices were voided, so a
  PAST_DUE invoice for a period still running, and its Stripe copy, stayed
  payable after the subscription behind it was gone. The same goes for the
  past-due proration of an upgrade still waiting on payment. A PAST_DUE
  invoice for a period that has already ended stays payable: that is money
  owed for service already used.
- **A renewal invoice no longer completes an unpaid upgrade.** On
  STRIPE_INTEGRATION any paid invoice for the subscription fulfilled the
  waiting upgrade. A charge-first upgrade now completes only on the invoice
  Stripe raised for it.
- **A failed first charge no longer kills the upgrade's invoice.** On
  STRIPE_INTEGRATION a failed payment reverted the Stripe price and marked
  the change FAILED, so the hosted invoice the caller had just been handed led
  nowhere. The charge-first change now stays payable until it is paid,
  expires (`customer.subscription.pending_update_expired`, now handled on
  STRIPE_INTEGRATION too) or is cancelled.
- **An upgrade's proration invoice no longer moves the billing period.** On
  STRIPE_INTEGRATION the period was read off the first invoice line, which for
  a proration invoice starts now, and accumulate-mode plans billed a full
  period's base price and usage on it. Invoices with `billing_reason:
  subscription_update` now keep the subscription's period and are mirrored as
  they are.
- **The runbook said nothing is sent to the owner email.** Tanso sends
  nothing, but Stripe may send receipts and invoices to it.
- **A charge larger than the whole key budget no longer says `wait`.** It
  answered `budget_exceeded` / `wait` with a `retry_after`, but no window reset
  lets it through. It now answers `spend_cap_exceeded` / `raise_spend_cap`
  with `retry_after: null`. A charge that fits the budget but not what is left
  of this window still answers `wait`.
- **After Stripe Checkout, the human no longer lands on example.com.** With
  no `stripeCheckoutSuccessUrl` / `stripeCheckoutCancelUrl` set, card setup,
  spend mandates, credit top-ups and plan checkouts sent the human to
  `https://example.com/success` or `/cancel`. Tanso now serves its own pages,
  `/public/checkout/complete` and `/public/checkout/cancelled`, on the host
  the checkout was requested through, and uses them by default. URLs the
  operator sets still win.

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
