# Tanso agent signup runbook

How an agent gets an account on this instance, uses the free plan, and pays when it needs more.
No CAPTCHA, no email confirmation, no human in the loop until money moves.

## Catalogs

{catalogs}
## 1. Read the catalog

```
curl {base}/public/v1/catalog/{slug}/pricing.json
```

Plans, features, credit weights and governance flags. Plan keys from this document are what
you pass to `POST /api/v1/client/subscriptions` later.

## 2. Sign up

Every field is optional. An empty body works.

```
curl -X POST {base}/public/v1/catalog/{slug}/signup \
  -H 'Content-Type: application/json' \
  -d '{}'
```

With an owner email, a display name and a spend mandate:

```
curl -X POST {base}/public/v1/catalog/{slug}/signup \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "ops@example.com",
    "name": "research-agent-1",
    "spend_mandate": { "max_amount": 50.00, "currency": "usd", "period": "month" }
  }'
```

- `email`: optional. Must be a valid address if present. Recorded as the owner contact. Tanso sends
  nothing; Stripe may send receipts and invoices to it. It never resolves to an existing customer:
  every signup creates a new provisional customer, even with an email seen before. Without one, the
  email your principal types on the spend mandate page is recorded instead.
- `name`: optional, up to 100 characters.
- `spend_mandate`: optional. `currency` must equal the account currency. `max_amount` is required
  when the object is present and must not be above the operator's limit. `period` is `day`, `week`
  or `month` (default `month`). See step 3 for what comes back.

Errors: `404 not_found` when there is no catalog at that slug or signup is not enabled on it (same
body for both, on purpose). `400 validation_failed` for a bad email, a `spend_mandate` without
`max_amount`, a currency that does not match the account, or a `max_amount` above the operator's
limit. That last message names the limit, for example: `spend_mandate.max_amount 500.00 is above
this account's limit of 200.00 USD; ask for 200.00 or less.` Ask again with the limit or less.
Nothing is created by a 400. `429 rate_limited` when a rate limit is hit (see Rate limits).

## 3. The response (201)

```json
{
  "success": true,
  "data": {
    "customerReferenceId": "agent_7f3c9a2e",
    "apiKey": "ck_test_9b1d...e4",
    "apiKeyScopes": ["read", "purchase"],
    "plan": "free",
    "status": "provisional",
    "expires_at": "2026-10-01T00:00:00Z",
    "limits": {
      "features": {
        "ai.chat": { "included": 5, "period": "month", "unlimited": false }
      },
      "spend_cap": null,
      "currency": "usd"
    },
    "spend_mandate": {
      "status": "pending",
      "setup_url": "https://checkout.stripe.com/c/pay/cs_test_...",
      "max_amount": 50.00,
      "currency": "usd",
      "period": "month"
    },
    "status_url": "{base}/api/v1/client/customers/agent_7f3c9a2e/status",
    "owner_url": "{base}/api/v1/client/customers/agent_7f3c9a2e/owner",
    "nextSteps": {
      "base_url": "{base}",
      "pricing": "{base}/public/v1/catalog/{slug}/pricing.json",
      "check_entitlement_template": "{base}/api/v1/client/entitlements/agent_7f3c9a2e/{featureKey}",
      "check_entitlement_example": "{base}/api/v1/client/entitlements/agent_7f3c9a2e/ai.chat",
      "record_usage": "{base}/api/v1/client/events",
      "usage_summary": "{base}/api/v1/client/customers/agent_7f3c9a2e/usage",
      "usage_history": "{base}/api/v1/client/customers/agent_7f3c9a2e/usage/history",
      "usage_events": "{base}/api/v1/client/customers/agent_7f3c9a2e/usage/events",
      "credit_balances": "{base}/api/v1/client/credits/agent_7f3c9a2e/pools",
      "buy_credits": "{base}/api/v1/client/credits/purchases",
      "change_plan": "{base}/api/v1/client/subscriptions",
      "runbook": "{base}/agent-signup.md",
      "docs": "{base}/swagger-ui.html"
    }
  }
}
```

`expires_at` is null once the account is claimed. `check_entitlement_example` is present only when
the plan has at least one feature. Null fields are written out (`"spend_mandate": null`,
`"claimed_at": null`), never omitted, in both the signup and the status body.

`limits.features` is keyed by feature key. `included` is null when the plan does not meter that
feature; `unlimited: true` means no cap; `period` comes from the plan interval: `"month"` or
`"N months"`. `limits.spend_cap` is the largest single charge the operator lets an agent start
(`agentMaxTopupAmount`), off-session or on a hosted page; null means no per-charge limit. It is distinct from the calling key's
budget, which the status endpoint reports as `spend.cap`.

