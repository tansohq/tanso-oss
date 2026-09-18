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

- `email`: optional. Must be a valid address if present. It is recorded as the owner contact only.
  Nothing is sent to it, and it never resolves to an existing customer: every signup creates a
  new provisional customer, even with an email seen before.
- `name`: optional, up to 100 characters.
- `spend_mandate`: optional. `currency` must equal the account currency. `max_amount` is required
  when the object is present. See step 3 for what comes back.

Errors: `404 not_found` when there is no catalog at that slug or signup is not enabled on it (same
body for both, on purpose). `400 validation_failed` for a bad email, a `spend_mandate` without
`max_amount`, or a currency that does not match the account. `429 rate_limited` when a rate limit
is hit (see Rate limits).

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
(`agentMaxTopupAmount`); null means no per-charge limit. It is distinct from the calling key's
budget, which the status endpoint reports as `spend.cap`.

`spend_mandate` is:

- `null` when the request did not ask for one.
- `{ "status": "unavailable", "max_amount", "currency", "period" }` when you asked but the operator
  has not enabled mandates, Stripe is not connected, or Stripe failed to open a session. The signup
  still succeeds.
- `{ "status": "pending", "setup_url", "max_amount", "currency", "period" }` when a Stripe Checkout
  page was opened. Hand `setup_url` to a human. When they finish, the cap is applied to every active
  key of the customer, keys rotated later inherit it, and the customer is claimed.

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
    "spend_mandate": { "status": "pending", "setup_url": "https://checkout.stripe.com/c/pay/cs_test_...", "max_amount": 50.00, "currency": "usd" }
  }
}
```

`status` is `provisional`, `claimed` or `expired`. `remaining` is per feature key, null when
the feature is unmetered. `spend` is null until the calling key has a budget; then it has `cap`,
`spent`, `remaining`, `currency` and `resets_at`. `limits.features[].period` uses the same
vocabulary as signup (`"month"` or `"N months"`).

`spend_mandate.status` is `none`, `pending`, `active` or `expired`. `setup_url` is present only
while `pending`. There is no `period` here. `expired` means the Stripe setup page expired unused
(24 hours); sign up again or ask for a new mandate.

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
Buy credits: `POST {base}/api/v1/client/credits/purchases`.
Change plan: `POST {base}/api/v1/client/subscriptions` with a plan key from pricing.json.
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
| 402 | `payment_required` | `payment` | `nominate_owner` | Stripe needs an email to send the invoice to. `PUT {"email": ...}` to `url` (the owner endpoint), then retry the call. Signing up with an email avoids this. |
| 403 | `budget_exceeded` | `budget` | `wait` | The key's budget window is used up. Wait `retry_after` seconds, then retry. Do not retry in a loop. |
| 403 | `spend_cap_exceeded` | `budget` | `raise_spend_cap` | One charge is above the operator's per-charge cap or the mandate cap. `retry_after` is null; waiting will not help. Ask the owner to raise the cap or make a smaller purchase. |
| 403 | `forbidden` | `scope` | `use_own_reference` | The key belongs to another customer. Message: "This API key belongs to another customer; use your own customerReferenceId or omit it." |
| 403 | `scope_denied` | `scope` | `request_scope` | The key lacks the `purchase` scope. Ask the operator for a key with it. |
| 403 | `forbidden` | `scope` | `request_scope` | The endpoint is not open to this kind of key. Use a tenant key or ask the account owner. |

There is no claim gate. Nothing is closed to a provisional account; paying is the claim.

`url`, `poll` and `retry_after` are null when they do not apply. `message` is one sentence that
says what to do. It is safe to show it to a human.

## 8. Set the owner

Optional. Records a human contact on the customer. Sends nothing.

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
| 400 | `validation_failed` | Bad email, `spend_mandate` without `max_amount`, or a `currency` that does not match the account. |
| 401 | `unauthorized` | Missing, wrong or revoked `X-API-Key`. After expiry every endpoint answers this. |
| 404 | `not_found` | Unknown slug, or signup is off for that catalog. Same body for both, on purpose. |
| 405 | `not_found` | Wrong verb, for example `GET` on the signup URL. The code is `not_found`, the status is 405. |

## Reference

- OpenAPI: {base}/v3/api-docs
- Manifest: {base}/.well-known/agent.json
- Index: {base}/llms.txt
