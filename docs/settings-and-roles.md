# Settings, Authentication & Roles

Tanso has two ways to authenticate, depending on who is calling. Pick the one that matches the caller.

| Method | Audience | Credential | Endpoints |
|---|---|---|---|
| API key | Application backends (server-to-server) | `X-API-Key: <api-key>` or `Authorization: Bearer <api-key>` | Client API (`/api/v1/client/**`) |
| JWT | Dashboard / interactive users | `Authorization: Bearer <jwt>` | Dashboard API (`/api/v1/monetization/**`, `/api/v1/tanso/**`, `/api/v1/account/**`) |

- **API key** is a long-lived secret for your own backend to call the client API. It is not tied to a person.
- **JWT** is a short-lived token a dashboard user gets by logging in with email and password. It represents that user's session.

---

## API key (server-to-server)

Your API key authenticates requests to the Tanso client API. Find it in **Settings > General > API Key**.

**Keep this key secret — it provides full client API access to your account.** Never expose it in client-side code (browser JavaScript, mobile apps). It is intended for server-to-server calls only.

Pass the key as either:
- `X-API-Key: <api-key>`
- `Authorization: Bearer <api-key>`

API keys support rotation and revocation via an `isActive` flag and an `expiresAt` timestamp — enabling zero-downtime key rotation.

### What the client API covers

With a valid API key your backend can:

- Ingest events (`POST /api/v1/client/events`)
- Check entitlements (`GET /api/v1/client/entitlements/{customerReferenceId}/{feature-key}`)
- Simulate entitlement usage (`POST /api/v1/client/entitlements`)
- Manage customers and subscriptions
- Handle invoices and billing

---

## Dashboard authentication (JWT)

The dashboard and its API (`/api/v1/monetization/**`, `/api/v1/tanso/**`, `/api/v1/account/**`) authenticate with a JWT, not an API key. A dashboard user obtains one by logging in.

### 1. Log in

`POST /public/v1/login` is public (no credential required to call it). Send the user's email as `username` plus their password:

```http
POST /public/v1/login
Content-Type: application/json

{
  "username": "you@example.com",
  "password": "your-password"
}
```

> The field is named `username`, but its value is the account member's **email address**.

A successful response returns the token:

```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "type": "Bearer"
  }
}
```

Repeated failed logins for the same username are rate-limited (locked after 5 failures, clearing 15 minutes after the last attempt).

### 2. Call dashboard endpoints

Send the token as a bearer header on every dashboard request:

```http
GET /api/v1/monetization/plans
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

Tokens expire two hours after they are issued. When a token expires, log in again to get a new one.

The first dashboard user is created at deploy time from `ADMIN_EMAIL` / `ADMIN_PASSWORD`. See **[Configuration](./configuration.md)** for how those are set.

---

## Roles & access

Tanso does not yet have per-user roles. Every member of an account has the **same, full access** to that account's data and dashboard endpoints — there is no read-only, billing-only, or admin-only tier today. Access control is enforced at the account boundary (see Multi-Tenancy below), not between users within an account.

---

## Security Model

### Multi-Tenancy

Every entity is associated with an Account. All database queries are filtered by account ID at the row level, providing complete tenant isolation.

### Soft Deletion

When a record is deleted, a `deletedAt` timestamp is set rather than removing the row. This preserves historical data integrity — deleting a plan does not orphan invoices, deleting a customer does not erase subscription history.

### Stateless Sessions

All API endpoints are fully stateless. Each request must include its own authentication credential (an API key or a JWT). This makes horizontal scaling straightforward.

---

## What to Read Next

- **[Configuration](./configuration.md)** — Environment variables for self-hosting
- **[Stripe Integration](./stripe-integration.md)** — Stripe setup and webhook registration
- **[FAQ & Troubleshooting](./faq-and-troubleshooting.md)** — Common integration questions
