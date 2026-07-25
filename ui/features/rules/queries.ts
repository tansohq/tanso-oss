import { useQuery } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type { PlanFeatureRuleDto } from "@/lib/api/types"

export function usePlanFeatureRule(planId: string, featureId: string | null) {
  return useQuery({
    queryKey: ["rules", planId, featureId],
    queryFn: () =>
      apiFetch<PlanFeatureRuleDto>(`/api/v1/monetization/rules/plan-features/${planId}/${featureId}`),
    enabled: !!featureId,
  })
}
