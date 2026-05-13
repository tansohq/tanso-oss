import { useQuery } from '@tanstack/vue-query'
import { fetchInvoices, fetchInvoice } from './api'
import type { MaybeRef } from 'vue'
import { toValue } from 'vue'

export function useInvoicesQuery() {
  return useQuery({
    queryKey: ['invoices'],
    queryFn: fetchInvoices
  })
}

export function useInvoiceDetailQuery(invoiceId: MaybeRef<string>) {
  return useQuery({
    queryKey: ['invoice', invoiceId],
    queryFn: () => fetchInvoice(toValue(invoiceId)),
    enabled: !!toValue(invoiceId)
  })
}
