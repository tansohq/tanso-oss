import { useMutation, useQueryClient } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type { CustomerApiKeyDto, KeyBudgetDto } from "@/lib/api/types"

const keysKey = (customerId: string) => ["customers", customerId, "keys"]

export function useCreateCustomerKey(customerId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (scopes: string[]) =>
      apiFetch<CustomerApiKeyDto>(`/api/v1/tanso/customers/${customerId}/keys`, {
        method: "POST",
        body: JSON.stringify({ scopes }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keysKey(customerId) }),
  })
}

export function useRotateCustomerKey(customerId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (keyId: string) =>
      apiFetch<CustomerApiKeyDto>(`/api/v1/tanso/customers/${customerId}/keys/${keyId}/rotate`, {
        method: "POST",
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keysKey(customerId) }),
  })
}

export function useRevokeCustomerKey(customerId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (keyId: string) =>
      apiFetch<void>(`/api/v1/tanso/customers/${customerId}/keys/${keyId}`, { method: "DELETE" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keysKey(customerId) }),
  })
}

export function useSetKeyBudget(customerId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({
      keyId,
      period,
      creditLimit,
      amountLimit,
      alertThreshold,
    }: {
      keyId: string
      period: string
      creditLimit: string
      amountLimit: string
      alertThreshold: string
    }) =>
      apiFetch<KeyBudgetDto>(`/api/v1/tanso/customers/${customerId}/keys/${keyId}/budget`, {
        method: "PUT",
        body: JSON.stringify({
          period,
          // An empty field means "leave this axis unlimited", which the API
          // expresses as null rather than zero — zero would cap it at nothing.
          creditLimit: creditLimit === "" ? null : Number(creditLimit),
          amountLimit: amountLimit === "" ? null : Number(amountLimit),
          // Omitted means "leave the threshold as it is" — the API only changes
          // it when a value is sent, so an empty field must not send one.
          alertThreshold: alertThreshold === "" ? undefined : Number(alertThreshold),
        }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keysKey(customerId) }),
  })
}

export function useClearKeyBudget(customerId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (keyId: string) =>
      apiFetch<void>(`/api/v1/tanso/customers/${customerId}/keys/${keyId}/budget`, {
        method: "DELETE",
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keysKey(customerId) }),
  })
}
