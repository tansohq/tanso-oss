import { useMutation, useQueryClient } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type { VendorConnectionInput } from "./schemas"
import type { VendorConnectionDto } from "./types"

export function useCreateVendorConnection() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: VendorConnectionInput) =>
      apiFetch<VendorConnectionDto>("/api/v1/spend/connections", {
        method: "POST",
        body: JSON.stringify(input),
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["vendor-connections"] }),
  })
}

export function useDeleteVendorConnection() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiFetch<void>(`/api/v1/spend/connections/${id}`, { method: "DELETE" }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["vendor-connections"] }),
  })
}
