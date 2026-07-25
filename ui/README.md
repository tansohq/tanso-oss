# Tanso Console

Admin console for the Tanso monetization engine. Next.js + Tailwind + shadcn/ui, talking to the Tanso Core API over the JWT admin surface (`/api/v1/monetization/**`, `/api/v1/tanso/**`, `/api/v1/analytics`).

## Prerequisites

- A running Tanso Core backend (default `http://localhost:8080`). See [`deploy/setup.sh`](../deploy/setup.sh) for a one-command local stack, which also seeds a login (`test` / `password`) and demo data.
- Node 20.9+.

## Run

```bash
# from the repo root
npm install
npm run dev:ui
```

Open http://localhost:3000 (Next picks the next free port if 3000 is taken) and sign in with the seeded credentials.

## Configuration

| Env var | Default | Purpose |
| --- | --- | --- |
| `TANSO_BASE_URL` | `http://localhost:8080` | Backend the Next.js server proxies API calls to |
| `NEXT_PUBLIC_TANSO_BASE_URL` | _(unset)_ | Set to make the browser call the backend directly instead of via the proxy — requires the backend's CORS config to allow the console's origin |

By default API calls go through the Next.js server via a same-origin proxy route (`/api/v1/*`, `/public/*`), so no CORS setup is needed.

## Structure

- `app/` — routes. `(console)/` is the authenticated shell (sidebar + JWT guard); `login/` is public.
- `features/{domain}/` — one folder per domain with `queries.ts` (TanStack Query), `mutations.ts`, `schemas.ts` (zod), and form components.
- `lib/api/` — typed client. `schema.d.ts` is generated from the backend's OpenAPI spec.
- `components/ui/` — shadcn/ui components (Base UI primitives). `components/data-table.tsx` is the shared table.

## Regenerating API types

With the backend running:

```bash
npm run openapi
```

This refreshes `openapi/tanso-api.json` from springdoc and regenerates `lib/api/schema.d.ts`.
