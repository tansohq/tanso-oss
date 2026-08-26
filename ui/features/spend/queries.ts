import { useQuery } from "@tanstack/react-query"

import { ApiError, apiFetch, queryString } from "@/lib/api/client"
import type {
  SpendReconcileReportDto,
  SpendUsageReportDto,
  VendorConnectionDto,
  VendorInvoiceDto,
} from "./types"

/** True when the build side is switched off (APP_MODULES_BUILD_ENABLED=false): the routes 404. */
export function isBuildSideOff(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404
}

export function useVendorConnections() {
  return useQuery({
    queryKey: ["vendor-connections"],
    queryFn: () => apiFetch<VendorConnectionDto[]>("/api/v1/spend/connections"),
    retry: (count, error) => !isBuildSideOff(error) && count < 3,
  })
}

export function useVendorInvoices() {
  return useQuery({
    queryKey: ["vendor-invoices"],
    queryFn: () => apiFetch<VendorInvoiceDto[]>("/api/v1/spend/invoices"),
  })
}

export function useSpendUsage(from: string, to: string) {
  return useQuery({
    queryKey: ["spend-usage", from, to],
    queryFn: () =>
      apiFetch<SpendUsageReportDto>(
        `/api/v1/spend/reports/usage${queryString({ from, to })}`
      ),
    enabled: !!from && !!to,
  })
}

export function useSpendReconcile(from: string, to: string) {
  return useQuery({
    queryKey: ["spend-reconcile", from, to],
    queryFn: () =>
      apiFetch<SpendReconcileReportDto>(
        `/api/v1/spend/reports/reconcile${queryString({ from, to })}`
      ),
    enabled: !!from && !!to,
  })
}
