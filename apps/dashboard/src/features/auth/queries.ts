import { useQuery } from '@tanstack/vue-query'
import { fetchOnboardingPlans } from './api'

export function useOnboardingPlansQuery() {
  return useQuery({
    queryKey: ['onboarding-plans'],
    queryFn: () => fetchOnboardingPlans()
  })
}
