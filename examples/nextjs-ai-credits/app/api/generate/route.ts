import { TansoConflictError } from "@tansohq/sdk";
import { NextResponse } from "next/server";

import { runModel } from "../../../lib/model";
import { getDemoIdentity, getTansoClient } from "../../../lib/tanso";

export const runtime = "nodejs";

export async function POST(request: Request) {
  const requestId = request.headers.get("X-Request-Id") ?? crypto.randomUUID();

  try {
    const body = (await request.json()) as { prompt?: unknown };
    const prompt = typeof body.prompt === "string" ? body.prompt.trim() : "";

    if (!prompt || prompt.length > 2_000) {
      return NextResponse.json(
        { error: "Prompt must contain between 1 and 2,000 characters." },
        { status: 400 },
      );
    }

    const tanso = getTansoClient();
    const { customerReferenceId, featureKey } = getDemoIdentity();

    // 1. Check access before spending money with a model provider.
    const before = await tanso.entitlements.evaluate({
      customerReferenceId,
      featureKey,
      usage: {
        eventName: "ai.chat.generate",
        usageUnits: 1,
      },
      context: {
        idempotencyKey: `check-${requestId}`,
        flowId: requestId,
      },
    });

    if (!before.allowed) {
      return NextResponse.json(
        {
          error:
            before.meta?.reason?.description ??
            "The request is not entitled to run.",
          decision: before,
        },
        { status: 402 },
      );
    }

    // 2. Run the billable work.
    const result = await runModel(prompt);

    // 3. Record real usage. With the demo seed, this deducts one hard-limit
    // credit atomically. Reusing the request ID cannot double-charge.
    await tanso.events.ingest({
      customerReferenceId,
      featureKey,
      eventName: "ai.chat.completed",
      eventIdempotencyKey: `usage-${requestId}`,
      flowId: requestId,
      usageUnits: 1,
      costAmount: 0.0003,
      revenueAmount: 0.02,
      costInput: {
        model: "gpt-4.1-mini",
        modelProvider: "openai",
        costUnits: result.inputTokens + result.outputTokens,
      },
      meta: {
        example: "nextjs-ai-credits",
        inputTokens: result.inputTokens,
        outputTokens: result.outputTokens,
      },
    });

    const after = await tanso.entitlements.check(
      customerReferenceId,
      featureKey,
      false,
    );

    return NextResponse.json({
      answer: result.text,
      decision: after,
      requestId,
    });
  } catch (error) {
    if (error instanceof TansoConflictError) {
      return NextResponse.json(
        {
          error:
            "The credit pool was depleted before this request could be recorded.",
        },
        { status: 402 },
      );
    }

    return NextResponse.json(
      {
        error:
          error instanceof Error ? error.message : "The request could not run.",
      },
      { status: 500 },
    );
  }
}
