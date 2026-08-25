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
    requests?: number | null
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
    requests?: number | null
    meteredCostCents: number
    vendorCostCents?: number | null
    priced: boolean
    cacheRatesKnown: boolean
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

// ---- phase 2: allocate + control
export type SpendUnitType = "TEAM" | "PERSON" | "PROJECT"
export type AttributionMatchKind = "WORKSPACE_ID" | "API_KEY_ID" | "ACTOR"
export type SpendAlertKind = "THRESHOLD" | "BREACH" | "SPIKE"
export type BudgetMode = "ALERT" | "BLOCK"

export interface SpendUnitDto {
  id: string
  type: SpendUnitType
  name: string
  email?: string
  githubLogin?: string
  parentId?: string
  createdAt?: string
}

export interface SpendAttributionRuleDto {
  id: string
  spendUnitId: string
  provider: VendorProvider
  matchKind: AttributionMatchKind
  matchValue: string
  priority: number
}

export interface SpendAllocationRowDto {
  unitId: string
  name: string
  type: SpendUnitType
  parentId?: string
  ownCents: number
  totalCents: number
  personEstimateCents?: number | null
  spendCents: number
}

export interface SpendAllocationReportDto {
  from: string
  to: string
  rows: SpendAllocationRowDto[]
  unattributedCents: number
  totalMeteredCents: number
  personLevelEnabled: boolean
}

export interface SpendBudgetDto {
  spendUnitId: string
  dailyCents?: number | null
  monthlyCents?: number | null
  alertThreshold: number
  monthlyMode: BudgetMode
  dailySpentCents: number
  monthlySpentCents: number
  dailyResetsAt?: string
  monthlyResetsAt?: string
}

export interface SpendAlertDto {
  id: string
  spendUnitId: string
  unitName?: string
  kind: SpendAlertKind
  period?: "DAY" | "MONTH" | null
  windowStart: string
  spentCents: number
  limitCents?: number | null
  message: string
  firedAt: string
  ackedAt?: string | null
  ackedBy?: string | null
}

export interface SpendSettingsDto {
  personLevelEnabled: boolean
  workerNotice?: string | null
  slackConfigured: boolean
}

// ---- phase 3: outcomes
export type OutcomeSource = "GITHUB" | "LINEAR" | "MANUAL"
export type OutcomeKind = "PR_MERGED" | "ISSUE_DONE" | "CUSTOM"

export interface OutcomeSourceDto {
  id: string
  source: OutcomeSource
  label: string
  scope: string
  defaultSpendUnitId?: string | null
  status: "ACTIVE" | "ERROR"
  lastError?: string | null
  lastSyncedAt?: string | null
  createdAt?: string
}

export interface OutcomeDto {
  id: string
  source: OutcomeSource
  kind: OutcomeKind
  externalId: string
  title?: string | null
  url?: string | null
  actorEmail?: string | null
  actorLogin?: string | null
  spendUnitId?: string | null
  unitName?: string | null
  occurredAt: string
}

export interface SpendOutcomeRowDto {
  unitId: string
  name: string
  type: SpendUnitType
  parentId?: string | null
  prsMerged: number
  issuesDone: number
  custom: number
  outcomes: number
  spendCents: number
  personEstimateCents?: number | null
  costPerOutcomeCents?: number | null
}

export interface SpendOutcomeReportDto {
  from: string
  to: string
  rows: SpendOutcomeRowDto[]
  totalOutcomes: number
  unattributedOutcomes: number
  totalSpendCents: number
  costPerOutcomeCents?: number | null
}
