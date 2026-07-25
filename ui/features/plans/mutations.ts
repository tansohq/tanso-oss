import { useMutation, useQueryClient } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type { PlanDto } from "@/lib/api/types"
import type { PlanInput } from "./schemas"

function toPlanRequest(input: PlanInput) {
  return { ...input, intervalMonths: String(input.intervalMonths) }
}

export function useCreatePlan() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: PlanInput) =>
      apiFetch<PlanDto>("/api/v1/monetization/plans", {
        method: "POST",
        body: JSON.stringify(toPlanRequest(input)),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["plans"] }),
  })
}

export function useUpdatePlan(planId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: PlanInput) =>
      apiFetch<PlanDto>(`/api/v1/monetization/plans/${planId}`, {
        method: "PATCH",
        body: JSON.stringify(toPlanRequest(input)),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["plans"] }),
  })
}
