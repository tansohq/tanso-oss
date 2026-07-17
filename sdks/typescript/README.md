# `@tansohq/sdk`

Server-side TypeScript client for the Tanso client API.

> Keep Tanso API keys on the server. Never import this client into browser
> bundles or expose an `sk_test_` / `sk_live_` key through `NEXT_PUBLIC_*`.

```ts
import { TansoClient } from "@tansohq/sdk";

const tanso = new TansoClient({
  apiKey: process.env.TANSO_API_KEY!,
  baseUrl: process.env.TANSO_BASE_URL ?? "http://localhost:8080",
});

const decision = await tanso.evaluateEntitlement({
  customerReferenceId: "customer_123",
  featureKey: "ai.chat",
  usage: { usageUnits: 1 },
});

if (!decision.allowed) {
  throw new Error(decision.meta?.reason?.description ?? "Access denied");
}

await tanso.ingestEvent(
  {
    customerReferenceId: "customer_123",
    featureKey: "ai.chat",
    eventName: "ai.chat.completed",
    usageUnits: 1,
  },
  { idempotencyKey: crypto.randomUUID() },
);
```

The package currently ships from the monorepo workspace. Its package metadata
and build output are ready for public npm publishing.