`spend_mandate` is:

- `null` when the request did not ask for one.
- `{ "status": "unavailable", "max_amount", "currency", "period" }` when you asked but the operator
  has not enabled mandates, Stripe is not connected, or Stripe failed to open a session. The signup
  still succeeds.
- `{ "status": "pending", "setup_url", "max_amount", "currency", "period" }` when a Stripe Checkout
  page was opened. Hand `setup_url` to your principal, the human who pays. The page tells them they
  are letting you pay without asking, up to `max_amount` per `period`. When they finish, the card is
  saved, the mandate is stored on your customer, and the customer is claimed.
  Afterwards Stripe shows them a page that says they can close the tab; it sends you nothing.

The mandate covers the whole customer, not one key. Every charge made off-session with the saved
card (buying credits, subscribing to a paid plan, the prorated part of an upgrade) must fit two
limits: the calling key's own budget, if the operator set one, and the mandate. The mandate counts
what all of the customer's keys spent off-session in the current window together. Windows start when
the mandate starts and repeat every day, 7 days or 30 days.

A hosted page never hits the mandate or the key budget. When a call hands you a Stripe Checkout page
or a Stripe hosted invoice (a 402 with `action: complete_checkout`), a human pays it in person, and
that is their approval. Neither limit refuses the page, and money paid on it does not count toward
the mandate. It does count toward the calling key's budget, so `spend.spent` on the status endpoint
includes it. The operator's per-charge cap (`limits.spend_cap`) still applies to a hosted page. One
exception: an upgrade where Stripe runs the billing tries the saved card in the same call, so both
limits are checked before it, even if it ends in a hosted invoice. When that invoice is paid later,
it stays out of the mandate only if Stripe emails invoices for the subscription; otherwise Stripe
may have retried the saved card, so it counts as off-session.

## 4. Store the key once

`apiKey` is returned exactly once. It is not retrievable later. Store it before doing anything
else. Send it on every call:

```
-H 'X-API-Key: ck_test_9b1d...e4'
```

The key is pinned to its own customer. Reading another customer's data returns 403.

## 5. Check status

```
curl {base}/api/v1/client/customers/agent_7f3c9a2e/status \
  -H 'X-API-Key: ck_test_9b1d...e4'
```

```json
{
  "success": true,
  "data": {
    "customerReferenceId": "agent_7f3c9a2e",
    "status": "provisional",
    "expires_at": "2026-10-01T00:00:00Z",
    "claimed_at": null,
    "plan": "free",
    "limits": { "features": { "ai.chat": { "included": 5, "period": "month", "unlimited": false } }, "spend_cap": null, "currency": "usd" },
    "remaining": { "ai.chat": 3 },
    "spend": null,
    "owner_email": "ops@example.com",
    "spend_mandate": {
      "status": "active",
      "setup_url": null,
      "max_amount": 50.00,
      "spent": 12.00,
      "remaining": 38.00,
      "period": "month",
      "resets_at": "2026-10-21T09:14:00Z",
      "currency": "usd"
    }
  }
}
```

`status` is `provisional`, `claimed` or `expired`. `remaining` is per feature key, null when
the feature is unmetered. `spend` is null until the calling key has a budget; then it has `cap`,
`spent`, `remaining`, `currency` and `resets_at`. `limits.features[].period` uses the same
vocabulary as signup (`"month"` or `"N months"`).

`spend_mandate.status` is `none`, `pending`, `active` or `expired`. All fields are always present.
`spent`, `remaining`, `period` and `resets_at` are filled while a mandate is `active`; they count
off-session spend across all of the customer's keys. What a human paid on a hosted page is not in
`spend_mandate.spent`; it is in `spend.spent`, the calling key's budget. `setup_url` is set while a setup page waits for
your principal, including a raise you asked for while an older mandate is still `active` (the older
one keeps applying until the new page is completed). `expired` means the Stripe setup page expired
unused (24 hours); ask for a new mandate (step 5a).

## 5a. Ask for a new mandate

For a first mandate after signup, a higher limit, or after a setup page expired:

```
curl -X POST {base}/api/v1/client/customers/agent_7f3c9a2e/spend-mandate \
  -H 'X-API-Key: ck_test_9b1d...e4' -H 'Content-Type: application/json' \
  -d '{ "max_amount": 100.00, "currency": "usd", "period": "month" }'
```

