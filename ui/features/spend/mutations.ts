import { useMutation, useQueryClient } from "@tanstack/react-query"

import { apiFetch, apiUpload } from "@/lib/api/client"
import type { VendorConnectionInput } from "./schemas"
import type {
  OutcomeDto,
  OutcomeKind,
  OutcomeSource,
  OutcomeSourceDto,
  AttributionMatchKind,
  BudgetMode,
  SpendAlertDto,
  SpendAttributionRuleDto,
  SpendBudgetDto,
  SpendSettingsDto,
  SpendUnitDto,
  SpendUnitType,
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

export function useReplaceVendorKey() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { id: string; adminKey: string }) =>
      apiFetch<VendorConnectionDto>(
        `/api/v1/spend/connections/${input.id}/key`,
        {
          method: "PUT",
          body: JSON.stringify({ adminKey: input.adminKey }),
        }
      ),
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

// ---- phase 2: allocate + control
export interface SpendUnitInput {
  type: SpendUnitType
  name: string
  email?: string
  githubLogin?: string
  parentId?: string
}

function invalidateAllocation(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: ["spend-units"] })
  queryClient.invalidateQueries({ queryKey: ["spend-rules"] })
  queryClient.invalidateQueries({ queryKey: ["spend-allocation"] })
  queryClient.invalidateQueries({ queryKey: ["spend-budget"] })
}

export function useCreateSpendUnit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: SpendUnitInput) =>
      apiFetch<SpendUnitDto>("/api/v1/spend/units", {
        method: "POST",
        body: JSON.stringify(input),
      }),
    onSuccess: () => invalidateAllocation(queryClient),
  })
}

export function useUpdateSpendUnit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { id: string } & SpendUnitInput) =>
      apiFetch<SpendUnitDto>(`/api/v1/spend/units/${input.id}`, {
        method: "PUT",
        body: JSON.stringify({
          type: input.type,
          name: input.name,
          email: input.email,
          parentId: input.parentId,
        }),
      }),
    onSuccess: () => invalidateAllocation(queryClient),
  })
}

export function useDeleteSpendUnit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiFetch<void>(`/api/v1/spend/units/${id}`, { method: "DELETE" }),
    onSuccess: () => invalidateAllocation(queryClient),
  })
}

export interface SpendRuleInput {
  spendUnitId: string
  provider: VendorProvider
  matchKind: AttributionMatchKind
  matchValue: string
  priority?: number
}

export function useCreateSpendRule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: SpendRuleInput) =>
      apiFetch<SpendAttributionRuleDto>("/api/v1/spend/rules", {
        method: "POST",
        body: JSON.stringify(input),
      }),
    onSuccess: () => invalidateAllocation(queryClient),
  })
}

export function useDeleteSpendRule() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiFetch<void>(`/api/v1/spend/rules/${id}`, { method: "DELETE" }),
    onSuccess: () => invalidateAllocation(queryClient),
  })
}

export interface SpendBudgetInput {
  unitId: string
  dailyCents: number | null
  monthlyCents: number | null
  alertThreshold: number
  monthlyMode: BudgetMode
}

export function usePutSpendBudget() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: SpendBudgetInput) =>
      apiFetch<SpendBudgetDto>(`/api/v1/spend/units/${input.unitId}/budget`, {
        method: "PUT",
        body: JSON.stringify({
          dailyCents: input.dailyCents,
          monthlyCents: input.monthlyCents,
          alertThreshold: input.alertThreshold,
          monthlyMode: input.monthlyMode,
        }),
      }),
    onSuccess: (_d, input) => {
      queryClient.invalidateQueries({
        queryKey: ["spend-budget", input.unitId],
      })
      queryClient.invalidateQueries({ queryKey: ["spend-alerts"] })
    },
  })
}

export function useDeleteSpendBudget() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (unitId: string) =>
      apiFetch<void>(`/api/v1/spend/units/${unitId}/budget`, {
        method: "DELETE",
      }),
    onSuccess: (_d, unitId) =>
      queryClient.invalidateQueries({ queryKey: ["spend-budget", unitId] }),
  })
}

export function useEvaluateBudgets() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiFetch<SpendAlertDto[]>("/api/v1/spend/budgets/evaluate", {
        method: "POST",
      }),
    onSettled: () =>
      queryClient.invalidateQueries({ queryKey: ["spend-alerts"] }),
  })
}

export function useAckSpendAlert() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiFetch<SpendAlertDto>(`/api/v1/spend/alerts/${id}/ack`, {
        method: "POST",
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["spend-alerts"] }),
  })
}

export interface SpendSettingsInput {
  personLevelEnabled?: boolean
  workerNotice?: string
  slackWebhookUrl?: string
}

export function useUpdateSpendSettings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: SpendSettingsInput) =>
      apiFetch<SpendSettingsDto>("/api/v1/spend/settings", {
        method: "PUT",
        body: JSON.stringify(input),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["spend-settings"] })
      queryClient.invalidateQueries({ queryKey: ["spend-usage"] })
      queryClient.invalidateQueries({ queryKey: ["spend-allocation"] })
    },
  })
}

// ---- phase 3: outcomes
export interface OutcomeSourceInput {
  source: OutcomeSource
  label: string
  token: string
  scope: string
  defaultSpendUnitId?: string
}

function invalidateOutcomes(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: ["outcome-sources"] })
  queryClient.invalidateQueries({ queryKey: ["outcomes"] })
  queryClient.invalidateQueries({ queryKey: ["outcome-report"] })
}

export function useCreateOutcomeSource() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: OutcomeSourceInput) =>
      apiFetch<OutcomeSourceDto>("/api/v1/spend/outcome-sources", {
        method: "POST",
        body: JSON.stringify(input),
      }),
    onSuccess: () => invalidateOutcomes(queryClient),
  })
}

export function useDeleteOutcomeSource() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiFetch<void>(`/api/v1/spend/outcome-sources/${id}`, {
        method: "DELETE",
      }),
    onSuccess: () => invalidateOutcomes(queryClient),
  })
}

export function useProbeOutcomeSource() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiFetch<VendorProbeResultDto>(
        `/api/v1/spend/outcome-sources/${id}/probe`,
        { method: "POST" }
      ),
    onSettled: () =>
      queryClient.invalidateQueries({ queryKey: ["outcome-sources"] }),
  })
}

export function useSyncOutcomeSource() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiFetch<VendorSyncResultDto>(
        `/api/v1/spend/outcome-sources/${id}/sync`,
        { method: "POST" }
      ),
    onSettled: () => invalidateOutcomes(queryClient),
  })
}

export interface OutcomeInput {
  kind: OutcomeKind
  externalId: string
  title?: string
  url?: string
  actorEmail?: string
  actorLogin?: string
  spendUnitId?: string
}

export function useRecordOutcome() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: OutcomeInput) =>
      apiFetch<OutcomeDto>("/api/v1/spend/outcomes", {
        method: "POST",
        body: JSON.stringify(input),
      }),
    onSuccess: () => invalidateOutcomes(queryClient),
  })
}
