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

/** A token we still hold but that has expired reads as signed out. */
export function isTokenValid(): boolean {
  const claims = getClaims()
  if (!claims) return false
  return claims.exp * 1000 > Date.now()
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
