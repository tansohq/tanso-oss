import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api } from '@/lib/api'
import { env } from '@/lib/env'
import type { DecodedToken } from '@/features/auth/types'
import { queryClient } from '@/lib/queryClient.ts'
import { clearSubscriptionCache } from '@/lib/subscriptionCache'
import { useEnvironmentStore } from '@/stores/environment'

export function decodeJWT(token: string): DecodedToken | null {
  try {
    const base64Url = token.split('.')[1]
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    )
    return JSON.parse(jsonPayload)
  } catch (error) {
    console.error('Failed to decode token:', error)
    return null
  }
}

export function isTokenExpired(decoded: DecodedToken): boolean {
  if (!decoded.exp) return false
  return decoded.exp * 1000 < Date.now()
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const tokenType = ref<string>('Bearer')
  const decodedToken = ref<DecodedToken | null>(null)

  const isAuthenticated = computed(() => !!token.value)
  const userId = computed(() => decodedToken.value?.sub || null)
  const accountId = computed(() => decodedToken.value?.account_id || null)
  const userEmail = computed(() => decodedToken.value?.email || null)
  const userRole = computed(() => decodedToken.value?.role || null)

  function setToken(newToken: string, type: string = 'Bearer') {
    token.value = newToken
    tokenType.value = type
    decodedToken.value = decodeJWT(newToken)
    api.setToken(newToken)
  }

  /**
   * Updates the in-memory token refs without persisting to localStorage.
   * Used by the environment store when switching between production/sandbox.
   */
  function updateActiveToken(newToken: string) {
    token.value = newToken
    decodedToken.value = decodeJWT(newToken)
  }

  function logout() {
    localStorage.removeItem('auth_token')
    localStorage.removeItem('auth_token_sandbox')
    localStorage.removeItem('tanso_environment')

    // Redirect before clearing reactive state to prevent page flash
    if (window.location.pathname !== '/login' && !window.location.pathname.startsWith('/demo')) {
      window.location.href = '/login'
      return
    }

    // Only clear reactive state if already on login/demo (no redirect needed)
    token.value = null
    tokenType.value = 'Bearer'
    decodedToken.value = null
    api.setToken(null)
    api.setBaseUrl(env.apiBaseUrl)
    queryClient.clear()
    clearSubscriptionCache()

    const environmentStore = useEnvironmentStore()
    environmentStore.clearAll()
  }

  function initialize() {
    api.setOnUnauthorized(() => logout())

    const environmentStore = useEnvironmentStore()

    const savedToken = localStorage.getItem('auth_token')
    if (savedToken) {
      const decoded = decodeJWT(savedToken)
      if (!decoded || isTokenExpired(decoded)) {
        // Token expired or invalid — clean up and send to login
        localStorage.removeItem('auth_token')
        localStorage.removeItem('auth_token_sandbox')
        localStorage.removeItem('tanso_environment')
        environmentStore.initialize()
        return
      }

      // If we're in sandbox mode, set the production token on the store
      // but the API client will use the sandbox token (set by environment store)
      setToken(savedToken)
    }

    // Initialize environment after auth — this may override the API base URL and token
    environmentStore.initialize()
  }

  return {
    token,
    tokenType,
    decodedToken,
    isAuthenticated,
    userId,
    accountId,
    userEmail,
    userRole,
    setToken,
    updateActiveToken,
    logout,
    initialize
  }
})
