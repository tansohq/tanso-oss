# MCP Server

Tanso ships a built-in [Model Context Protocol](https://modelcontextprotocol.io) server that lets an AI agent drive your billing setup directly. Instead of clicking through the dashboard, an agent connected to the server can create features and plans, register customers, check entitlements, report usage, manage credits, and run analytics — all through typed MCP tools backed by the same logic as the REST API.

The server is **off by default**. Enable it, connect a client with an API key, and your agent gets a full set of Tanso tools.

---

## Enable It

The server is gated by a single flag. Set `APP_MCP_ENABLED` to `true` and restart the API.

**Docker Compose** — set it in your `.env`:

```bash
APP_MCP_ENABLED=true
```

The `api` service in [`deploy/docker-compose.yml`](../deploy/docker-compose.yml) passes it through (default `false`).

**Running the API directly** — pass it as an environment variable:

```bash
APP_MCP_ENABLED=true ./mvnw spring-boot:run
```

When enabled, Tanso registers the MCP server and mounts a security filter chain that protects the endpoint. When disabled, the endpoint and its tools are not registered at all.

---

## The Endpoint

The server speaks streamable HTTP at:

```
http://localhost:8080/mcp
```

Every request must carry a Tanso API key in the `X-API-Key` header. The key scopes all tool calls to the account it belongs to, exactly like the REST API. Get a key from the dashboard under **Settings > General**.

---

## Connect a Client

The dashboard's **Getting Started** page (the home screen after you log in) generates a ready-to-paste command with your API key and backend URL already filled in. It also shows JSON config for Cursor, VS Code, Windsurf, and Codex.

### Claude Code

```bash
claude mcp add tanso-prod --transport http --header "X-API-Key: <your-api-key>" http://localhost:8080/mcp
```

### Cursor / Windsurf / other JSON clients

```json
{
  "mcpServers": {
    "tanso": {
      "url": "http://localhost:8080/mcp",
      "headers": { "X-API-Key": "<your-api-key>" }
    }
  }
}
```

Add it to `~/.cursor/mcp.json` (Cursor), `.vscode/mcp.json` (VS Code), or `~/.codeium/windsurf/mcp_config.json` (Windsurf). Swap in the endpoint URL for your deployment if it isn't running on `localhost`.

---

## Available Tools

The tools are grouped by domain. Each group maps to a class under [`apps/api/src/main/java/com/tansoflow/tansocore/mcp/tools/`](../apps/api/src/main/java/com/tansoflow/tansocore/mcp/tools).

| Group | What the agent can do |
|---|---|
| **Customers** | Look up, create, and update customers by your own reference ID. |
| **Plans & Features** | List the catalog; create and update plans and features; link features to plans with boolean gates or metered pricing rules. |
| **Subscriptions** | Subscribe a customer to a plan, change plans (upgrade/downgrade), and cancel. |
| **Entitlements** | Check whether a customer can use a feature, list all of a customer's entitlements, and simulate a check with hypothetical usage. |
| **Events** | Ingest usage events for billing and metering; query and aggregate the event stream by customer, feature, model, or event name. |
| **Credits** | Read customer credit pools, balances, and transactions; manage credit models, pools, grants, and deductions. |
| **Billing** | List a customer's invoices and mark invoices paid. |
| **Analytics** | Portfolio-level margin analytics, a period-over-period revenue waterfall, and cost/usage grouped by AI model. |
| **Stripe Setup** | Register a Stripe API key, wire up webhooks, and discover and import existing Stripe data into Tanso. |

Tools that create, delete, or otherwise change data are described to the agent as destructive and require an explicit confirmation argument before they run.

---

## Related Docs

- [Quickstart](./quickstart.md) - first API calls in 5 minutes
- [Product Catalog](./product-catalog.md) - features, plans, and pricing models
- [Entitlements](./entitlements.md) - real-time feature gating
- [Usage Metering & Events](./usage-metering-and-events.md) - event schema and idempotency
- [Stripe Integration](./stripe-integration.md) - connect payment collection
