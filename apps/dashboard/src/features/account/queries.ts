import { useQuery } from '@tanstack/vue-query'
import { fetchAccountApiKey, fetchSubscriptionStatus } from './api'

export function useAccountApiKeyQuery() {
  return useQuery({
    queryKey: ['account', 'api-key'],
    queryFn: fetchAccountApiKey
  })
}

export function useSubscriptionStatusQuery() {
  return useQuery({
    queryKey: ['account', 'subscription-status'],
    queryFn: fetchSubscriptionStatus
  })
}
