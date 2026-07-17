import { NextResponse } from "next/server";

import { getDemoIdentity, getTansoClient } from "../../../lib/tanso";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET() {
  try {
    const tanso = getTansoClient();
    const { customerReferenceId, featureKey } = getDemoIdentity();
    const decision = await tanso.checkEntitlement(
      customerReferenceId,
      featureKey,
      { record: false },
    );

    return NextResponse.json({ decision });
  } catch (error) {
    return NextResponse.json(
      {
        error:
          error instanceof Error
            ? error.message
            : "Unable to reach the Tanso API",
      },
      { status: 503 },
    );
  }
}
