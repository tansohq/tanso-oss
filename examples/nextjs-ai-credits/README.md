# Next.js AI credits

A deliberately small integration showing the complete Tanso request path:

1. check the customer's entitlement before billable work;
2. run the model call;
3. record usage, provider cost, and customer revenue;
4. deduct one credit atomically;
5. deny the sixth request when the five-credit demo pool is empty.

The Tanso secret key is used only in Next.js route handlers. There are no
provider credentials, external databases, dashboards, or Stripe setup steps.

## Run it

From the Tanso Core repository root:

```bash
cd deploy
cp .env.example .env
# Set JWT_SECRET, for example: openssl rand -base64 48
docker compose up -d --build
./setup.sh

cd ..
npm install
cp examples/nextjs-ai-credits/.env.example \
  examples/nextjs-ai-credits/.env.local
npm run dev --workspace @tansohq/nextjs-ai-credits-example
```

Open [http://localhost:3000](http://localhost:3000).

Run the request five times. Each successful call consumes one `AI_CREDITS`
credit. The next request is denied before `runModel` executes.

## Read the integration

- [`app/api/generate/route.ts`](app/api/generate/route.ts) is the complete
  check → work → record flow.
- [`lib/model.ts`](lib/model.ts) is the provider-free model stub. Replace it
  with your OpenAI, Anthropic, or other provider call.
- [`lib/tanso.ts`](lib/tanso.ts) creates the server-side Tanso client.
- [`../../sdks/typescript`](../../sdks/typescript) contains the reusable typed
  client.

## Reset the five demo credits

Re-run:

```bash
cd deploy
./setup.sh
```

The developer demo seed resets only the fixed `demo-user` credit pool. It does
not affect customers you create yourself.
