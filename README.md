# Tanso

Open-source billing, metering, and entitlement platform.

- Real-time entitlement enforcement
- Usage-based and subscription billing
- Credit pools with grants, hard limits, and rollover
- Stripe integration
- MCP server for AI agent integration
- Self-host with Docker Compose

**Stack:** Spring Boot (Java 21) + Vue 3 + PostgreSQL

**License:** AGPL-3.0

**Docs:** https://tansohq.com/docs

## Quick Start

```bash
git clone https://github.com/tanso-io/tanso.git
cd tanso/deploy
docker compose up
```

Open http://localhost:3000 to access the dashboard.

## Architecture

```
apps/
  api/          Spring Boot backend
  dashboard/    Vue 3 frontend
packages/
  sdk-typescript/   TypeScript client SDK
deploy/
  docker-compose.yml
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Security

See [SECURITY.md](SECURITY.md).
