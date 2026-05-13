# Settings & API Authentication

## API Key

Your API key authenticates requests to the Tanso client API. Find it in **Settings > General > API Key**.

**Keep this key secret — it provides full client API access to your account.** Never expose it in client-side code (browser JavaScript, mobile apps). It is intended for server-to-server calls only.

Pass the key as either:
- `X-API-Key: sk_live_...`
- `Authorization: Bearer sk_live_...`

API keys support rotation and revocation via `isActive` flag and `expiresAt` timestamp — enabling zero-downtime key rotation.

---

## Authentication Model

| Method | Audience | Credential | Scope |
|---|---|---|---|
| API Key | Application backends | `X-API-Key: <api-key>` | Client API (`/api/v1/client/**`) |

### Client API Access

Your backend authenticates using an API key and gets access to:

- Ingesting events (`POST /api/v1/client/events`)
- Checking entitlements (`GET /api/v1/client/entitlements/{customerReferenceId}/{feature-key}`)
- Entitlement usage simulation (`POST /api/v1/client/entitlements`)
- Managing customers and subscriptions
- Handling invoices and billing

---

## Security Model

### Multi-Tenancy

Every entity is associated with an Account. All database queries are filtered by account ID at the row level, providing complete tenant isolation.

### Soft Deletion

When a record is deleted, a `deletedAt` timestamp is set rather than removing the row. This preserves historical data integrity — deleting a plan does not orphan invoices, deleting a customer does not erase subscription history.

### Stateless Sessions

All API endpoints are fully stateless. Each request must include its own authentication credential. This makes horizontal scaling straightforward.

---

## What to Read Next

- **[Stripe Integration](./stripe-integration.md)** — Stripe setup and webhook registration
- **[FAQ & Troubleshooting](./faq-and-troubleshooting.md)** — Common integration questions
