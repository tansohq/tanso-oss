import { useQuery } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import { newestFirst } from "@/lib/newest-first"
import type {
  PlanCreditAllocationDto,
  PlanDto,
  PlanFeatureLinkedDto,
  PlanRevenueResponse,
} from "@/lib/api/types"

export function usePlans() {
  return useQuery({
    queryKey: ["plans"],
    queryFn: () => apiFetch<PlanDto[]>("/api/v1/monetization/plans"),
    select: newestFirst,
  })
}

export function usePlanFeatures(planId: string) {
  return useQuery({
    queryKey: ["plans", planId, "features"],
    queryFn: () => apiFetch<PlanFeatureLinkedDto>(`/api/v1/monetization/plans/${planId}/features`),
  })
}

export function usePlanRevenue(planId: string) {
  return useQuery({
    queryKey: ["plans", planId, "revenue"],
    queryFn: () => {
      const periodEnd = new Date()
      const periodStart = new Date(periodEnd)
      periodStart.setDate(periodStart.getDate() - 30)
      const params = new URLSearchParams({
        periodStart: periodStart.toISOString(),
        periodEnd: periodEnd.toISOString(),
      })
      return apiFetch<PlanRevenueResponse>(
        `/api/v1/monetization/plans/${planId}/revenue?${params}`,
      )
    },
  })
}

export function usePlanCreditAllocations(planId: string) {
  return useQuery({
    queryKey: ["plans", planId, "credit-allocations"],
    queryFn: () =>
      apiFetch<PlanCreditAllocationDto[]>(
        `/api/v1/monetization/credits/plans/${planId}/allocations`,
      ),
  })
}
