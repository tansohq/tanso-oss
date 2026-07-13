# Tanso Core

**Tanso Core** is an open-source B2B SaaS monetization engine. It provides the
infrastructure to manage customer lifecycles, flexible billing models, usage
metering, and real-time feature entitlements — the plumbing behind a
subscription business, as a service you can self-host.

> Licensed under the **GNU AGPL-3.0**. See [LICENSE](LICENSE).

---

## Features

- **Identity & workspaces** — accounts (tenants), users, and role-based access.
- **Product catalog** — define features and bundle them into plans.
- **Monetization rules** — link features to plans with flat, usage-based, or
  graduated (tiered) pricing.
- **Subscriptions** — full lifecycle: subscribe, upgrade with proration, and
  cancel immediately or at end of period.
- **Usage & metering** — high-throughput, idempotent event ingestion.
- **Entitlements** — low-latency gating of capabilities based on subscription state.
- **Credits** — prepaid credit pools per customer, with grants, deductions,
  expirations, and full transaction history.
- **Billing & payments** — invoice generation and cycle rollover, synchronized
  with [Stripe](https://stripe.com).
- **Agent-native (MCP)** — an optional MCP server so AI agents can operate the
  platform directly, with an explicit consent gate on actions that spend money.

## Tech stack

| Layer         | Technology                                   |
| ------------- | -------------------------------------------- |
| Language      | Java 21                                      |
| Framework     | Spring Boot 3.5                              |
| Database      | PostgreSQL                                    |
| Migrations    | Liquibase                                     |
| Payments      | Stripe                                        |
| Transactional email | Resend                                 |
| AI / MCP      | Spring AI (optional, disabled by default)     |
| Build         | Maven                                         |

---

## Getting started

### Prerequisites

- Java 21 (JDK)
- Docker (for a local PostgreSQL instance)
- Maven — or use the bundled `./mvnw` wrapper

### 1. Start PostgreSQL

The `dev` profile expects a database at `localhost:5432/core_db`:

```bash
docker run --name tanso-db \
  -e POSTGRES_DB=core_db \
  -e POSTGRES_USER=dev_user \
  -e POSTGRES_PASSWORD=dev_pass \
  -p 5432:5432 -d postgres:17.5
```

### 2. Run the application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Liquibase applies the schema on startup. The app listens on
[http://localhost:8080](http://localhost:8080), with API docs at
`/swagger-ui.html`.

### 3. (Optional) Seed the platform master account

Tanso "dogfoods" its own billing engine via a master account. To seed it locally:

```bash
psql "postgresql://dev_user:dev_pass@localhost:5432/core_db" \
  -f scripts/seed_tanso_master_account.sql
```

> The seed uses a **placeholder** master API key — change it before using this
> anywhere real.

---

## Configuration

Configuration lives in `src/main/resources/application-*.yaml`, one file per
profile (`dev`, `staging`, `sandbox`, `prod`). **No secrets are committed** —
supply them via environment variables. The common ones:

| Variable | Description |
| -------- | ----------- |
| `SPRING_PROFILES_ACTIVE` | Active profile (`dev`, `staging`, `sandbox`, `prod`) |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | Signing secret for UI session tokens |
| `STRIPE_API_KEY` / `STRIPE_WEBHOOK_SECRET` | Stripe integration |
| `RESEND_API_KEY` | Transactional email |
| `OPENAI_API_KEY` | AI features (optional) |
| `APP_WEBHOOK_ENDPOINT` | Public Stripe webhook URL |
| `CORS_ALLOWED_ORIGINS` | Allowed dashboard origins |
| `MASTER_ACCOUNT_ID` / `DEFAULT_FREE_PLAN_ID` | Dogfooding identifiers |

> The non-`dev` config files reference a `your-domain.com` placeholder for
> webhook, CORS, and cross-environment URLs — replace these with your own.

---

## Agents & MCP

Tanso Core ships an [MCP](https://modelcontextprotocol.io) server so AI agents
can operate the platform directly — check credit balances, inspect
entitlements, manage subscriptions, generate billing insights — using the same
authenticated, account-scoped access as any other client. There's no separate,
weaker path for agents.

Tools that spend money or make hard-to-reverse changes (generating AI
insights, creating Stripe resources, billing operations) require the caller to
pass `confirmAction: true` before they execute.

Disabled by default. To enable:

```yaml
app:
  mcp:
    enabled: true
spring:
  ai:
    mcp:
      server:
        enabled: true
```

The server exposes `/mcp`. See `McpServerConfig` and
`src/main/java/com/tansoflow/tansocore/mcp/tools/` for the full tool catalog.

---

## Project structure

```
tanso-core/
├── src/main/java/com/tansoflow/tansocore/   # application code
│   ├── controller/    # REST controllers (client + admin APIs)
│   ├── service/       # business logic
│   ├── entity/        # JPA entities
│   ├── integration/   # Stripe & external integrations
│   └── ...
├── src/main/resources/
│   ├── application-*.yaml    # per-profile config
│   └── db/changelog/         # Liquibase migrations
├── scripts/           # SQL seed & helper scripts
├── docs/openapi/      # OpenAPI specs
├── compose.yaml       # local Docker stack
└── pom.xml
```

---

## Testing

```bash
./mvnw test
```

---

## Deployment

The included `Makefile` and `Dockerfile`s target an AWS ECR/ECS setup, but Tanso
Core is a standard Spring Boot app and runs anywhere you can run a container.

Build a production image:

```bash
docker build -t tanso-core:latest .
```

The `Makefile` targets (`login`, `build`, `push`, `tag-env`, `deploy-*`) assume
an ECR registry and ECS services. Override the placeholders at the top of the
`Makefile` (AWS account ID, cluster/service names) or set them via environment
variables. Infrastructure provisioning (ECS, ALB, RDS, etc.) is **not** included
in this repository.

---

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) before
opening a pull request.

## License

This project is licensed under the **GNU Affero General Public License v3.0**.
See [LICENSE](LICENSE) for the full text.

## Contact

Questions or security reports: **me@dougbaek.com**
