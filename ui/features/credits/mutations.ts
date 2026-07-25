import { useMutation, useQueryClient } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type { CreditGrantDto, CreditModelDto, CreditPoolDto } from "@/lib/api/types"
import type { CreditGrantInput, CreditModelInput, CreditPoolInput } from "./schemas"
import type { CreditFeatureWeightDto, PublishCreditWeightsInput } from "./types"

export function useCreateCreditModel() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreditModelInput) =>
      apiFetch<CreditModelDto>("/api/v1/monetization/credits/models", {
        method: "POST",
        body: JSON.stringify(input),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["credit-models"] }),
  })
}

export function useCreateCreditPool() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreditPoolInput) =>
      apiFetch<CreditPoolDto>("/api/v1/monetization/credits/pools", {
        method: "POST",
        body: JSON.stringify({ ...input, customerId: input.customerId || undefined }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["credit-pools"] }),
  })
}

export function usePublishCreditWeights() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: PublishCreditWeightsInput) =>
      apiFetch<CreditFeatureWeightDto[]>("/api/v1/monetization/credits/weights/publish", {
        method: "POST",
        body: JSON.stringify(input),
      }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ["credit-weights"] }),
  })
}

export function useDeleteCreditWeight() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (weightId: string) =>
      apiFetch<void>(`/api/v1/monetization/credits/weights/${weightId}`, { method: "DELETE" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["credit-weights"] }),
  })
}

export function useCreateCreditGrant(poolId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreditGrantInput) =>
      apiFetch<CreditGrantDto>("/api/v1/monetization/credits/grants", {
        method: "POST",
        body: JSON.stringify({
          ...input,
          creditPoolId: poolId,
          expiresAt: input.expiresAt ? new Date(input.expiresAt).toISOString() : undefined,
        }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["credit-pools"] }),
  })
}
