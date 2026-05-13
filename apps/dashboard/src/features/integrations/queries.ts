import type { Ref, ComputedRef } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { fetchAccountSettings, fetchStripeKeys, getStripeImportStatus } from './api'

export function useAccountSettingsQuery() {
  return useQuery({
    queryKey: ['account-settings'],
    queryFn: () => fetchAccountSettings()
  })
}

export function useStripeKeysQuery(enabled: Ref<boolean> | ComputedRef<boolean>) {
  return useQuery({
    queryKey: ['stripe-keys'],
    queryFn: () => fetchStripeKeys(),
    enabled
  })
}

export function useStripeImportStatusQuery(jobId: Ref<string | null>, enabled: Ref<boolean> | ComputedRef<boolean>) {
  return useQuery({
    queryKey: ['stripe-import-status', jobId],
    queryFn: () => {
      if (!jobId.value) throw new Error('Import job ID is not available')
      return getStripeImportStatus(jobId.value)
    },
    enabled,
    refetchInterval: 2000
  })
}
