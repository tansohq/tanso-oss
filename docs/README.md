# Tanso Documentation

Open-source billing, metering, and entitlement platform. Your backend makes two calls —
check an entitlement before serving a request, report usage after — and Tanso handles
enforcement, invoicing, and optional Stripe sync.

New here? Read in this order. Each doc builds on the ones before it.

## Start here

1. **[Introduction](./introduction.md)** — what Tanso does, the core concepts, and the check/serve/report pattern.
2. **[Quickstart](./quickstart.md)** — your first API calls in about five minutes, with curl and the TypeScript SDK.

## Build your catalog

3. **[Product Catalog](./product-catalog.md)** — features, plans, and pricing models (flat, usage-based, graduated tiers).
4. **[Customers & Subscriptions](./customers-and-subscriptions.md)** — register customers with your own IDs and subscribe them to plans.

## Enforce and meter

5. **[Entitlements](./entitlements.md)** — real-time "can this customer use this feature?" checks that fail closed.
6. **[Usage Metering & Events](./usage-metering-and-events.md)** — the event schema, idempotency keys, and how usage rolls up.

## Bill

7. **[Billing & Invoicing](./billing-and-invoicing.md)** — automated billing cycles and how invoices are generated.
8. **[Stripe Integration](./stripe-integration.md)** — connect Stripe for payment collection, webhooks, and billing meters.
9. **[Credits](./credits.md)** — pre-paid balances with grants, hard limits, and rollover.

## Operate

10. **[Settings & Auth](./settings-and-roles.md)** — API keys, the authentication model, and the multi-tenant security model.
11. **[MCP Server](./mcp-server.md)** — expose Tanso to AI agents over the Model Context Protocol.
12. **[Configuration](./configuration.md)** — environment variables and application settings for self-hosting.
13. **[FAQ & Troubleshooting](./faq-and-troubleshooting.md)** — common integration questions and fixes.

## Reference

- **[API Reference](./clientAPI/openapi.yaml)** — full Client API endpoints (OpenAPI spec).
- **[TypeScript SDK](https://www.npmjs.com/package/@tansohq/sdk)** — typed client for Node.js 18+.
- `./llms.txt` — concise API summary for AI coding assistants.
- `./llms-full.txt` — complete docs in a single file.
