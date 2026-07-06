import { env } from './env'

class ApiClient {
  private baseUrl: string
  private token: string | null = null
  private onUnauthorized: (() => void) | null = null
  private logoutFired = false

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl
    try {
      this.token = localStorage.getItem('auth_token')
    } catch {
      // localStorage unavailable (private browsing, etc.)
    }
  }

  setBaseUrl(url: string) {
    this.baseUrl = url
  }

  getBaseUrl(): string {
    return this.baseUrl
  }

  setToken(token: string | null, { persist = true }: { persist?: boolean } = {}) {
    this.token = token
    if (token) {
      // A new session was established — allow a future expiry to fire logout again
      this.logoutFired = false
    }
    if (persist) {
      try {
        if (token) {
          localStorage.setItem('auth_token', token)
        } else {
          localStorage.removeItem('auth_token')
        }
      } catch {
        // localStorage unavailable (private browsing, etc.)
      }
    }
  }

  getToken(): string | null {
    return this.token
  }

  setOnUnauthorized(callback: () => void) {
    this.onUnauthorized = callback
  }

  private async request<T>(endpoint: string, options?: RequestInit): Promise<T> {
    const url = `${this.baseUrl}${endpoint}`

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(options?.headers as Record<string, string>)
    }

    if (this.token) {
      headers['Authorization'] = `Bearer ${this.token}`
    }

    const config: RequestInit = {
      ...options,
      headers
    }

    const response = await fetch(url, config)

    if (!response.ok) {
      let errorData: unknown = null
      try {
        errorData = await response.json()
      } catch {
        // Response may not have JSON body
      }

      // A 401 on an authenticated endpoint means the session expired — fire the
      // logout handler once (deduped across concurrent requests). A 401 from a
      // public endpoint (e.g. login/signup) is a failed credential check, not a
      // session expiry, so it must surface as a rejected request without logout.
      if (
        response.status === 401 &&
        !endpoint.startsWith('/public/') &&
        this.onUnauthorized &&
        !this.logoutFired
      ) {
        this.logoutFired = true
        this.onUnauthorized()
      }

      // Handle nested error structures: { error: { detail: "...", message: "..." } } or { message: "..." }
      const errorMessage =
        (errorData as { error?: { detail?: string } })?.error?.detail ||
        (errorData as { detail?: string })?.detail ||
        (errorData as { error?: { message?: string } })?.error?.message ||
        (errorData as { message?: string })?.message ||
        (errorData as { error?: string })?.error ||
        response.statusText

      const error = new Error(errorMessage) as Error & {
        response?: { status: number; data: unknown }
      }
      error.response = {
        status: response.status,
        data: errorData
      }
      throw error
    }

    if (response.status === 204 || response.headers.get('content-length') === '0') {
      return { success: true } as T
    }

    return response.json()
  }

  async get<T>(endpoint: string): Promise<T> {
    return this.request<T>(endpoint, { method: 'GET' })
  }

  async post<T>(endpoint: string, data?: unknown): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'POST',
      body: JSON.stringify(data)
    })
  }

  async put<T>(endpoint: string, data?: unknown): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'PUT',
      body: JSON.stringify(data)
    })
  }

  async patch<T>(endpoint: string, data?: unknown): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'PATCH',
      body: JSON.stringify(data)
    })
  }

  async delete<T>(endpoint: string, data?: unknown): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'DELETE',
      body: data ? JSON.stringify(data) : undefined
    })
  }
}

export const api = new ApiClient(env.apiBaseUrl)