The key needs the `purchase` scope. The body and the checks are the same as `spend_mandate` at
signup, including the operator's limit (400 above it). The response `data` is the same
`spend_mandate` object as in step 3: hand `setup_url` to your principal. When they finish, the new
mandate replaces the old one. Keeping the same `period` keeps the current window, so raising the
amount does not reset what was already spent.

## 6. Use the free plan

Check before you do work, record after:

```
curl {base}/api/v1/client/entitlements/agent_7f3c9a2e/ai.chat \
  -H 'X-API-Key: ck_test_9b1d...e4'

curl -X POST {base}/api/v1/client/events \
  -H 'X-API-Key: ck_test_9b1d...e4' -H 'Content-Type: application/json' \
  -d '{ "customerReferenceId": "agent_7f3c9a2e", "featureKey": "ai.chat",
        "eventName": "chat completion", "eventIdempotencyKey": "evt-1", "usageUnits": 1 }'
```

Usage and burndown: `GET {base}/api/v1/client/customers/agent_7f3c9a2e/usage`.
Past usage, for audit: `GET {base}/api/v1/client/customers/agent_7f3c9a2e/usage/history?from=&to=`.
The events behind a total: `GET {base}/api/v1/client/customers/agent_7f3c9a2e/usage/events`.
Usage outlives the plan it was recorded on, so a period stays readable after a plan change.
Buy credits: `POST {base}/api/v1/client/credits/purchases`.
Change plan: `POST {base}/api/v1/client/subscriptions` with a plan key from pricing.json.
Change an existing subscription in place: `POST {base}/api/v1/client/subscriptions/{subscriptionId}/plan-change`.
An upgrade that costs money answers 402 with the invoice to pay; the plan swaps when the invoice is paid.
Where Stripe runs the billing, Stripe charges the prorated amount during the call: a 200 means it was
paid and the new plan is already yours. You get the 402 instead when there is no card, the charge did not
go through, or the operator's Stripe sends invoices by email rather than charging a card. `url` is then
Stripe's invoice; the plan moves when it is paid. A downgrade is scheduled for the end of the period and
charges nothing.
Mutating requests accept an `Idempotency-Key` header; replays return the stored response for 24h.

## 7. Gates: 402 and 403

Every 402, and every 403 caused by a limit or access rule, has this `error` object. A 402 also
keeps its normal `data` payload (the subscription or credit purchase result). `detail` always
carries the error id to quote to the operator.

```json
{
  "success": false,
  "error": {
    "code": "payment_required",
    "gate": "payment",
    "action": "complete_checkout",
    "url": "https://checkout.stripe.com/c/pay/cs_test_...",
    "poll": "{base}/api/v1/client/checkout-sessions/cs_test_...",
    "retry_after": null,
    "message": "Payment is required: hand url to a human to complete checkout, then poll for the outcome.",
    "detail": "errorId=3f6c1b2e-8d0a-4c7e-9a51-2b7d4e0f6a13"
  },
  "data": { "checkoutUrl": "https://checkout.stripe.com/c/pay/cs_test_...", "checkoutSessionId": "cs_test_..." }
}
```

When the operator's billing sends a Stripe hosted invoice instead of a Checkout page, `url` is that
invoice and `poll` is your status URL: poll it until `plan` shows the new plan and `status` is
`claimed`. When no payment processor is connected to the instance, the 402 has `url` and `poll` null and
`message` is "Payment is required but no payment processor is connected to this instance; contact
the operator."

A budget gate looks like this:

```json
{
  "success": false,
  "error": {
    "code": "budget_exceeded",
    "gate": "budget",
    "action": "wait",
    "url": null,
    "poll": null,
    "retry_after": 43200,
    "message": "This API key's spend budget of 50.00 would be exceeded: 48.00 already used, 5.00 requested, window resets at 2026-10-01T00:00:00Z; wait for the window to reset or ask the key owner to raise the budget.",
    "detail": "errorId=9a1d7c04-5e2b-4f38-b6c1-0d8e2f7a4b55"
  }
}
```

What to do per row:

