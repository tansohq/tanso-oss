import assert from "node:assert/strict";
import test from "node:test";

import { TansoApiError, TansoClient } from "../dist/index.js";

test("sends a server-side bearer key and unwraps entitlement data", async () => {
  let request;
  const client = new TansoClient({
    apiKey: "sk_test_example",
    baseUrl: "http://tanso.test/",
    fetch: async (input, init) => {
      request = { input, init };
      return Response.json({
        success: true,
        data: {
          referenceCustomerId: "demo-user",
          featureKey: "ai.chat",
          allowed: true,
          credit: {
            denomination: "AI_CREDITS",
            balance: 5,
            totalGranted: 5,
            totalConsumed: 0,
            hardLimit: true,
          },
        },
      });
    },
  });

  const decision = await client.checkEntitlement("demo-user", "ai.chat", {
    record: false,
  });

  assert.equal(decision.allowed, true);
  assert.equal(decision.credit?.balance, 5);
  assert.equal(
    request.input,
    "http://tanso.test/api/v1/client/entitlements/demo-user/ai.chat?record=false",
  );
  assert.equal(
    new Headers(request.init.headers).get("Authorization"),
    "Bearer sk_test_example",
  );
});

test("uses X-Idempotency-Key for event ingestion", async () => {
  let request;
  const client = new TansoClient({
    apiKey: "sk_test_example",
    fetch: async (input, init) => {
      request = { input, init };
      return new Response(JSON.stringify({ success: true }), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      });
    },
  });

  const result = await client.ingestEvent(
    {
      eventName: "ai.chat.completed",
      customerReferenceId: "demo-user",
      featureKey: "ai.chat",
      usageUnits: 1,
    },
    { idempotencyKey: "req_123" },
  );

  assert.equal(result, undefined);
  assert.equal(
    new Headers(request.init.headers).get("X-Idempotency-Key"),
    "req_123",
  );
  assert.deepEqual(JSON.parse(request.init.body), {
    eventName: "ai.chat.completed",
    customerReferenceId: "demo-user",
    featureKey: "ai.chat",
    usageUnits: 1,
  });
});

test("throws a typed error with the API error id", async () => {
  const client = new TansoClient({
    apiKey: "sk_test_example",
    fetch: async () =>
      Response.json(
        {
          success: false,
          error: {
            message: "Credit limit reached (errorId=credit-test)",
          },
        },
        { status: 409 },
      ),
  });

  await assert.rejects(
    () =>
      client.ingestEvent({
        eventName: "ai.chat.completed",
        customerReferenceId: "demo-user",
        featureKey: "ai.chat",
        usageUnits: 1,
      }),
    (error) => {
      assert.ok(error instanceof TansoApiError);
      assert.equal(error.status, 409);
      assert.match(error.message, /errorId=credit-test/);
      return true;
    },
  );
});
