import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { rotateAccountApiKey, subscribeToPlan, changePassword } from './api'
import { invalidateSubscriptionCache } from '@/lib/subscriptionCache'
import type { SubscribeToPlanRequest, ChangePasswordRequest } from './types'

export function useRotateApiKeyMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: rotateAccountApiKey,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['account', 'api-key'] })
    }
  })
}

export function useSubscribeToPlanMutation() {
  const router = useRouter()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: SubscribeToPlanRequest) => subscribeToPlan(request),
    onSuccess: () => {
      // Invalidate subscription status cache and query
      invalidateSubscriptionCache()
      queryClient.invalidateQueries({ queryKey: ['account', 'subscription-status'] })
      // Navigate to home after successful subscription
      router.push('/')
    },
    onError: (error) => {
      console.error('Failed to subscribe to plan:', error)
    }
  })
}

export function useChangePasswordMutation() {
  return useMutation({
    mutationFn: (request: ChangePasswordRequest) => changePassword(request)
  })
}
