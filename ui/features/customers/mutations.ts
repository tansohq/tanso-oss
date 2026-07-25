import { useMutation, useQueryClient } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type { CustomerDto } from "@/lib/api/types"
import type { CustomerInput } from "./schemas"

export function useCreateCustomer() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CustomerInput) =>
      apiFetch<CustomerDto>("/api/v1/monetization/customers", {
        method: "POST",
        body: JSON.stringify(input),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["customers"] }),
  })
}

export function useUpdateCustomer(customerId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CustomerInput) =>
      apiFetch<CustomerDto>(`/api/v1/monetization/customers/${customerId}`, {
        method: "PATCH",
        body: JSON.stringify(input),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["customers"] }),
  })
}
