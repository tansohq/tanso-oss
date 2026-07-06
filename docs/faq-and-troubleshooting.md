# FAQ & Troubleshooting

---

## Frequently Asked Questions

**How do I get my API key?**

Settings > General > API Key. Pass it as `X-API-Key: sk_live_...` in your requests.

---

**What is the difference between a Feature Key and an Event Name?**

The **Feature Key** (`featureKey`) links the event to a feature for billing and usage tracking. The **Event Name** (`eventName`) is a free-form label for your own reference — it does not affect billing.

---

**What happens if I ingest an event for a customer with no active subscription?**

Tanso still records the event, but it is not used for billing since there is no active subscription to bill against.

---

**What is an idempotency key and should I use one?**

A unique string per event that prevents double-counting on retries. If Tanso has already seen the key, it returns HTTP 409 Conflict. **Always use idempotency keys in production.** Pattern: `evt_{customer_id}_{action}_{source_record_id}`.

---

**How does billing timing work — in advance or in arrears?**

**In advance**: flat base price charged at the start of each period. **In arrears**: charges calculated at the end. Usage-based charges are always in arrears regardless of the billing timing setting.

---

**Can I change a customer's plan after they subscribe?**

Yes. Upgrades take effect immediately with proration. Downgrades are scheduled for the next billing period.

---

**How does cancellation work?**

**End of Period**: customer keeps access until the period ends. **Immediate**: access ends instantly. End-of-period cancellations can be reversed before the effective date via `DELETE /api/v1/client/subscriptions/cancellation/{subscriptionId}/scheduled`.

---

**How do I generate a Stripe Checkout session?**

`POST /api/v1/client/billing/subscriptions/{subscriptionId}/stripe/checkout` — pass the **subscription ID**. Tanso resolves the first DUE invoice for that subscription automatically.

---

**What does `usageLimitExceeded: true` mean in an event response?**

The customer has consumed their full `maxUsage` quota. The event is still recorded, but subsequent entitlement checks will return `allowed: false`. Surface an upgrade prompt when you receive this.

---

**Do I need to set up webhooks manually in Stripe?**

No. Tanso automatically registers a webhook endpoint when you connect Stripe.

---

**Can I re-import data from Stripe?**

Yes. Use the import flow in Settings > Integrations. Existing records are not duplicated.

---

## Troubleshooting

### Events rejected with HTTP 409 Conflict

Tanso has already processed an event with the same idempotency key. **This is intended behavior** — your retry safeguard is working. If you're seeing 409s for events that aren't retries, verify your idempotency key generates a unique value per distinct event.

---

### Usage not appearing in invoice line items

Verify:
1. The event includes a `featureKey` matching a Feature attached to the customer's plan.
2. The `customerReferenceId` exactly matches (case-sensitive) the customer's Reference ID in Tanso.
3. The plan feature rule has a pricing model with a non-zero price per unit.
4. The customer has an active subscription to the plan.

---

### Stripe invoices not being created

1. Verify Stripe integration shows **Connected** in Settings > Integrations.
2. Confirm the Stripe API key has permissions for customers, invoices, prices, and webhooks.
3. Check the webhook endpoint is active in Stripe Dashboard > Developers > Webhooks.

---

### Customer still has access after cancellation

If cancelled with **End of Period**, the customer retains access until the billing period expires. To revoke immediately, cancel again with `cancelMode=IMMEDIATE`.
