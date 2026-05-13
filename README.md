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
git clone https://github.com/tanso-io/tanso.git
cd tanso/deploy
cp .env.example .env        # edit JWT_SECRET and passwords
docker compose up -d
./setup.sh                   # creates admin account
```

Open http://localhost:3000 and log in with `admin@example.com` / `changeme`.

## What You Can Do

1. **Define features** — create metered or boolean features (`api-calls`, `seats`, `export`)
2. **Create plans** — bundle features into plans with pricing rules (flat, usage-based, graduated tiers)
3. **Add customers** — register customers and subscribe them to plans
4. **Ingest events** — send usage events via the Client API
5. **Check entitlements** — real-time access checks that return allow/deny
6. **Generate invoices** — automated billing cycles with Stripe sync

## Architecture

```
apps/
  api/          Spring Boot backend (Java 21)
  dashboard/    Vue 3 frontend
packages/
  sdk-java/     Java client SDK
deploy/
  docker-compose.yml
  setup.sh      First-run admin bootstrap
docs/           API docs, quickstart, llms.txt
```

## Client API

```bash
# Check entitlement
curl http://localhost:8080/api/v1/client/entitlements/cust_123/api-calls \
  -H "X-API-Key: sk_test_your_key"

# Ingest usage event
curl -X POST http://localhost:8080/api/v1/client/events \
  -H "X-API-Key: sk_test_your_key" \
  -H "Content-Type: application/json" \
  -d '{"customerReferenceId":"cust_123","featureKey":"api-calls","units":1}'
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
