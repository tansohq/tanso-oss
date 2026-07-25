import { useQuery } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type {
  CreditGrantDto,
  CreditModelDto,
  CreditPoolDto,
  CreditTransactionDto,
} from "@/lib/api/types"

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
