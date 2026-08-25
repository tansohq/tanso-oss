export type {
  VendorConnectionDto,
  VendorProbeResultDto,
  VendorSyncResultDto,
} from "@/lib/api/types"

// Report shapes are hand-written on purpose: the generated schema marks every field optional,
// which would push null-handling into every cell. Keep in step with model/spend/*.java.
export type VendorProvider = "ANTHROPIC" | "OPENAI"

export interface VendorInvoiceLineDto {
  description: string
  kind: "TOKEN" | "SEAT" | "TOOL" | "OTHER"
  model?: string
  quantity?: number
  amountCents: number
}

export interface VendorInvoiceDto {
  id: string
  provider: VendorProvider
  periodStart: string
  periodEnd: string
  currency: string
  totalCents: number
  importedFrom?: string
  createdAt?: string
  lines: VendorInvoiceLineDto[]
}

export interface SpendUsageReportDto {
  from: string
  to: string
  totals: {
    uncachedInputTokens: number
    cacheReadTokens: number
    cacheCreationTokens: number
    outputTokens: number
    requests: number
    vendorCostCents: number
    meteredCostCents: number
  }
  byModel: {
    provider: VendorProvider
    model?: string
    uncachedInputTokens: number
    cacheReadTokens: number
    cacheCreationTokens: number
    outputTokens: number
    requests: number
    meteredCostCents: number
    vendorCostCents?: number
    priced: boolean
  }[]
  byDay: {
    date: string
    totalTokens: number
    meteredCostCents: number
    vendorCostCents: number
  }[]
  byActor: {
    provider: VendorProvider
    actor: string
    totalTokens: number
    sessions: number
    vendorCostCents?: number
    meteredCostCents: number
  }[]
  unpricedModels: string[]
}

export interface SpendReconcileRowDto {
  provider: VendorProvider
  meteredCents: number
  meteredIsEstimate: boolean
  vendorReportedCents: number
  invoicedCents?: number
  invoiceCount: number
  meteredVsVendorCents: number
  vendorVsInvoiceCents?: number
}

export interface SpendReconcileReportDto {
  from: string
  to: string
  rows: SpendReconcileRowDto[]
}