| status | code | gate | action | do this |
|--------|------|------|--------|---------|
| 402 | `payment_required` | `payment` | `complete_checkout` | Hand `url` to the human who owns the account. Poll `poll` (`{base}/api/v1/client/checkout-sessions/{id}`) until the session is complete, then retry the call. |
| 402 | `payment_required` | `payment` | `complete_checkout` | On `plan-change`: the upgrade is raised but not granted. Hand `url` to the human, poll `poll` (the customer's status URL) until `plan` shows the new plan. Do not retry the call; paying completes it. A Stripe invoice for a declined card, left unpaid for about 23 hours, expires and the change is dropped; ask again after that. |
| 402 | `payment_required` | `payment` | `nominate_owner` | Only on accounts where Tanso sends Stripe invoices by email, when no card is saved. `PUT {"email": ...}` to `url`, then retry. A completed spend mandate or a signup email avoids this. |
| 403 | `budget_exceeded` | `budget` | `wait` | The key's budget window, or the mandate's window, is used up, but the charge fits once it resets. Only an off-session charge gets this; a hosted page never does. Wait `retry_after` seconds, then retry. Do not retry in a loop. |
| 403 | `spend_cap_exceeded` | `budget` | `raise_spend_cap` | One charge is above the operator's per-charge cap (`limits.spend_cap`), hosted or not, or an off-session charge is larger than the key's whole budget. `retry_after` is null; waiting will not help. Ask the operator to raise the cap, or make a smaller purchase. |
| 403 | `spend_cap_exceeded` | `budget` | `raise_mandate` | One off-session charge is larger than the whole mandate your principal approved. `retry_after` is null; waiting will not help. `url` is the spend-mandate endpoint: `POST` a higher `max_amount` to it (step 5a) and hand the new `setup_url` to your principal, or buy less. |
| 403 | `forbidden` | `scope` | `use_own_reference` | The key belongs to another customer. Message: "This API key belongs to another customer; use your own customerReferenceId or omit it." |
| 403 | `scope_denied` | `scope` | `request_scope` | The key lacks the `purchase` scope. Ask the operator for a key with it. |
| 403 | `forbidden` | `scope` | `request_scope` | The endpoint is not open to this kind of key. Use a tenant key or ask the account owner. |

There is no claim gate. Nothing is closed to a provisional account; paying is the claim.

`url`, `poll` and `retry_after` are null when they do not apply. `message` is one sentence that
says what to do. It is safe to show it to a human.

## 8. Set the owner

Optional. Records a human contact on the customer. Tanso sends nothing; Stripe may send receipts and
invoices to it.

```
curl -X PUT {base}/api/v1/client/customers/agent_7f3c9a2e/owner \
  -H 'X-API-Key: ck_test_9b1d...e4' -H 'Content-Type: application/json' \
  -d '{ "email": "ops@example.com" }'
```

Returns the status body from step 5 with `owner_email` set.

## 9. Expiry and claiming

A provisional account expires at `expires_at`, 14 days after signup by default (the operator can
change the window). Paying claims the account: the first completed checkout or paid invoice sets
`status` to `claimed` and `expires_at` to null. Completing a spend mandate setup also claims it.

After the nightly job (03:30 UTC) an expired customer's keys are revoked, so your next call,
including `status_url`, returns `401 unauthorized`. If you get 401 on every endpoint after your
expiry date, the account expired. Sign up again. Usage history is kept.

A payment that completes after expiry re-claims the customer, but the old keys stay revoked; the
operator issues a new key from the console.

## Rate limits

- Per catalog: the operator's hourly signup cap (10 per hour by default).
- Per IP: 5 signups per hour by default. The IP is the connection's remote address; behind a proxy
  the operator sets `server.forward-headers-strategy`.

Both return `429` with a `Retry-After` header in seconds and one of these bodies:

```json
{ "success": false, "error": { "code": "rate_limited", "message": "Signup rate limit reached for this catalog; retry after Retry-After seconds (errorId=3f6c1b2e-8d0a-4c7e-9a51-2b7d4e0f6a13)" } }
```

```json
{ "success": false, "error": { "code": "rate_limited", "message": "Signup rate limit reached for this address; retry after Retry-After seconds (errorId=9a1d7c04-5e2b-4f38-b6c1-0d8e2f7a4b55)" } }
```

Wait `Retry-After` seconds before trying again.

## Other errors

All carry `success: false` and `error.code`; `error.message` ends with `(errorId=...)`.

| status | code | when |
|--------|------|------|
| 400 | `validation_failed` | Bad email, `spend_mandate` without `max_amount`, a `currency` that does not match the account, or a `max_amount` above the operator's limit. |
| 401 | `unauthorized` | Missing, wrong or revoked `X-API-Key`. After expiry every endpoint answers this. |
| 404 | `not_found` | Unknown slug, or signup is off for that catalog. Same body for both, on purpose. |
| 405 | `not_found` | Wrong verb, for example `GET` on the signup URL. The code is `not_found`, the status is 405. |

## Reference

- OpenAPI: {base}/v3/api-docs
- Manifest: {base}/.well-known/agent.json
- Index: {base}/llms.txt
