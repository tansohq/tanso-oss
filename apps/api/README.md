# Tanso API

The Tanso backend: real-time entitlement checks, usage metering, credit pools, billing
and invoices, Stripe sync, and an MCP server for AI agents. Spring Boot (Java 21) +
PostgreSQL.

To run the whole platform (API + dashboard + database) the fast way, use the Docker
Compose quickstart in the [root README](../../README.md). This guide is for working on
the backend itself.

## Prerequisites

- Java 21 (`java -version`)
- A PostgreSQL 16 database
- The Maven wrapper (`./mvnw`) is included — no separate Maven install needed

## Run locally

The backend reads its datasource and signing secret from the environment (there are no
insecure defaults). Point it at a Postgres instance and start it:

```bash
# from apps/api/
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/tanso
export SPRING_DATASOURCE_USERNAME=tanso
export SPRING_DATASOURCE_PASSWORD=your_password
export JWT_SECRET=$(openssl rand -base64 48)

./mvnw spring-boot:run
```

Liquibase creates and migrates the schema on startup, so an empty database is fine. The
API listens on http://localhost:8080; health is at `/actuator/health`.

## Run the tests

The suite runs against a real PostgreSQL — see `src/test/resources/application-test.yaml`,
which expects a database reachable at `localhost:5432/core_db`. Provide a database and a
JWT secret, then:

```bash
export JWT_SECRET=test-secret
./mvnw test
```

Integration tests tagged `@Tag("manual")` are excluded from `./mvnw test`.

## Configuration

Key environment variables (see `src/main/resources/application.yaml` for the full set):

| Variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Database connection |
| `JWT_SECRET` | Signing secret for dashboard JWTs — required, no default |
| `CORS_ALLOWED_ORIGINS` | Allowed dashboard origins (default `http://localhost:3000`) |
| `APP_MCP_ENABLED` | Enable the MCP server at `/mcp` (default `false`) |
| `STRIPE_API_KEY` / `STRIPE_WEBHOOK_SECRET` | Optional Stripe integration |

## Layout

```
apps/api/
  src/main/java/com/tansoflow/tansocore/   controllers, services, filters, jobs, mcp
  src/main/resources/                       application.yaml + Liquibase changelogs
  src/test/                                 JUnit tests
  pom.xml
  Dockerfile
```
