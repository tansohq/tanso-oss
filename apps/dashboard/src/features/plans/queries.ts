import { computed, toRef, type MaybeRef } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { fetchPlans, getPlan, getPlanFeatures, fetchPlanRevenue } from './api'

export function usePlansQuery() {
  return useQuery({
    queryKey: ['plans'],
    queryFn: () => fetchPlans()
  })
}

export function usePlanQuery(planUuid: string) {
  return useQuery({
    queryKey: ['plan', planUuid],
    queryFn: () => getPlan(planUuid),
    enabled: !!planUuid
  })
}

export function usePlansWithFeaturesQuery() {
  return useQuery({
    queryKey: ['plans', 'features'],
    queryFn: () => fetchPlans() // Assuming fetchPlans now includes features or is the intended replacement
  })
}

export function usePlanFeaturesQuery(planUuid: MaybeRef<string>) {
  const planUuidRef = toRef(planUuid)
  return useQuery({
    queryKey: computed(() => ['plan-features', planUuidRef.value]),
    queryFn: () => getPlanFeatures(planUuidRef.value),
    enabled: computed(() => !!planUuidRef.value)
  })
}

export function usePlanRevenueQuery(
  planId: MaybeRef<string>,
  periodStart: MaybeRef<string>,
  periodEnd: MaybeRef<string>
) {
  const planIdRef = toRef(planId)
  const periodStartRef = toRef(periodStart)
  const periodEndRef = toRef(periodEnd)
  return useQuery({
    queryKey: computed(() => ['plan-revenue', planIdRef.value, periodStartRef.value, periodEndRef.value]),
    queryFn: () => fetchPlanRevenue(planIdRef.value, periodStartRef.value, periodEndRef.value),
    enabled: computed(() => !!planIdRef.value && !!periodStartRef.value && !!periodEndRef.value)
  })
}
