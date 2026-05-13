import { useMutation } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { login, signup } from './api'
import { useAuthStore } from '@/stores/auth'
import { useEnvironmentStore } from '@/stores/environment'
import { useTracking } from '@/lib/tracking'
import { env } from '@/lib/env'
import type { LoginCredentials, SignupRequest } from './types'

export function useLoginMutation() {
  const router = useRouter()
  const authStore = useAuthStore()
  const { track, identify } = useTracking()

  return useMutation({
    mutationFn: (credentials: LoginCredentials) => login(credentials),
    onSuccess: (response) => {
      if (response.success && response.data.token) {
        authStore.setToken(response.data.token, response.data.type)
        if (authStore.userId) {
          identify(authStore.userId, {
            $email: authStore.userEmail,
            email: authStore.userEmail,
            accountId: authStore.accountId,
            role: authStore.userRole
          })
        }
        track('user_logged_in')
        router.push('/')
      } else {
        console.error('Login succeeded but response was unexpected:', response)
      }
    },
    onError: (error) => {
      console.error('Login failed:', error)
    }
  })
}

export function useSignupMutation() {
  const router = useRouter()
  const authStore = useAuthStore()
  const environmentStore = useEnvironmentStore()
  const { track, identify } = useTracking()

  return useMutation({
    mutationFn: (request: SignupRequest) => signup(request),
    onSuccess: async (response) => {
      if (response.success && response.data.token) {
        authStore.setToken(response.data.token, response.data.type)
        if (authStore.userId) {
          identify(authStore.userId, {
            $email: authStore.userEmail,
            email: authStore.userEmail,
            accountId: authStore.accountId,
            role: authStore.userRole
          })
        }
        track('user_signed_up')
        if (env.environment === 'production') {
          try {
            await environmentStore.switchToSandbox()
          } catch {
            // If sandbox switch fails, continue in production
          }
        }
        router.push('/')
      }
    },
    onError: (error) => {
      console.error('Signup failed:', error)
    }
  })
}
