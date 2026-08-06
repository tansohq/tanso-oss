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
        model: "gpt-4.1-mini",
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

    // The quote resolves now; the charge resolves at the event's occurredAt.
    // pricePerCredit/estimatedCost arrive when the account's price book has a
    // price for the denomination (SDK types gain these fields in the next
    // release; until then they ride along untyped).
    const quote = before.creditQuote as
      | (NonNullable<typeof before.creditQuote> & {
          pricePerCredit?: number;
          currency?: string;
          estimatedCost?: number;
        })
      | undefined;
    console.log(
      `credit quote: ${quote?.estimatedCredits ?? 1} credits` +
        (quote?.estimatedCost != null
          ? ` ≈ ${quote.estimatedCost} ${quote.currency ?? ""}`
          : "") +
        ` (match: ${quote?.weightMatch ?? "NONE"})`,
    );

    // 2. Run the billable work.
    const result = await runModel(prompt);

    // 3. Record real usage. With the demo seed, this deducts hard-limit
    // credits atomically. Reusing the request ID cannot double-charge.
    const receipt = await tanso.events.ingest({
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
        inputTokens: result.inputTokens,
        outputTokens: result.outputTokens,
      },
      meta: {
        example: "nextjs-ai-credits",
      },
    });

    const after = await tanso.entitlements.check(
      customerReferenceId,
      featureKey,
      false,
    );

    const deducted = receipt.creditsDeducted ?? 1;
    return NextResponse.json({
      answer: `${result.text} Tanso recorded one unit of usage and deducted ${deducted} ${deducted === 1 ? "credit" : "credits"}.`,
      decision: after,
      receipt,
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
