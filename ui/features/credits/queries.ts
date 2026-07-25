import { useQuery } from "@tanstack/react-query"

import { apiFetch, queryString } from "@/lib/api/client"
import type {
  CreditGrantDto,
  CreditModelDto,
  CreditPoolDto,
  CreditTransactionDto,
} from "@/lib/api/types"
import type { CreditFeatureWeightDto } from "./types"

export function useCreditModels() {
  return useQuery({
    queryKey: ["credit-models"],
    queryFn: () => apiFetch<CreditModelDto[]>("/api/v1/monetization/credits/models"),
  })
}

export function useCreditPools() {
  return useQuery({
    queryKey: ["credit-pools"],
    queryFn: () => apiFetch<CreditPoolDto[]>("/api/v1/monetization/credits/pools"),
  })
}

export function useCreditPool(poolId: string) {
  return useQuery({
    queryKey: ["credit-pools", poolId],
    queryFn: () => apiFetch<CreditPoolDto>(`/api/v1/monetization/credits/pools/${poolId}`),
  })
}

export function useCreditPoolGrants(poolId: string) {
  return useQuery({
    queryKey: ["credit-pools", poolId, "grants"],
    queryFn: () =>
      apiFetch<CreditGrantDto[]>(`/api/v1/monetization/credits/pools/${poolId}/grants`),
  })
}

export function useCreditPoolTransactions(poolId: string) {
  return useQuery({
    queryKey: ["credit-pools", poolId, "transactions"],
    queryFn: () =>
      apiFetch<CreditTransactionDto[]>(`/api/v1/monetization/credits/pools/${poolId}/transactions`),
  })
}

export function useCreditWeights() {
  return useQuery({
    queryKey: ["credit-weights"],
    queryFn: () => apiFetch<CreditFeatureWeightDto[]>("/api/v1/monetization/credits/weights"),
  })
}

export function useCreditWeightUnitCosts() {
  return useQuery({
    queryKey: ["credit-weights", "unit-costs"],
    queryFn: () =>
      apiFetch<Record<string, number>>("/api/v1/monetization/credits/weights/unit-costs"),
  })
}

export function useCreditWeightHistory(featureId: string | undefined, model: string | null) {
  return useQuery({
    queryKey: ["credit-weights", "history", featureId, model],
    queryFn: () =>
      apiFetch<CreditFeatureWeightDto[]>(
        `/api/v1/monetization/credits/weights/history${queryString({ featureId, model: model ?? undefined })}`,
      ),
    enabled: !!featureId,
  })
}
