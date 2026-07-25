import type { NextRequest } from "next/server"

import { proxyRequest } from "@/lib/api/proxy"

type Ctx = { params: Promise<{ path: string[] }> }

async function handle(req: NextRequest, ctx: Ctx) {
  const { path } = await ctx.params
  return proxyRequest(req, ["public"], path)
}

export { handle as GET, handle as POST }
