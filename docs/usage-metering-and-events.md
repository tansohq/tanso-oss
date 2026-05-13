# Usage Metering & Events

Events are the integration primitive that closes the loop between what your product does and what you charge for it. The billing engine aggregates them into invoice line items; the analytics engine uses them for margin calculations.

## Sending Events

### Event Ingestion Endpoint

```http
POST /api/v1/client/events
X-API-Key: <your-api-key>
Content-Type: application/json

{
  "eventIdempotencyKey": "evt_unique_id_here",
  "eventName": "AI text generation request",
  "featureKey": "ai.text-generation",
  "occurredAt": "2024-03-15T14:32:00Z",
  "customerReferenceId": "org_7f3a9b",
  "usageUnits": 15000.0,
  "costAmount": 0.60
}
```

### Event Fields

| Field                 | Required     | Description                                                                                                                          |
| --------------------- | ------------ | ------------------------------------------------------------------------------------------------------------------------------------ |
| `eventIdempotencyKey` | Yes          | Unique key preventing duplicate processing                                                                                           |
| `eventName`           | Yes          | Descriptive name for the event. Free-form — does not need to match a feature key.                                                    |
| `featureKey`          | No           | The feature key this event tracks usage for. Used to resolve pricing rules and billing.                                              |
| `featureId`           | No           | Feature UUID. If provided, supersedes `featureKey`.                                                                                  |
| `occurredAt`          | Yes          | ISO 8601 timestamp of when the event happened                                                                                        |
| `customerReferenceId` | One of these | Your external ID for the customer                                                                                                    |
| `customerId`          | One of these | Tanso's internal UUID for the customer                                                                                               |
| `usageUnits`          | No           | Quantity of units consumed. Multiplied by `price_per_unit` for billing. Defaults to 1.                                               |
| `costAmount`          | No           | COGS you incurred. If omitted and a cost model is configured, Tanso calculates this automatically.                                   |
| `flowId`              | No           | Correlation identifier for grouping related events                                                                                   |
| `meta`                | No           | Arbitrary JSON object for custom metadata                                                                                            |
| `subscriptionId`      | No           | Link event to a specific subscription (useful for multi-subscription customers)                                                      |

---

## Idempotency — Protection Against Double-Billing

Every event must include a unique `eventIdempotencyKey` (or an `X-Idempotency-Key` header). Duplicates are rejected with `409 Conflict`.

Derive idempotency keys from stable, unique operation IDs in your system. A reliable pattern: `evt_{customer_id}_{action}_{source_record_id}`.

**Always use idempotency keys in production.**

---

## Usage Limit Responses

When usage reaches the `maxUsage` limit configured on a plan feature rule, Tanso returns:

**HTTP 200:**

```json
{
  "success": true,
  "data": {
    "usageLimitExceeded": true,
    "message": "Usage limit exceeded for feature ai.text-generation"
  }
}
```

A successful event ingestion returns **HTTP 201**. When the limit is exceeded, the response is **HTTP 200** with the fields above. The event is still recorded, but `usageLimitExceeded: true` signals your application to surface an upgrade prompt or restrict access. Subsequent entitlement checks will return `allowed: false`.

---

## Cost Units

The `costUnit` field indicates the denomination of the cost amount:

| Value      | Meaning                                                     |
| ---------- | ----------------------------------------------------------- |
| `TOKENS`   | Cost measured in AI tokens or similar non-monetary units    |
| `CREDITS`  | Cost measured in Tanso credit units                         |
| `CURRENCY` | Cost measured in the plan's configured currency (e.g., USD) |

---

## Metadata Fields

Events support a `meta` field for attaching arbitrary operational context (user ID, session ID, request path, etc.).

Tanso also populates two system-managed fields automatically:

* **`properties`**: Feature-specific context about the event. Not settable in API requests.
* **`context`**: Correlation and debugging context. Not settable in API requests.

---

## Event Types

| Type                      | Description                                                                                      |
| ------------------------- | ------------------------------------------------------------------------------------------------ |
| `CLIENT_TRACKED`          | Usage event submitted by your backend via the events API                                         |
| `ENTITLEMENT_CHECKED`     | Audit event from the entitlement simulation endpoint — zero usage, does not affect billing        |
| `ENTITLEMENT_REVOKED`     | System event when a customer's entitlement was revoked                                           |
| `CUSTOMER_CREATED`        | System lifecycle event                                                                           |
| `PLAN_CREATED`            | System lifecycle event                                                                           |
| `SUBSCRIPTION_CREATED`    | System lifecycle event                                                                           |
| `SUBSCRIPTION_CANCELLED`  | System lifecycle event                                                                           |
| `SUBSCRIPTION_UPGRADED`   | System lifecycle event                                                                           |
| `SUBSCRIPTION_DOWNGRADED` | System lifecycle event                                                                           |
| `INVOICE_CREATED`         | System lifecycle event                                                                           |

Only `CLIENT_TRACKED` events feed billing calculations and analytics.
