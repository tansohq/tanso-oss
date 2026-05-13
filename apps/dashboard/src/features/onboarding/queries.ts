import { useQuery } from '@tanstack/vue-query'
import { fetchIntakeData, fetchOnboardingProgress } from './api'
import type { Ref } from 'vue'

export function useIntakeDataQuery() {
  return useQuery({
    queryKey: ['onboarding', 'intake'],
    queryFn: fetchIntakeData
  })
}

export function useOnboardingProgressQuery() {
  return useQuery({
    queryKey: ['onboarding', 'progress'],
    queryFn: fetchOnboardingProgress
  })
}

export function useOnboardingStatusQuery(enabled: Ref<boolean>) {
  return useQuery({
    queryKey: ['onboarding', 'progress'],
    queryFn: fetchOnboardingProgress,
    enabled
  })
}
