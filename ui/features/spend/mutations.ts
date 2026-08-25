import { useMutation, useQueryClient } from "@tanstack/react-query"

import { apiFetch, apiUpload } from "@/lib/api/client"
import type { VendorConnectionInput } from "./schemas"
import type {
  VendorConnectionDto,
  VendorInvoiceDto,
  VendorProbeResultDto,
  VendorProvider,
  VendorSyncResultDto,
} from "./types"

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

export function useProbeVendorConnection() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiFetch<VendorProbeResultDto>(`/api/v1/spend/connections/${id}/probe`, {
        method: "POST",
      }),
    onSettled: () =>
      queryClient.invalidateQueries({ queryKey: ["vendor-connections"] }),
  })
}

export function useSyncVendorConnection() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiFetch<VendorSyncResultDto>(`/api/v1/spend/connections/${id}/sync`, {
        method: "POST",
      }),
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["vendor-connections"] })
      queryClient.invalidateQueries({ queryKey: ["spend-usage"] })
      queryClient.invalidateQueries({ queryKey: ["spend-reconcile"] })
    },
  })
}

export interface ImportInvoiceInput {
  provider: VendorProvider
  periodStart: string
  periodEnd: string
  currency: string
  file: File
}

export function useImportVendorInvoice() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: ImportInvoiceInput) => {
      const form = new FormData()
      form.set("provider", input.provider)
      form.set("periodStart", input.periodStart)
      form.set("periodEnd", input.periodEnd)
      form.set("currency", input.currency)
      form.set("file", input.file)
      return apiUpload<VendorInvoiceDto>("/api/v1/spend/invoices", form)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["vendor-invoices"] })
      queryClient.invalidateQueries({ queryKey: ["spend-reconcile"] })
    },
  })
}

export function useDeleteVendorInvoice() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiFetch<void>(`/api/v1/spend/invoices/${id}`, { method: "DELETE" }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["vendor-invoices"] })
      queryClient.invalidateQueries({ queryKey: ["spend-reconcile"] })
    },
  })
}
