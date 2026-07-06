# Configuration

Environment variables for self-hosting Tanso. The bundled `deploy/docker-compose.yml` reads these from a `.env` file next to it — copy `deploy/.env.example` to `deploy/.env` and edit. If you run the API or dashboard outside the bundled Compose setup, set the same variables in your own environment.

Only `JWT_SECRET` has no default and must be set. Everything else has a working default for a local, single-host deployment.

---

## Database

The provided Compose file runs Postgres for you and builds the backend's datasource URL from the `POSTGRES_*` values below. You normally set only these.

| Variable | Purpose | Default |
|---|---|---|
| `POSTGRES_HOST` | Hostname of the Postgres server. | `postgres` (the Compose service name) |
| `POSTGRES_PORT` | Postgres port, also the host port it is published on. | `5432` |
| `POSTGRES_DB` | Database name. | `tanso` |
| `POSTGRES_USER` | Database user. | `tanso` |
| `POSTGRES_PASSWORD` | Database password. The shipped value is a non-functional placeholder — **set a real one before deploying.** | `changeme` (placeholder) |

### Connecting to an external database

If you run the API against a Postgres you manage yourself (not the bundled container), set the Spring datasource variables directly instead of the `POSTGRES_*` ones:

| Variable | Purpose | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL, e.g. `jdbc:postgresql://host:5432/tanso`. | `jdbc:postgresql://postgres:5432/tanso` (docker profile) |
| `SPRING_DATASOURCE_USERNAME` | Database user. | `tanso` (docker profile) |
| `SPRING_DATASOURCE_PASSWORD` | Database password. | `changeme` (docker profile) |

---

## Backend

| Variable | Purpose | Default |
|---|---|---|
| `JWT_SECRET` | **Required.** Secret used to sign and verify dashboard login tokens. No default — the API container refuses to start if it is unset or empty. Generate one with `openssl rand -base64 48`. Changing it invalidates all existing tokens. | _(none — required)_ |
| `API_PORT` | Host port the backend is published on (maps to container port 8080). | `8080` |
| `APP_MCP_ENABLED` | Enables the MCP server endpoint at `/mcp` (authenticated with an API key). Leave off unless you use it. | `false` |

---

## CORS

| Variable | Purpose | Default |
|---|---|---|
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of origins allowed to call the API from a browser. Set this to the URL where you serve the dashboard. If the list contains `*`, credentialed requests are disabled. | `http://localhost:3000` |

---

## Frontend

The dashboard is a static bundle. `VITE_*` variables are inlined at **build time**, so the Compose file passes them as build args, not runtime environment. Changing one requires rebuilding the dashboard image.

| Variable | Purpose | Default |
|---|---|---|
| `DASHBOARD_PORT` | Host port the dashboard is published on (maps to container port 3000). | `3000` |
| `VITE_TANSO_BACKEND_URL` | URL the dashboard uses to reach the backend API. Must be reachable from the user's browser, not just from inside Docker. | `http://localhost:8080` |

---

## Stripe (optional)

Leave these empty to run without Stripe. See **[Stripe Integration](./stripe-integration.md)** for setup.

| Variable | Purpose | Default |
|---|---|---|
| `STRIPE_API_KEY` | Stripe secret API key, used to create charges and sync billing. | _(empty)_ |
| `STRIPE_WEBHOOK_SECRET` | Signing secret for verifying incoming Stripe webhooks. | _(empty)_ |

---

## Admin bootstrap

Used once on first startup to create the initial dashboard user. See **[Settings, Authentication & Roles](./settings-and-roles.md)** for how that user logs in.

| Variable | Purpose | Default |
|---|---|---|
| `ADMIN_EMAIL` | Email address of the first dashboard user. | `admin@example.com` |
| `ADMIN_PASSWORD` | Password for the first dashboard user. Leave unset to have `setup.sh` generate a strong password and print it once. Set it only if you want to choose your own. | _(unset — auto-generated)_ |

---

## What to Read Next

- **[Settings, Authentication & Roles](./settings-and-roles.md)** — How API keys and dashboard logins work
- **[Quickstart](./quickstart.md)** — Get a local instance running
- **[Stripe Integration](./stripe-integration.md)** — Stripe setup and webhook registration
