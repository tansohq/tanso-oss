import { useQuery } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import { newestFirst } from "@/lib/newest-first"
import type { InvoiceDto } from "@/lib/api/types"

export function useInvoices() {
  return useQuery({
    queryKey: ["invoices"],
    queryFn: () => apiFetch<InvoiceDto[]>("/api/v1/monetization/billing/invoices"),
    select: newestFirst,
  })
}

export function useInvoice(invoiceId: string | null) {
  return useQuery({
    queryKey: ["invoices", invoiceId],
    queryFn: () => apiFetch<InvoiceDto>(`/api/v1/monetization/billing/invoices/${invoiceId}`),
    enabled: !!invoiceId,
  })
}
