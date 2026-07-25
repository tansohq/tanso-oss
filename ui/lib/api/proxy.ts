import type { NextRequest } from "next/server"

const backend = process.env.TANSO_BASE_URL ?? "http://localhost:8080"

// Spring maps this route WITH a trailing slash and 500s without it, but the
// Next router strips trailing slashes before route handlers run — restore it.
const NEEDS_TRAILING_SLASH = /^api\/v1\/monetization\/subscriptions\/customer\/[0-9a-fA-F-]+$/

export async function proxyRequest(req: NextRequest, prefix: string[], path: string[]) {
  const joined = [...prefix, ...path].join("/")
  const slash = NEEDS_TRAILING_SLASH.test(joined) ? "/" : ""

  const headers = new Headers()
  const auth = req.headers.get("authorization")
  if (auth) headers.set("authorization", auth)
  const contentType = req.headers.get("content-type")
  if (contentType) headers.set("content-type", contentType)

  const res = await fetch(`${backend}/${joined}${slash}${req.nextUrl.search}`, {
    method: req.method,
    headers,
    body: req.method === "GET" || req.method === "HEAD" ? undefined : await req.text(),
    cache: "no-store",
  })

  return new Response(await res.text(), {
    status: res.status,
    headers: { "content-type": res.headers.get("content-type") ?? "application/json" },
  })
}
