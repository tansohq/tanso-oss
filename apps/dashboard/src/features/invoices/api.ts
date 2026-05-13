import { api } from '@/lib/api'
import type { InvoicesResponse, InvoiceDetailResponse } from './types'

export async function fetchInvoices(): Promise<InvoicesResponse> {
  return api.get<InvoicesResponse>('/api/v1/monetization/billing/invoices')
}

export async function fetchInvoice(id: string): Promise<InvoiceDetailResponse> {
  return api.get<InvoiceDetailResponse>(`/api/v1/monetization/billing/invoices/${id}`)
}
