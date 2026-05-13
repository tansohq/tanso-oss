import { computed, type MaybeRef, toValue } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { getPlanFeatureRule } from './api'
import type { PlanFeatureRuleResponse } from './types'

export function usePlanFeatureRuleQuery(planUuid: string, featureUuid: string) {
  return useQuery({
    queryKey: ['plan-feature-rule', planUuid, featureUuid],
    queryFn: () => getPlanFeatureRule(planUuid, featureUuid)
  })
}

export interface PlanFeatureRuleResult {
  featureId: string
  rule: PlanFeatureRuleResponse | null
  error: unknown | null
  is404: boolean
}

export function usePlanFeatureRulesQuery(
  planId: MaybeRef<string>,
  featureIds: MaybeRef<string[]>
) {
  return useQuery({
    queryKey: computed(() => ['plan-feature-rules', toValue(planId), toValue(featureIds)]),
    queryFn: async (): Promise<PlanFeatureRuleResult[]> => {
      const ids = toValue(featureIds)
      const currentPlanId = toValue(planId)
      const results = await Promise.allSettled(
        ids.map((id) => getPlanFeatureRule(currentPlanId, id))
      )
      return results.map((r, i) => ({
        featureId: ids[i],
        rule: r.status === 'fulfilled' ? r.value : null,
        error: r.status === 'rejected' ? r.reason : null,
        is404: r.status === 'rejected' && is404Error(r.reason)
      }))
    },
    enabled: computed(() => !!toValue(planId) && toValue(featureIds).length > 0)
  })
}

function is404Error(error: unknown): boolean {
  if (error && typeof error === 'object') {
    const err = error as Record<string, unknown>
    if (err.status === 404 || err.statusCode === 404) return true
    if (err.response && typeof err.response === 'object') {
      const resp = err.response as Record<string, unknown>
      if (resp.status === 404) return true
    }
  }
  return false
}
