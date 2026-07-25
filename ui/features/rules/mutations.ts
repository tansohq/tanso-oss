import { useMutation, useQueryClient } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type { PlanFeatureRuleDto } from "@/lib/api/types"
import type { RuleInput } from "./schemas"

function toRuleRequest(planId: string, input: RuleInput) {
  return {
    planId,
    featureId: input.featureId,
    isEnabled: input.isEnabled,
    type: "BASE",
    value: JSON.parse(input.value),
    creditModelId: input.creditModelId || undefined,
  }
}

export function useCreateRule(planId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: RuleInput) =>
      apiFetch<PlanFeatureRuleDto>("/api/v1/monetization/rules/plan-features", {
        method: "POST",
        body: JSON.stringify(toRuleRequest(planId, input)),
      }),
    onSuccess: (_, input) => {
      queryClient.invalidateQueries({ queryKey: ["plans", planId, "features"] })
      queryClient.invalidateQueries({ queryKey: ["rules", planId, input.featureId] })
    },
  })
}

export function useUpdateRule(planId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: RuleInput) =>
      apiFetch<PlanFeatureRuleDto>("/api/v1/monetization/rules/plan-features", {
        method: "PATCH",
        body: JSON.stringify(toRuleRequest(planId, input)),
      }),
    onSuccess: (_, input) => {
      queryClient.invalidateQueries({ queryKey: ["plans", planId, "features"] })
      queryClient.invalidateQueries({ queryKey: ["rules", planId, input.featureId] })
    },
  })
}
