import { useQuery } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type { CustomerApiKeyDto, KeyBudgetDto } from "@/lib/api/types"

export function useCustomerKeys(customerId: string, enabled = true) {
  return useQuery({
    queryKey: ["customers", customerId, "keys"],
    queryFn: () => apiFetch<CustomerApiKeyDto[]>(`/api/v1/tanso/customers/${customerId}/keys`),
    enabled,
    retry: false,
  })
}

export function useKeyBudget(customerId: string, keyId: string | null) {
  return useQuery({
    queryKey: ["customers", customerId, "keys", keyId, "budget"],
    queryFn: () =>
      apiFetch<KeyBudgetDto>(`/api/v1/tanso/customers/${customerId}/keys/${keyId}/budget`),
    enabled: Boolean(keyId),
  })
}
