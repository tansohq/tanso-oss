import { clearToken, getToken } from "@/lib/auth"

const BASE_URL = process.env.NEXT_PUBLIC_TANSO_BASE_URL ?? ""

type ApiEnvelope<T> = {
  success: boolean
  data?: T
  error?: { code?: string; message?: string } | string
  meta?: unknown
}

export class ApiError extends Error {
  status: number
  code?: string

  constructor(message: string, status: number, code?: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

function errorMessage(
  error: ApiEnvelope<unknown>["error"],
  status: number
): string {
  if (typeof error === "string") return error
  if (error?.message) return error.message
  return `Request failed with status ${status}`
}

export async function apiFetch<T>(
  path: string,
  init?: RequestInit
): Promise<T> {
  const token = getToken()
  const res = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init?.headers,
    },
  })
  return unwrap<T>(res, path)
}

/** multipart/form-data — the browser sets the boundary, so no Content-Type here. */
export async function apiUpload<T>(path: string, body: FormData): Promise<T> {
  const token = getToken()
  const res = await fetch(`${BASE_URL}${path}`, {
    method: "POST",
    body,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  return unwrap<T>(res, path)
}

async function unwrap<T>(res: Response, path: string): Promise<T> {
  if (res.status === 401) {
    clearToken()
    if (
      typeof window !== "undefined" &&
      !window.location.pathname.startsWith("/login")
    ) {
      window.location.assign("/login")
    }
    throw new ApiError("Session expired", 401, "invalid_or_expired_token")
  }

  let body: ApiEnvelope<T> | undefined
  try {
    body = await res.json()
  } catch {
    throw new ApiError(
      `Invalid response from ${path} (status ${res.status})`,
      res.status
    )
  }

  if (!res.ok || !body || body.success === false) {
    const error = body?.error
    const code = typeof error === "object" ? error?.code : undefined
    throw new ApiError(errorMessage(error, res.status), res.status, code)
  }

  return body.data as T
}

export function queryString(
  params: Record<string, string | number | undefined>
): string {
  const entries = Object.entries(params).filter(
    ([, v]) => v !== undefined && v !== ""
  )
  if (entries.length === 0) return ""
  return (
    "?" +
    new URLSearchParams(entries.map(([k, v]) => [k, String(v)])).toString()
  )
}
