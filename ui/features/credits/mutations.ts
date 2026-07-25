import { useMutation, useQueryClient } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type { CreditGrantDto, CreditModelDto, CreditPoolDto } from "@/lib/api/types"
import type { CreditGrantInput, CreditModelInput, CreditPoolInput } from "./schemas"

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
