<div align="center">

<img src=".github/assets/logo.svg" alt="Tanso" width="88" />

# Tanso Core

**Open-source monetization engine for B2B AI products** — for teams that sell
credits or usage and whose inference costs are big enough that margin per
customer is a real question.

[Website](https://tansohq.com) · [Quick start](#quick-start-docker) · [Next.js example](#try-the-five-credit-nextjs-example) · [Features](#features) · [Agents & MCP](#agents--mcp) · [API & SDKs](#api-reference--sdks) · [Contributing](CONTRIBUTING.md) · [Docs](https://tanso.mintlify.app/introduction)

[![CI](https://github.com/tansohq/tanso-oss/actions/workflows/ci.yml/badge.svg)](https://github.com/tansohq/tanso-oss/actions/workflows/ci.yml)
[![License: AGPL-3.0](https://img.shields.io/badge/license-AGPL--3.0-blue)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-b07219)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-6db33f)](https://spring.io/projects/spring-boot)

</div>

## Why Tanso

Every metered event carries its cost: input/output tokens, model, provider,
and what that usage cost you — alongside what you billed for it. Billing tools
meter usage but don't know your costs; observability tools know your costs but
don't bill. Tanso does both in one ledger, so you can see margin per customer,
per feature, per model.

The same ledger enforces in real time: entitlement checks, usage caps, and
credit limits are applied when the event is ingested, not reconciled at
invoice time. Billing state lives in Tanso — Stripe is the payment adapter,
not the source of truth.

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
- **Credit weights (tariff)** — server-side table mapping usage units to
  credits per feature and model, with scheduled effective times, a console
  editor, and a pre-flight credit quote on entitlement checks.
- **Credit price book** — the second pricing dial: what one credit costs to
  buy, per denomination, with the same scheduled-publish mechanics as
  weights. Purchased top-ups are stamped with the price in force at sale
  time, entitlement quotes carry the money equivalent, and a client
  endpoint exposes current prices for paywall and top-up screens.
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

## Quick start (Docker)

One path from clone to a running, seeded instance — Docker is the only
prerequisite:

```bash
git clone https://github.com/tansohq/tanso-oss.git
cd tanso-oss/deploy
cp .env.example .env     # set JWT_SECRET and APP_SECRETS_KEY (openssl rand -base64 48 each)
docker compose up -d --build
./setup.sh               # seeds a test account and prints credentials
```

The API listens on [http://localhost:8080](http://localhost:8080) with docs at
`/swagger-ui.html`. `setup.sh` prints a test login and API key — change them
before exposing the instance to anything real.

### Run the admin console

The repo ships a web console for the admin API — plans, features, customers,
subscriptions, credits, invoices, events, and margin analytics:

<div align="center">
<img src=".github/assets/screenshots/margin-overview.png" alt="Overview — revenue, cost, and per-model margin" width="800" />
<br /><em>Per-model margin from the same ledger that bills — a money-losing model shows up in red.</em>
<br /><br />
<img src=".github/assets/screenshots/weights.png" alt="Credit weights — tariff editor with observed cost per unit" width="800" />
<br /><em>The credit tariff: weights next to observed cost per unit, with scheduled cutovers.</em>
<br /><br />
<img src=".github/assets/screenshots/ledger.png" alt="Credit pool ledger — weighted deductions with running balances" width="800" />
<br /><em>Every deduction auditable to the unit: weighted burns, grants, running balances.</em>
<br /><br />
<img src=".github/assets/gifs/create-plan-attach-feature.gif" alt="Creating a plan and attaching a feature to it" width="800" />
<br /><em>Creating a plan and attaching a feature — the full catalog flow, end to end.</em>
</div>

```bash
npm install
npm run dev:ui
```

Sign in with the credentials `setup.sh` printed. See [`ui/README.md`](ui/README.md)
for configuration.

**Plan → feature → customer → subscription → usage, in order:**

1. New plans start as `DRAFT`. A `DRAFT` plan can't accept subscriptions —
   attach features to it first, then edit it to `ACTIVE`. Activation also
   requires a non-empty description.
2. Attach a feature via the **Feature** dropdown in the "Attach feature"
   panel — click to open it, then pick from the list. Typing into the box
   only edits its placeholder text and won't register a selection.
3. Give customers a **Reference ID** at creation time. The client API's
   entitlement and event endpoints key off that reference, not the internal
   customer UUID — a customer created without one can still receive events
   by UUID, but entitlement checks (`GET /entitlements/{refId}/{key}`) will
   404.
4. A new subscription on a paid plan starts **inactive** until its first
   invoice is marked paid — that's expected, not a bug. Free ($0) plans
   activate immediately. To pay it: without Stripe, call
   `POST /api/v1/client/billing/invoices/{invoiceId}/mark-paid`; with Stripe
   connected, `POST /api/v1/client/billing/subscriptions/{subscriptionId}/stripe/checkout`
   returns a hosted payment page, and the `invoice.paid` webhook activates
   the subscription.
5. Stripe mode (Settings → Billing) is locked to `None` until you connect a
   secret key. Once connected, pick one of exactly two choices: **Stripe
   drives billing** (Stripe is the source of truth; Tanso becomes your
   entitlements/usage/analytics plane) or **Tanso handles billing** (Tanso
   manages subscriptions and invoices; Stripe only collects payment).
   Disconnecting resets the mode back to `None`. In **Stripe drives billing**,
   subscribing a paid plan returns a Stripe Checkout URL instead of creating
   the subscription immediately — the subscription is created by webhook once
   the customer pays, so nothing dangles if they never do.

<details>
<summary><strong>Testing Stripe payments locally</strong></summary>

Stripe can't reach `localhost`, so the webhook endpoint Tanso registers won't
receive events. Forward them with the
[Stripe CLI](https://docs.stripe.com/stripe-cli) instead — the account ID is in
the JWT printed by `setup.sh` (or the console URL after login):

```bash
stripe listen --api-key sk_test_... \
  --forward-to http://localhost:8080/public/stripe/ingest/webhook/<accountId>
```

`stripe listen` prints its own signing secret (`whsec_…`). Tanso verifies
signatures against the secret stored when you connected Stripe, so swap in the
CLI's one:

```bash
docker exec deploy-postgres-1 psql -U tanso -d tanso -c \
  "UPDATE external_api_keys SET key_value='whsec_…' \
   WHERE key_type='WEBHOOK_SECRET_SIGNING';"
```

Stored secrets are normally encrypted (`enc:v1:…` under `APP_SECRETS_KEY`); a
plaintext value written like this is still read correctly and gets encrypted
on the next restart.

Then pay a checkout with Stripe's test card `4242 4242 4242 4242` and watch
the invoice flip to `PAID` and the subscription activate.

</details>

### Try the five-credit Next.js example

The quickstart also seeds a `demo-user` with five hard-limit AI credits. In a
second terminal:

```bash
npm install
cp examples/nextjs-ai-credits/.env.example \
  examples/nextjs-ai-credits/.env.local
npm run dev --workspace @tansohq/nextjs-ai-credits-example
```

Open [http://localhost:3000](http://localhost:3000), then run the request five
times. Each call checks access before the billable work and records provider
cost, customer revenue, and one credit afterward. The sixth call is denied
before any provider cost is incurred. See the
[complete integration](examples/nextjs-ai-credits/app/api/generate/route.ts)
or install the published
[`@tansohq/sdk`](https://www.npmjs.com/package/@tansohq/sdk).

---

## Getting started (manual)

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

## Internal AI spend (build side)

The engine has two halves. The **serve side** — everything above — answers what
it costs to serve each customer. The **build side** answers what your own AI
spend is: the Anthropic and OpenAI bills for your engineers and agents.

<img src=".github/assets/screenshots/spend-usage.png" alt="Spend → Usage — internal AI usage by model, by person, by day; price-book cost next to the vendor's report" width="800" />

It works from the vendor's admin API, not a proxy in your request path:

1. **Spend → Connections**: store an Anthropic admin key (`sk-ant-admin01-…`),
   an OpenAI admin key, a Cursor admin API key (Enterprise; sent as HTTP
   Basic with the key as username; Cursor caps a window at 30 days, so a
   longer sync is pulled in 30-day chunks), a GitHub token with the *View
   Organization Copilot Metrics* permission plus the org name (a fine-grained
   PAT owned by an org admin; the org must have the Copilot metrics API
   policy enabled; reports cover one UTC day each and 204 means no activity),
   or a LiteLLM proxy's master key plus its URL.
   It is encrypted at rest and only its last four
   characters are ever shown. **Check key** makes one call to prove it works;
   **Sync now** pulls the last 30 days. An hourly job re-pulls the last three
   days after that (vendor reports lag by up to an hour).
2. **Spend → Usage**: tokens and cost by model, by day, and by person, two
   ways — what the price book (`model_pricing`) says the tokens should cost
   and what the vendor's own cost report says. The by-person view also shows
   what each vendor reports per seat: Claude Code sessions, commits, PRs and
   tool accept/reject; Cursor accepted lines, accepts/rejects and requests;
   Copilot interactions, accepted code and AI credits. Anthropic reports
   people only for Claude Code; OpenAI only for user-scoped keys.
3. **Spend → Reconcile**: per vendor and period, metered vs vendor-reported
   vs invoiced, with the two variances. Import the bill as a CSV with a header
   row — `description, amount` (dollars), optional `kind` (TOKEN, SEAT, TOOL,
   OTHER), `model`, `quantity`. An invoice only counts toward a window it sits
   entirely inside.

4. **Spend → Teams**: units (teams, projects, and — once switched on — people,
   nested however you like) and attribution rules that map a vendor workspace
   or project id, an API key id, or an actor onto a unit. Rules apply at
   report time, so editing one re-allocates history; when several rules match
   one row the lowest priority number wins. Whatever no rule claims shows as
   **Unattributed**. A unit's total is its own spend plus every descendant's
   (a person's Claude Code estimate is shown on the person only); budgets
   measure that total. Each unit can carry a budget: a small **daily**
   ceiling that catches a runaway agent and a **monthly** one for the real
   number (UTC calendar windows), with an alert threshold (default 80%).
5. **Spend → Alerts**: threshold and breach alerts on each ceiling, plus a
   **spike** when a unit has spent at least $5 today and more than twice its
   trailing-seven-day daily average — each once per window,
   checked after every sync and hourly; acknowledge to clear. Posted to a
   Slack incoming webhook, a generic webhook (JSON, `X-Tanso-Event`, and
   `X-Tanso-Signature: sha256=HMAC(secret, body)` when a secret is set) and
   the alert emails, whichever are stored under Spend settings. A
   **projected** alert fires once a month when the month-to-date pace lands
   above the ceiling (never in the first fifth of the month). A **temporary
   bump** lifts a unit's monthly ceiling until a date — launch week — without
   touching the standing number; it drops off on its own and is re-pushed to
   the gateway both ways. A **weekly digest** (Monday 08:00 UTC, opt-in) sends
   last week's spend per unit against the week before, with budget standing,
   to the same channels; preview or send it from Spend → Alerts. Tanso is not
   in the request path, so on its own a "Block" budget cannot stop a request —
   its alert says so. **Gateway mode**: connect a LiteLLM proxy and add a
   LiteLLM rule to the unit (its team id, key or user); a Block budget is then
   pushed to LiteLLM as that object's `max_budget` for the calendar month, the
   budget card shows where it is enforced, and the proxy refuses requests past
   the ceiling. Switching the budget back to Alert clears the limit. The
   proxy's spend logs are pulled like any other vendor, with the team, key
   and user LiteLLM already resolved per request. Two clocks, though: Tanso
   measures the budget on its price book, LiteLLM enforces against its own
   model map — the card shows both figures so the drift is visible.

<img src=".github/assets/screenshots/spend-teams.png" alt="Spend → Teams — allocation to projects, teams and people, with roll-up and the person's Claude Code estimate kept separate" width="800" />

<img src=".github/assets/screenshots/spend-alerts.png" alt="Spend → Alerts — daily breach and monthly threshold alerts, once per window" width="800" />

6. **Spend → Outcomes**: shipped work next to what it cost. Connect GitHub
   (merged pull requests per repo) or Linear (completed issues per team), or
   have any CI job post one: `POST /api/v1/client/outcomes` with the tenant
   `sk_` key (or `/api/v1/spend/outcomes` with a console JWT) and a body of
   `kind` (`PR_MERGED`, `ISSUE_DONE`, `CUSTOM`), a stable `externalId`, and
   optionally `title`, `url`, `actorEmail`, `actorLogin`, `spendUnitId`,
   `occurredAt`; posting the same `externalId` again updates only the fields
   you send. GitHub scope is a comma-separated `owner/repo` list; Linear
   scope is comma-separated team keys or `*`. A person's GitHub login goes on
   the PERSON unit. An outcome lands on the person whose email or GitHub
   login matches (person level on), else on the source's default unit.
   Disconnecting a source removes the outcomes it pulled; posted ones stay.
   A merged PR is tagged **AI-assisted** (with the tool) when GitHub already
   says so — a `claude-code-assisted`/`copilot`/`cursor` label, a
   `Co-authored-by`/`Made-with` trailer, or a bot author; posted outcomes can
   say so with `aiAssisted`/`aiTool`. Absence is not evidence. The report divides
   a unit's spend (with descendants) by its outcomes (with descendants):
   cost per merged PR, per team, per month. Pulled hourly for the last three
   days; re-pulls upsert.

<img src=".github/assets/screenshots/spend-outcomes.png" alt="Spend → Outcomes — merged PRs and completed issues next to allocated spend: cost per outcome per unit" width="800" />

Person-level attribution is **off by default**. Attributing spend to a named
employee is a monitoring capability (in Germany a works council can veto it);
the switch under Spend settings will not turn on until you have written down
what staff were told. While it is off, people cannot be created, person rules
are skipped, and the by-person view stays empty. A person's Claude Code
estimate is shown on the person and not rolled up into the team — the same
traffic already reaches the team through its key rules.

Pulled data lands in `vendor_usage_buckets` in the vendor's own dimensions
(model, workspace/project, key, actor); a window is rewritten on every pull.
`POST /api/v1/spend/connections/{id}/sync?from=&to=` pulls any window (`to`
exclusive; longer than 31 days is pulled in 31-day chunks). Dates on the usage
report are `[from, to)`; on reconcile they are inclusive, because invoices are
dated, not timestamped. "Metered" means tokens × the price book; it is marked
an estimate when a model is unpriced or has no cache rates. Seat lines on an
invoice count toward "invoiced" but never appear in the vendor's token cost
report, so "vendor − invoice" carries the seats.
API: `/api/v1/spend/connections`, `/api/v1/spend/reports/{usage,reconcile,allocation}`,
`/api/v1/spend/invoices`, `/api/v1/spend/units` and `/api/v1/spend/units/{id}/budget`,
`/api/v1/spend/rules`, `/api/v1/spend/alerts` (+ `/{id}/ack`),
`POST /api/v1/spend/budgets/evaluate`, `/api/v1/spend/settings`,
`/api/v1/spend/outcome-sources`, `/api/v1/spend/outcomes`,
`/api/v1/spend/reports/outcomes` (console JWT only).
`APP_SPEND_ANTHROPIC_BASE_URL` / `APP_SPEND_OPENAI_BASE_URL` (and `_GITHUB_` /
`_LINEAR_`) point the pulls at a gateway or proxy instead of the vendor; a
LiteLLM connection carries its own proxy URL. Next:
feature-level P&L — a project's build cost next to the serve-side revenue of
the feature it shipped.

An Anthropic admin key can administer your whole org (there is no read-only
scope on Console admin keys), so use a dedicated reporting org where you can.
Set `APP_MODULES_BUILD_ENABLED=false` to run a serve-side-only install.

## Configuration

Configuration lives in `src/main/resources/application-*.yaml`, one file per
profile (`dev`, `staging`, `sandbox`, `prod`). **No secrets are committed** —
supply them via environment variables. The common ones:

| Variable | Description |
| -------- | ----------- |
| `SPRING_PROFILES_ACTIVE` | Active profile (`dev`, `staging`, `sandbox`, `prod`) |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | Signing secret for UI session tokens |
| `APP_SECRETS_KEY` | Encrypts stored integration credentials (Stripe keys, vendor admin keys) at rest. Required. Startup refuses a key that does not decrypt what is stored; to rotate, `DELETE FROM external_api_keys` and `UPDATE vendor_connections SET admin_key=''`, then reconnect |
| `STRIPE_API_KEY` / `STRIPE_WEBHOOK_SECRET` | Stripe integration |
| `APP_RESEND_API_KEY` | Transactional email via Resend — spend alerts and the weekly digest. Empty with recipients configured = the email leg reports FAILED on every send (logged with the reason) |
| `OPENAI_API_KEY` | AI features (optional) |
| `APP_WEBHOOK_ENDPOINT` | Public Stripe webhook URL |
| `CORS_ALLOWED_ORIGINS` | Allowed dashboard origins |
| `MASTER_ACCOUNT_ID` / `DEFAULT_FREE_PLAN_ID` | Dogfooding identifiers |
| `TANSO_TELEMETRY_ENABLED` | Anonymous instance telemetry (`true` by default, set `false` to opt out) |
| `APP_MODULES_BUILD_ENABLED` | Internal AI spend — the console's Spend section and `/api/v1/spend/**` (`true` by default; `false` for a serve-side-only install) |
| `APP_SPEND_ANTHROPIC_BASE_URL` / `APP_SPEND_OPENAI_BASE_URL` | Where the build side pulls usage and cost from (defaults: the vendors' APIs; set to a gateway or proxy) |
| `APP_SPEND_GITHUB_BASE_URL` / `APP_SPEND_LINEAR_BASE_URL` | Where outcomes (and Copilot metrics) are pulled from (defaults: api.github.com, api.linear.app/graphql) |
| `APP_SPEND_CURSOR_BASE_URL` | Where Cursor usage is pulled from (default api.cursor.com) |
| `APP_SPEND_ALERT_FROM` | Sender for alert emails and the weekly digest, via Resend (`APP_RESEND_API_KEY`); default `Tanso <alerts@your-domain.com>` |

> The non-`dev` config files reference a `your-domain.com` placeholder for
> webhook, CORS, and cross-environment URLs — replace these with your own.

### Telemetry

Tanso sends **one anonymous ping per day** so we know how many self-hosted
instances exist and roughly how they're used. No customer PII, no financial
data, no keys — ever. The entire telemetry surface is one class,
[`TelemetryPingJob`](src/main/java/com/tansoflow/tansocore/jobs/scheduler/telemetry/TelemetryPingJob.java),
so you can audit it in under a minute. The exact payload:

```json
{
  "instance_id": "a8098c1a-f86e-11da-bd1a-00112444be1e",
  "version": "0.9.0",
  "accounts": 2,
  "customers": 10,
  "plans": 3,
  "subscriptions": 8,
  "events_last_24h": "101-1k",
  "mcp_enabled": true,
  "dogfooding_enabled": false
}
```

`instance_id` is a random UUID generated at first boot — it identifies the
installation, not you. Event volume is reported as a coarse bucket, never an
exact count. Opt out any time with `TANSO_TELEMETRY_ENABLED=false`.

The ping posts to `https://jozfgvokhefrdlojzefq.supabase.co/functions/v1/ping`
(`app.telemetry.endpoint`) — a Supabase edge function owned and operated by the
Tanso team. The random-looking hostname is just Supabase's auto-generated
project ID, not a third party.

---

## Agents & MCP

Tanso makes the product you build on it **agent-ready out of the box** — not
just your team's agents, but your customers' buying agents:

- **Discover**: `GET /public/v1/catalog/{slug}/pricing.json` publishes your
  plans, features, credit weight table, and governance flags as a
  machine-readable catalog (agent-serve pricing.json schema). Opt-in per
  account: set a slug and enable it in settings.
- **Sign up**: `POST /public/v1/catalog/{slug}/signup` creates a customer,
  subscribes your designated free plan, and returns a customer-scoped API key
  in one call — no CAPTCHA, no email loop. Opt-in, rate-capped per hour.
- **Scoped credentials**: `ck_live_`/`ck_test_` keys are pinned to one
  customer with explicit scopes (`read`, `purchase`); tenants issue and
  rotate them via `/api/v1/client/customers/{ref}/keys`. Every endpoint not
  deliberately opened to customer keys denies them.
- **Pay**: SetupIntent pre-authorization saves a payment method (card data
  never touches Tanso); subscribe and credit top-ups charge off-session with
  it. No payment method? The API returns **402** with a checkout URL and a
  checkout session id the agent can poll (`GET /checkout-sessions/{id}`) —
  no dead-ending at a browser redirect. Agent charges respect the account's
  spend cap and the calling key's own budget — credit top-ups and paid
  subscriptions alike.
- **Bound**: give one key a budget and it cannot spend past it, on usage or on
  money. `PUT /api/v1/client/customers/{ref}/keys/{keyId}/budget` sets
  `creditLimit` and `amountLimit` independently over a rolling `DAY`/`WEEK`/
  `MONTH`/`TOTAL` window; either may be left unlimited. The budget bounds the
  **key**, not the pool, so one runaway agent cannot drain the balance its
  siblings draw on. Breaching returns **403** with
  `"code": "budget_exceeded"` and the limit, spend, remainder, and reset time,
  so an agent can back off instead of retrying blindly. Only the tenant may
  set or clear a budget — a customer key can read its own (to pre-flight) but
  never raise it. A budget also carries a warning mark (80% by default, 0 to
  disable): once a key passes that share of its tightest limit, the budget
  reports `alerting`, `percentUsed`, and when it crossed, so an agent can slow
  down rather than discover the ceiling by being refused.
- **Use**: entitlement checks return credit quotes with estimated cost;
  `GET /customers/{ref}/usage` is the burndown API — per-feature projections
  and credit depletion dates. Errors carry stable `code` fields, and mutating
  requests accept an `Idempotency-Key` header with 24h replay.

Tanso Core also ships an [MCP](https://modelcontextprotocol.io) server so
agents can operate over MCP instead of REST — including a curated
customer-facing tool set (list plans, credit prices, entitlement pre-flight,
usage forecast, remaining budget, subscribe, buy credits) that works with customer-scoped keys
using the same authenticated, account-scoped access as any other client.
There's no separate, weaker path for agents.

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

The `Admin*` tools and Stripe setup tools reconfigure the tenant itself
(plans, tariffs, prices, Stripe keys). They are gated behind a second flag,
`app.mcp.admin-tools.enabled` (default `false`), and should only be enabled
when every holder of a client API key is the operator. Before this flag
existed, any client key could reach them — if you relied on that, opt back
in explicitly.

The server exposes `/mcp`. See `McpServerConfig` and
`src/main/java/com/tansoflow/tansocore/mcp/tools/` for the full tool catalog.

---

## Credit pricing: the two dials

Credit pricing has exactly two controls, both adjustable at runtime from the
console — no deploy, no plan migration:

1. **Burn rate (weights)** — how many credits one usage unit burns, per
   feature and optionally per model (**Credits → Weights**).
2. **Credit price (price book)** — what one credit costs the customer to buy,
   per denomination (**Credits → Pricing**).

`usage × weight = credits burned · credits × price = what customers pay.`
Both tabs open with an explainer and a worked example, and the Weights table
shows the money implication of each weight live against your observed cost
per unit. The full guide, including when to turn which dial, is in
[PRICING.md](PRICING.md).

### Dial 1 — weights (tariff)

By default one usage unit burns one credit. The weight table lets you reprice
server-side — "a `deep-research` call costs 5 credits, and 8 on `gpt-4.1`" —
without redeploying your client. Weights resolve most-specific first:
`(feature, model)` → `(feature, any model)` → `1.0`.

Publish a tariff from the console (**Credits → Weights**) or via
`POST /api/v1/monetization/credits/weights/publish`. All rows in a batch share
one effective time, which must be in the future — effective rows are
append-only and never repriced; only rows that haven't taken effect yet can be
deleted.

### Dial 2 — the price book

Publish what one credit costs (**Credits → Pricing** or
`POST /api/v1/monetization/credits/prices/publish`) with the same mechanics:
batched, effective-dated, append-only once effective. There is no default
price — an unpriced denomination simply quotes credits without money.

The price book feeds three places:

- **Entitlement quotes** carry `pricePerCredit`, `currency`, and
  `estimatedCost`, so your app can show "this action ≈ $0.30" before doing
  billable work.
- **Purchased grants** (`grantType: "PURCHASED"`) are stamped with the book
  price in force at sale time. Pass an explicit `unitPrice` for negotiated
  or volume deals — it always wins, and currency without a `unitPrice` is
  rejected.
- **`GET /api/v1/client/credits/prices`** returns current prices per
  denomination for paywall and top-up screens.

### Quote, then record

> The figures below assume a weight of 8 credits/unit for `claude-opus-4` on
> this feature and a price of $0.10/credit have been published (Credits →
> Weights, Credits → Pricing). On a fresh seed both are unset, so you will see
> `weight 1`, `weightMatch NONE`, and no `pricePerCredit`.

Quote the cost before doing billable work, then record it:

```bash
# Pre-flight: is the customer allowed, and what would this burn (and cost)?
curl -X POST http://localhost:8080/api/v1/client/entitlements \
  -H "X-API-Key: $TANSO_API_KEY" -H 'Content-Type: application/json' \
  -d '{"customerReferenceId":"demo-user","featureKey":"ai.chat",
       "usage":{"usageUnits":1,"model":"gpt-4.1"}}'
# → "creditQuote": {"weight":8, "estimatedCredits":8, "weightMatch":"MODEL",
#                   "pricePerCredit":0.10, "currency":"USD", "estimatedCost":0.80}

# Record the usage — the same weight resolution applies at ingestion
curl -X POST http://localhost:8080/api/v1/client/events \
  -H "X-API-Key: $TANSO_API_KEY" -H 'Content-Type: application/json' \
  -d '{"customerReferenceId":"demo-user","featureKey":"ai.chat",
       "eventName":"chat completion","eventIdempotencyKey":"evt-123",
       "usageUnits":1,"costInput":{"model":"gpt-4.1"}}'
# → "creditsDeducted":8, "weightApplied":8, "remainingBalance":42
```

The quote is a quote, not a promise — the charge resolves at the event's
`occurredAt`, so a tariff change between quote and charge can change the
outcome. The `model` string on evaluate and ingest must match the tariff's
model exactly. Monetary quote fields appear only when the denomination has a
published price.

**Migrating existing integrations:** if your client already sends
pre-multiplied units (`usageUnits: 5` for a "5-credit action"), deploy the
client to send raw units *first*, then publish the tariff — never the reverse,
or the server multiplies your multiplied numbers. Repricing also affects
anything calibrated against the old numbers: `maxUsage` caps, Stripe meter
prices, and grant sizing.

---

## Credit forecasting widget

[`credit-estimator`](https://github.com/tansohq/credit-estimator) is a
companion open-source project, published to npm under `@tansohq`. It
forecasts credit or usage runway from observed history and explicit
low/base/high burn assumptions, and renders the result as an embeddable,
accessible React widget.

It's provider-neutral — no Tanso dependency required — with an optional
adapter (`@tansohq/credit-forecast-tanso`) that maps a Tanso credits
snapshot into the same neutral input any other host supplies. See
[Credit forecasts](https://tanso.mintlify.app/credit-forecasts) in the docs.

```bash
npm install @tansohq/credit-forecast-core @tansohq/credit-burndown-react
```

---

## API reference & SDKs

The full OpenAPI spec is checked in at [`openapi.json`](openapi.json) —
readable without running anything. A running instance also serves it live
at `/v3/api-docs`, with interactive docs at `/swagger-ui.html`.

| Language | Package |
| :--- | :--- |
| TypeScript / Node.js | [`@tansohq/sdk`](https://www.npmjs.com/package/@tansohq/sdk) |

For other languages, generate a client from the spec (e.g. with
[openapi-generator](https://openapi-generator.tech/)) or call the REST API
directly. Agents don't need an SDK at all — see [Agents & MCP](#agents--mcp).

Guides and concepts live in the [docs](https://tanso.mintlify.app/introduction).

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
├── deploy/            # docker-compose quickstart (see Quick start)
├── examples/
│   └── nextjs-ai-credits/     # runnable check → work → record flow
├── scripts/           # SQL seed & helper scripts
├── package.json       # developer kit workspace
└── pom.xml
```

---

## Testing

The Spring integration tests require PostgreSQL. To run them against a fresh,
disposable database without depending on local state:

```bash
docker run --rm --name tanso-test-db \
  -e POSTGRES_DB=core_db \
  -e POSTGRES_USER=dev_user \
  -e POSTGRES_PASSWORD=dev_pass \
  -p 55432:5432 -d postgres:17.5

until docker exec tanso-test-db \
  pg_isready -U dev_user -d core_db >/dev/null 2>&1; do sleep 1; done

SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/core_db \
SPRING_DATASOURCE_USERNAME=dev_user \
SPRING_DATASOURCE_PASSWORD=dev_pass \
SPRING_LIQUIBASE_ENABLED=true \
  ./mvnw test

docker stop tanso-test-db
```

Validate the TypeScript SDK and Next.js example:

```bash
npm install
npm run check
npm run build
```

Without the environment overrides above, Spring context tests use the
PostgreSQL database configured in
`src/test/resources/application-test.yaml`. The default suite excludes tests
tagged `manual`, which execute scheduler jobs against that database's current
state. Run them explicitly only after preparing a disposable test database:

```bash
./mvnw test -Pmanual-tests -Dgroups=manual
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
