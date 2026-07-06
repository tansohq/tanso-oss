# Tanso

Open-source billing, metering, and entitlement platform.

- Real-time entitlement enforcement (403s, not just invoices)
- Usage-based and subscription billing
- Credit pools with grants, hard limits, and rollover
- Stripe integration
- MCP server for AI agent integration
- Self-host with Docker Compose

**Stack:** Spring Boot (Java 21) + Vue 3 + PostgreSQL

**License:** AGPL-3.0

## Quick Start

```bash
git clone https://github.com/tansohq/tanso-oss.git
cd tanso-oss/deploy
cp .env.example .env        # set JWT_SECRET and the DB password
docker compose up -d
./setup.sh                   # creates the admin account
```

Open http://localhost:3000 and log in as `admin@example.com`. `setup.sh` generates a
random admin password and prints it once — copy it from the output. (To choose your
own, set `ADMIN_PASSWORD` in `.env` before running `setup.sh`.)

## What You Can Do

1. **Define features** — create metered or boolean features (`api-calls`, `seats`, `export`)
2. **Create plans** — bundle features into plans with pricing rules (flat, usage-based, graduated tiers)
3. **Add customers** — register customers and subscribe them to plans
4. **Ingest events** — send usage events via the Client API
5. **Check entitlements** — real-time access checks that return allow/deny
6. **Generate invoices** — automated billing cycles with Stripe sync

## Documentation

Full guides and API reference: **[docs.tanso.dev](https://docs.tanso.dev)**.

For AI agents, the repo also ships machine-readable docs: [`docs/llms.txt`](docs/llms.txt)
and the [OpenAPI spec](docs/clientAPI/openapi.yaml).

## Architecture

```
apps/
  api/          Spring Boot backend (Java 21)
  dashboard/    Vue 3 frontend
deploy/
  docker-compose.yml
  setup.sh      First-run admin bootstrap
docs/           OpenAPI spec + llms.txt (agent-readable docs)
```

The official client SDK is published on npm as [`@tansohq/sdk`](https://www.npmjs.com/package/@tansohq/sdk).

## Client API

```bash
# Check entitlement
curl http://localhost:8080/api/v1/client/entitlements/cust_123/api-calls \
  -H "X-API-Key: sk_test_your_key"

# Ingest usage event
curl -X POST http://localhost:8080/api/v1/client/events \
  -H "X-API-Key: sk_test_your_key" \
  -H "Content-Type: application/json" \
  -d '{"customerReferenceId":"cust_123","featureKey":"api-calls","eventName":"api_call","usageUnits":1}'
```

## MCP Server

Tanso includes a built-in MCP server for AI agent integration. Enable it by setting `app.mcp.enabled: true` in your config, then connect any MCP-compatible client:

```bash
claude mcp add tanso --transport http \
  --header "X-API-Key: sk_test_your_key" \
  http://localhost:8080/mcp
```

## Development

### Backend
```bash
cd apps/api
SPRING_PROFILES_ACTIVE=docker ./mvnw spring-boot:run
```

### Frontend
```bash
cd apps/dashboard
npm install && npm run dev
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Security

See [SECURITY.md](SECURITY.md).
