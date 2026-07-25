const TOKEN_KEY = "tanso.jwt"

export function getToken(): string | null {
  if (typeof window === "undefined") return null
  return window.localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string) {
  window.localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  window.localStorage.removeItem(TOKEN_KEY)
}

export type TokenClaims = {
  sub: string
  account_id: string
  email: string
  exp: number
}

export function getClaims(): TokenClaims | null {
  const token = getToken()
  if (!token) return null
  try {
    const payload = token.split(".")[1]
    return JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/")))
  } catch {
    console.error("Failed to decode JWT payload")
    return null
  }
}
