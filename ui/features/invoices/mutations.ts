import { useMutation, useQueryClient } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type { InvoiceDto } from "@/lib/api/types"

export function useMarkInvoicePaid() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (invoiceId: string) =>
      apiFetch<InvoiceDto>(`/api/v1/monetization/billing/invoices/${invoiceId}`, {
        method: "PATCH",
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["invoices"] }),
  })
}
