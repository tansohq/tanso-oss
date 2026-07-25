import type { NextRequest } from "next/server"

import { proxyRequest } from "@/lib/api/proxy"

type Ctx = { params: Promise<{ path: string[] }> }

async function handle(req: NextRequest, ctx: Ctx) {
  const { path } = await ctx.params
  return proxyRequest(req, ["api", "v1"], path)
}

export { handle as GET, handle as POST, handle as PATCH, handle as PUT, handle as DELETE }
