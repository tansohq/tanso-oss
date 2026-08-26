import { useQuery } from "@tanstack/react-query"

import { ApiError, apiFetch, queryString } from "@/lib/api/client"
import type {
  OutcomeDto,
  OutcomeSourceDto,
  SpendOutcomeReportDto,
  SpendAlertDto,
  SpendAllocationReportDto,
  SpendAttributionRuleDto,
  SpendBudgetDto,
  SpendReconcileReportDto,
  SpendSettingsDto,
  SpendUnitDto,
  SpendUsageReportDto,
  VendorConnectionDto,
  VendorInvoiceDto,
  SpendDigestDto,
  SpendSavingsReportDto,
  PriceBookModelDto,
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

export function useSpendUnits() {
  return useQuery({
    queryKey: ["spend-units"],
    queryFn: () => apiFetch<SpendUnitDto[]>("/api/v1/spend/units"),
  })
}

export function useSpendRules() {
  return useQuery({
    queryKey: ["spend-rules"],
    queryFn: () => apiFetch<SpendAttributionRuleDto[]>("/api/v1/spend/rules"),
  })
}

export function useSpendAllocation(from: string, to: string) {
  return useQuery({
    queryKey: ["spend-allocation", from, to],
    queryFn: () =>
      apiFetch<SpendAllocationReportDto>(
        `/api/v1/spend/reports/allocation${queryString({ from, to })}`
      ),
    enabled: !!from && !!to,
    retry: (count, error) => !isBuildSideOff(error) && count < 3,
  })
}

export function useSpendBudget(unitId: string | null) {
  return useQuery({
    queryKey: ["spend-budget", unitId],
    queryFn: () =>
      apiFetch<SpendBudgetDto>(`/api/v1/spend/units/${unitId}/budget`),
    enabled: !!unitId,
    retry: false,
  })
}

export function useSpendAlerts(unackedOnly: boolean) {
  return useQuery({
    queryKey: ["spend-alerts", unackedOnly],
    queryFn: () =>
      apiFetch<SpendAlertDto[]>(
        `/api/v1/spend/alerts${queryString({ unackedOnly: String(unackedOnly) })}`
      ),
  })
}

export function useSpendSettings() {
  return useQuery({
    queryKey: ["spend-settings"],
    queryFn: () => apiFetch<SpendSettingsDto>("/api/v1/spend/settings"),
    retry: (count, error) => !isBuildSideOff(error) && count < 3,
  })
}

export function useSpendDigest() {
  return useQuery({
    queryKey: ["spend-digest"],
    queryFn: () => apiFetch<SpendDigestDto>("/api/v1/spend/digest"),
  })
}

export function useSpendSavings(from: string, to: string) {
  return useQuery({
    queryKey: ["spend-savings", from, to],
    queryFn: () =>
      apiFetch<SpendSavingsReportDto>(
        `/api/v1/spend/reports/savings${queryString({ from, to })}`
      ),
    enabled: !!from && !!to,
  })
}

export function usePriceBookModels() {
  return useQuery({
    queryKey: ["spend-models"],
    queryFn: () =>
      apiFetch<PriceBookModelDto[]>("/api/v1/spend/reports/models"),
  })
}

export function useOutcomeSources() {
  return useQuery({
    queryKey: ["outcome-sources"],
    queryFn: () =>
      apiFetch<OutcomeSourceDto[]>("/api/v1/spend/outcome-sources"),
    retry: (count, error) => !isBuildSideOff(error) && count < 3,
  })
}

export function useRecentOutcomes() {
  return useQuery({
    queryKey: ["outcomes"],
    queryFn: () => apiFetch<OutcomeDto[]>("/api/v1/spend/outcomes"),
  })
}

export function useOutcomeReport(from: string, to: string) {
  return useQuery({
    queryKey: ["outcome-report", from, to],
    queryFn: () =>
      apiFetch<SpendOutcomeReportDto>(
        `/api/v1/spend/reports/outcomes${queryString({ from, to })}`
      ),
    enabled: !!from && !!to,
    retry: (count, error) => !isBuildSideOff(error) && count < 3,
  })
}
