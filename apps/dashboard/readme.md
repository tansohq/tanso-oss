# Tanso Dashboard

The Vue 3 web dashboard for Tanso — define features and plans, manage customers and
subscriptions, view usage events and invoices, and connect Stripe.

To run the whole platform (dashboard + API + database) the fast way, use the Docker
Compose quickstart in the [root README](../../README.md). This guide is for working on
the dashboard itself.

## Tech stack

- **Vue 3** (Composition API, `<script setup>`) + **Vite** + **TypeScript**
- **shadcn-vue** components (reka-ui) + **Tailwind CSS**
- **Pinia** for auth/session state only — server data goes through **TanStack Query**
- **vee-validate + Zod** for forms
- **Vue Router**

## Local development

```bash
# from apps/dashboard/
npm install
cp .env.example .env        # set VITE_TANSO_BACKEND_URL if the API isn't on localhost:8080
npm run dev                 # http://localhost:3000
```

The only required setting is the backend URL:

```bash
VITE_TANSO_BACKEND_URL=http://localhost:8080
```

`VITE_*` values are inlined at build time, so set them before `npm run build` (the
Dockerfile passes them as build args).

## Commands

```bash
npm run dev       # dev server with hot reload
npm run build     # type-check (vue-tsc) + production build
npm run lint      # eslint
npm run format    # prettier
```

## Structure

```
src/
  app/          bootstrap: router, root App view
  features/     one folder per domain (plans, customers, subscriptions, invoices, ...)
  components/ui shadcn-vue primitives
  lib/          API client, env
  shared/       shared components and layout
  stores/       Pinia stores (auth, environment)
```

Each feature folder follows the same shape:

```
features/plans/
  pages/        route components
  components/   feature components
  queries.ts    TanStack Query reads
  mutations.ts  TanStack Query writes
  schemas.ts    Zod schemas
  types.ts      types inferred from schemas
  api.ts        API calls
```

## Conventions

- Composition API + `<script setup>` only
- Server data via TanStack Query — never store it in Pinia
- Forms use vee-validate + Zod; validation lives in `schemas.ts`
- Use the shadcn-vue components in `components/ui` — don't hand-roll dialogs/tables/dropdowns
