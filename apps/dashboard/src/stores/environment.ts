import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api } from '@/lib/api'
import { env } from '@/lib/env'
import { queryClient } from '@/lib/queryClient'
import { clearSubscriptionCache } from '@/lib/subscriptionCache'
import { clearCachedApiKey } from '@/features/example-app/api'
import { useAuthStore, isTokenExpired, decodeJWT } from '@/stores/auth'

export type Environment = 'production' | 'sandbox'

export const useEnvironmentStore = defineStore('environment', () => {
  // If the build-time environment is 'sandbox', we're on the sandbox dashboard URL
  // and should always report sandbox
  const isSandboxBuild = env.environment === 'sandbox'

  const defaultEnv: Environment = 'production'

  const activeEnvironment = ref<Environment>(
    isSandboxBuild
      ? 'sandbox'
      : ((localStorage.getItem('tanso_environment') as Environment) || defaultEnv)
  )
  const sandboxToken = ref<string | null>(localStorage.getItem('auth_token_sandbox'))
  const isSwitching = ref(false)
  const switchError = ref<string | null>(null)

  const isSandbox = computed(() => activeEnvironment.value === 'sandbox')
  const isDeveloperEnvironment = computed(() =>
    isSandbox.value || env.environment === 'staging' || env.environment === 'local'
  )

  function setEnvironment(target: Environment) {
    activeEnvironment.value = target
    try {
      localStorage.setItem('tanso_environment', target)
    } catch {
      // localStorage unavailable
    }
  }

  function setSandboxToken(token: string | null) {
    sandboxToken.value = token
    try {
      if (token) {
        localStorage.setItem('auth_token_sandbox', token)
      } else {
        localStorage.removeItem('auth_token_sandbox')
      }
    } catch {
      // localStorage unavailable
    }
  }

  async function switchToSandbox() {
    if (isSwitching.value) return
    isSwitching.value = true
    switchError.value = null

    try {
      // Call the production backend to get a sandbox token
      const response = await api.post<{
        success: boolean
        data: { token: string; type: string; apiBaseUrl: string }
      }>('/api/v1/account/environment-token', { targetEnvironment: 'sandbox' })

      const { token, apiBaseUrl } = response.data

      // Store sandbox token
      setSandboxToken(token)

      // Switch API client to sandbox — persist: false so we don't overwrite
      // the production token in localStorage('auth_token')
      api.setBaseUrl(apiBaseUrl)
      api.setToken(token, { persist: false })

      // Update auth store refs so decodedToken/userId/accountId reflect sandbox
      const authStore = useAuthStore()
      authStore.updateActiveToken(token)

      setEnvironment('sandbox')

      // Invalidate queries so active ones refetch from sandbox
      clearCachedApiKey()
      clearSubscriptionCache()
      await queryClient.invalidateQueries()
    } catch (e) {
      switchError.value = e instanceof Error ? e.message : 'Failed to switch to sandbox'
      throw e
    } finally {
      isSwitching.value = false
    }
  }

  function switchToProduction() {
    // Restore production token from localStorage (never overwritten by sandbox)
    const productionToken = localStorage.getItem('auth_token')
    if (productionToken) {
      api.setBaseUrl(env.apiBaseUrl)
      api.setToken(productionToken, { persist: false })

      // Update auth store refs to reflect production user
      const authStore = useAuthStore()
      authStore.updateActiveToken(productionToken)
    }

    setEnvironment('production')

    // Invalidate queries so active ones refetch from production
    clearCachedApiKey()
    clearSubscriptionCache()
    queryClient.invalidateQueries()
  }

  /**
   * Called during app initialization to restore the correct API state
   * based on the persisted environment.
   */
  function initialize() {
    if (isSandboxBuild) return // sandbox build always uses its own backend

    if (activeEnvironment.value === 'sandbox') {
      const token = sandboxToken.value
      const decoded = token ? decodeJWT(token) : null
      if (!token || !decoded || isTokenExpired(decoded)) {
        // No valid sandbox token — fall back to production so the UI label
        // matches the production data the API client is serving
        setSandboxToken(null)
        setEnvironment('production')
        return
      }

      api.setBaseUrl(env.sandboxApiBaseUrl || env.apiBaseUrl)
      api.setToken(token, { persist: false })

      // Update auth store refs to reflect sandbox user
      const authStore = useAuthStore()
      authStore.updateActiveToken(token)
    }
    // Production is the default — auth store handles its own token
  }

  function clearAll() {
    setSandboxToken(null)
    setEnvironment('production')
    api.setBaseUrl(env.apiBaseUrl)
    try {
      localStorage.removeItem('tanso_environment')
      localStorage.removeItem('auth_token_sandbox')
    } catch {
      // localStorage unavailable
    }
  }

  return {
    activeEnvironment,
    sandboxToken,
    isSwitching,
    switchError,
    isSandbox,
    isDeveloperEnvironment,
    switchToSandbox,
    switchToProduction,
    initialize,
    clearAll
  }
})
