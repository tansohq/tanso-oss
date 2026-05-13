// ============ EXPERIMENT READINESS ============

export type ExperimentReadiness = 'too_small' | 'small_sample' | 'ready'

export interface ReadinessInfo {
  status: ExperimentReadiness
  label: string
  description: string
  color: 'red' | 'yellow' | 'green'
  experimentMethod: 'qualitative' | 'switchback' | 'standard_ab'
  estimatedDuration: string
}

export function getExperimentReadiness(accountCount: number): ReadinessInfo {
  if (accountCount < 15) {
    return {
      status: 'too_small',
      label: 'Too small for experiments',
      description: 'Use for qualitative research or combine segments',
      color: 'red',
      experimentMethod: 'qualitative',
      estimatedDuration: 'N/A'
    }
  }
  if (accountCount <= 40) {
    return {
      status: 'small_sample',
      label: 'Small sample (~12 weeks)',
      description: 'Uses switchback design for statistical validity',
      color: 'yellow',
      experimentMethod: 'switchback',
      estimatedDuration: '~12 weeks'
    }
  }
  return {
    status: 'ready',
    label: 'Ready (~6-8 weeks)',
    description: 'Standard A/B test',
    color: 'green',
    experimentMethod: 'standard_ab',
    estimatedDuration: '~6-8 weeks'
  }
}

// ============ SEGMENTS ============

export type SegmentFieldCategory =
  | 'commercial'
  | 'commercial_ai'
  | 'behavioral'
  | 'margin_cost'
  | 'ai_native'
  | 'signals'

export interface SegmentFieldOption {
  value: string
  label: string
  category: SegmentFieldCategory
  categoryLabel: string
  type: 'numeric' | 'select' | 'boolean' | 'date' | 'comparative'
  options?: { value: string; label: string }[]
}

export type SegmentOperator =
  | 'equals'
  | 'not_equals'
  | 'less_than'
  | 'at_most'
  | 'greater_than'
  | 'at_least'
  | 'between'
  | 'is'
  | 'is_not'
  | 'is_any_of'
  | 'is_true'
  | 'is_false'
  | 'within_last_days'
  | 'more_than_days_ago'
  | 'above_average'
  | 'below_average'

export interface SegmentRule {
  id: string
  field: string
  fieldLabel: string
  operator: SegmentOperator
  operatorLabel: string
  value: string | number | [number, number] | string[]
  valueLabel?: string
}

export type TrendDirection = 'up' | 'down' | 'stable'

export interface SegmentCustomer {
  id: string
  name: string
  email: string
  mrr: number
  carr: number
  uarr: number
  grossMarginPct: number | null
  marginTrend: number | null
  costPerSeat: number | null
  apiCost30d: number | null
  plan: string
  seats: number
  customerSince: string
  inSegmentSince: string
  segments: string[]
  activeExperiments: { id: string; name: string; variant: string; enrolledDaysAgo: number }[]
  automationHistory: { name: string; triggeredAt: string; actions: string[] }[]
  marginTrendData: { date: string; margin: number }[]
}

export interface Segment {
  id: string
  name: string
  icon: string
  color: string
  customerCount: number
  customerTrend: TrendDirection
  customerTrendValue?: number
  mrr: number
  carr: number
  uarr: number
  grossMarginPct: number | null
  marginTrend: TrendDirection
  marginTrendValue?: number
  rules: SegmentRule[]
  automationCount: number
  experimentCount: number
  linkedAutomationNames: string[]
  linkedExperimentNames: string[]
  lastUpdated: string
  status: 'active' | 'archived'
  customers: SegmentCustomer[]
  marginDistribution: { range: string; count: number }[]
}

export type SegmentFilter = 'all' | 'ready' | 'small_sample' | 'too_small'

// ============ AUTOMATIONS ============

// Unified automation types: alerts notify, actions do, experiments test
export type AutomationType = 'alert' | 'action' | 'experiment'

export type AutomationTriggerType =
  | 'segment_entry'
  | 'segment_exit'
  | 'margin_threshold'
  | 'cost_spike'
  | 'usage_threshold'
  | 'time_in_segment'
  | 'ramp_rate_below'
  | 'scheduled'

export interface AutomationTrigger {
  type: AutomationTriggerType
  typeLabel: string
  segmentId?: string
  segmentName?: string
  threshold?: number
  unit?: string
  direction?: 'above' | 'below'
  scheduleFrequency?: 'daily' | 'weekly'
  scheduleDay?: string
  scheduleTime?: string
}

export type AutomationActionType =
  | 'slack'
  | 'email'
  | 'in_app'
  | 'crm_update'
  | 'feature_access'
  | 'run_experiment'
  | 'usage_limit'
  | 'create_task'

export interface AutomationAction {
  type: AutomationActionType
  typeLabel: string
  icon: string
  config: Record<string, unknown>
}

export interface Automation {
  id: string
  name: string
  type: AutomationType
  trigger: AutomationTrigger
  conditions: SegmentRule[]
  actions: AutomationAction[]
  status: 'active' | 'paused' | 'running' | 'completed' | 'stopped'
  frequencyCap: {
    maxTriggers: number
    periodDays: number
  }
  stats: {
    triggeredThisWeek: number
    lastTriggered: string | null
    actionsExecuted: number
  }
  pauseDuringExperiments: boolean
  pausedByExperimentId?: string
  pausedByExperimentName?: string
  pausedCustomerCount?: number
  // Experiment-specific fields (when type === 'experiment')
  experiment?: {
    segmentId: string
    segmentName: string
    segmentSize: number
    phase: ExperimentPhase
    trend?: ExperimentTrend
    successMetric: SuccessMetric
    marginFloor: number
    weekNumber: number
    totalWeeks: number
    stoppedReason?: string
    srmWarning?: boolean
  }
}

// ============ EXPERIMENTS ============

export type ExperimentStatus = 'draft' | 'running' | 'completed' | 'stopped'

export type ExperimentPhase =
  | 'early_data' // < 14 days, hide detailed stats
  | 'trending' // 14-21 days, show trend signals
  | 'ready_to_call' // Statistical significance reached or min duration met
  | 'stopped' // Guardrail violation stopped it
  | 'no_clear_winner' // Completed without definitive winner

export type ExperimentTrend = 'positive' | 'negative' | 'neutral'

export type SuccessMetric = 'conversion' | 'revenue' | 'margin' | 'retention'

export type GuardrailStatus = 'healthy' | 'warning' | 'violated' | null

export interface ExperimentVariant {
  id: string
  name: string
  description: string
  weight: number
  metrics: {
    accountCount: number
    conversionRate: number | null
    conversionLift: number | null
    conversionPValue: number | null
    revenuePerAccount: number | null
    marginPerAccount: number | null
    grossMarginPct: number | null
    marginDelta: number | null
    unprofitableAccounts: number
  }
  guardrailStatus: GuardrailStatus
}

export interface ExperimentGuardrail {
  id: string
  type: 'margin_floor' | 'cost_ceiling' | 'revenue_floor' | 'unprofitable_threshold'
  label: string
  enabled: boolean
  value: number
  unit: string
}

export interface ExperimentGuardrails {
  marginFloor: { enabled: boolean; value: number }
  costCeiling: { enabled: boolean; value: number }
  revenueFloor: { enabled: boolean; value: number }
  unprofitableThreshold: { enabled: boolean; value: number }
  behavior: 'soft_stop' | 'hard_stop'
  evaluationWindowDays: number
}

export interface ExperimentRecommendation {
  winner: string
  winnerVariantId: string
  confidence: number
  rationale: string[]
}

// B2B-realistic experiment categories
export type ExperimentCategory =
  | 'expansion_timing' // When/how to prompt upgrades
  | 'plan_recommendation' // Which plan to suggest
  | 'retention_strategy' // What offers save at-risk accounts
  | 'feature_routing' // Which backend serves requests
  | 'messaging' // How pricing is communicated
  | 'sales_enablement' // What terms sales can offer
  | 'model_routing' // A/B test different AI models

export type AudienceType =
  | 'new_signups' // New signups during test period
  | 'expansion_eligible' // Accounts approaching usage limits
  | 'churn_risk' // Accounts flagged as churn risk
  | 'sales_opportunities' // Sales-assisted deals
  | 'feature_eligible' // Accounts using specific feature
  | 'api_requests' // Request-level, not account-level

// ============ MODEL ROUTING EXPERIMENTS ============

export interface RoutingMetrics {
  requests: number
  qualityScore: number // e.g., 4.2 out of 5
  completionRate: number // e.g., 0.97
  avgLatencyMs: number // e.g., 1200
  totalCost: number // e.g., 4120
  costPerRequest: number // e.g., 0.0097
}

export interface RoutingExperimentData {
  taskType: string // "summarization", "classification", etc.
  qualityThreshold: number // e.g., 4.0
  totalRequests: number // e.g., 847000
  controlModel: string // e.g., "claude-sonnet-3.5"
  challengerModel: string // e.g., "gpt-4o-mini"
  controlCostPer1M: number // e.g., 3.00
  challengerCostPer1M: number // e.g., 0.15
  controlMetrics: RoutingMetrics
  challengerMetrics: RoutingMetrics
  projectedAnnualSavings?: number
}

export interface Experiment {
  id: string
  name: string
  status: ExperimentStatus
  phase: ExperimentPhase
  trend?: ExperimentTrend
  stoppedReason?: string
  srmWarning?: boolean
  successMetric: SuccessMetric
  autoPauseAutomations: boolean
  pausedAutomationIds: string[]
  variants: ExperimentVariant[]
  segmentId: string
  segmentName: string
  segmentSize: number
  guardrails: ExperimentGuardrails
  startDate: string | null
  endDate: string | null
  durationDays: number | null
  recommendation: ExperimentRecommendation | null
  marginTrendData: { date: string; control: number; variantA?: number; variantB?: number }[]
  unprofitableByVariant: {
    variantId: string
    variantName: string
    total: number
    unprofitable: number
    pctUnprofitable: number
    avgMarginUnprofitable: number
  }[]
  // B2B-realistic experiment context
  category: ExperimentCategory
  audienceType: AudienceType
  audienceDescription: string
  audienceNote?: string
  testingQuestion: string
  controlDescription: string
  variantDescription: string
  hypothesis?: string
  // Model routing experiment data (only present for model_routing category)
  routing?: RoutingExperimentData
}

// ============ SIMULATIONS ============

export type SimulationStatus = 'draft' | 'running' | 'completed' | 'rolled_out'

export type PricingChangeType =
  | 'percentage_increase'
  | 'percentage_decrease'
  | 'flat_monthly'
  | 'per_unit'

export type ChurnRisk = 'high' | 'medium' | 'low'

export interface PricingChange {
  type: PricingChangeType
  value: number
  label: string
}

export interface SimulationScenarioResults {
  totalRevenue: number
  avgMargin: number
  churnRiskCount: number
  customerCount: number
}

export interface SimulationScenario {
  id: string
  name: string
  isBaseline: boolean
  pricingChange: PricingChange | null
  results?: SimulationScenarioResults
}

export interface SimulationCustomerImpact {
  customerId: string
  customerName: string
  currentMrr: number
  scenarioMrrs: Record<string, number>
  changePercent: number
  currentMargin: number
  newMargin: number
  churnRisk: ChurnRisk
  contractType?: 'annual' | 'monthly'
}

export interface SimulationSummary {
  scenarioId: string
  scenarioName: string
  revenue: number
  revenueChange: number
  revenuePctChange: number
  margin: number
  marginChange: number
  churnRiskCount: number
  badges: ('highest_revenue' | 'best_margin' | 'lowest_risk')[]
}

export interface SimulationSegmentPreview {
  customerCount: number
  totalMrr: number
  avgMargin: number
}

export interface Simulation {
  id: string
  name: string
  segmentId: string | null
  segmentName: string
  status: SimulationStatus
  timeRange: {
    start: string
    end: string
  }
  scenarios: SimulationScenario[]
  segmentPreview: SimulationSegmentPreview
  summaryTable?: SimulationSummary[]
  customerImpacts?: SimulationCustomerImpact[]
  winningScenarioId?: string
  createdAt: string
  runAt?: string
  rolledOutAt?: string
  rolledOutScenarioId?: string
  // Margin-aware simulation data
  marginImpact?: MarginImpactSummary
  featureAnalysis?: FeatureMarginAnalysis[]
  confidenceScore?: number // 0-100
  keyInsight?: string
}

// ============ MARGIN-AWARE SIMULATION TYPES ============

export type FeatureMarginStatus = 'negative' | 'low' | 'profitable' | 'high'

export interface FeatureMarginAnalysis {
  featureKey: string
  featureName: string
  currentPrice: number
  newPrice: number
  volume: number
  cost: number
  currentRevenue: number
  newRevenue: number
  currentMargin: number
  newMargin: number
  marginDelta: number
  status: FeatureMarginStatus
}

export interface MarginImpactSummary {
  baselineRevenue: number
  scenarioRevenue: number
  revenueDelta: number
  revenueDeltaPct: number
  baselineCost: number // Infrastructure cost (unchanged)
  scenarioCost: number // Same as baseline
  baselineMargin: number
  scenarioMargin: number
  marginDelta: number
  baselineMarginPct: number
  scenarioMarginPct: number
  marginPctDelta: number
}

// ============ PRICING OPPORTUNITIES ============

export type OpportunitySeverity = 'high' | 'medium' | 'low'
export type OpportunityType = 'negative_margin' | 'underpriced' | 'tiering_opportunity'

export interface PricingOpportunity {
  id: string
  type: OpportunityType
  severity: OpportunitySeverity
  featureKey: string
  featureName: string
  title: string
  description: string
  currentPrice: number
  suggestedPrice: number
  volume: number
  cost: number
  monthlyLoss?: number
  potentialGain?: number
  affectedCustomers: number
}

export function calculateChurnRisk(
  priceIncreasePercent: number,
  margin: number,
  isMonthToMonth: boolean
): ChurnRisk {
  if (priceIncreasePercent > 20 || margin < 0 || isMonthToMonth) {
    return 'high'
  }
  if ((priceIncreasePercent > 10 && priceIncreasePercent <= 20) || margin < 30) {
    return 'medium'
  }
  return 'low'
}

export function getChurnRiskColor(risk: ChurnRisk): string {
  switch (risk) {
    case 'high':
      return 'bg-red-100 text-red-700'
    case 'medium':
      return 'bg-amber-100 text-amber-700'
    case 'low':
      return 'bg-emerald-100 text-emerald-700'
  }
}

export function getSimulationStatusColor(status: SimulationStatus): string {
  switch (status) {
    case 'draft':
      return 'bg-slate-100 text-slate-700'
    case 'running':
      return 'bg-blue-100 text-blue-700'
    case 'completed':
      return 'bg-emerald-100 text-emerald-700'
    case 'rolled_out':
      return 'bg-purple-100 text-purple-700'
  }
}

export function getSimulationStatusLabel(status: SimulationStatus): string {
  switch (status) {
    case 'draft':
      return 'Draft'
    case 'running':
      return 'Running'
    case 'completed':
      return 'Completed'
    case 'rolled_out':
      return 'Rolled Out'
  }
}

// ============ INTEGRATIONS ============

export type IntegrationStatus = 'connected' | 'not_connected' | 'error'

export interface Integration {
  id: string
  name: string
  provider: 'stripe' | 'openai' | 'anthropic' | 'aws' | 'gcp' | 'csv' | 'clearbit' | 'apollo' | 'salesforce' | 'hubspot'
  logo: string
  status: IntegrationStatus
  description: string
  lastSync: string | null
  recordCount?: number
  mtdCost?: number
  customerCount?: number
}

// ============ STATS ============

export interface StatItem {
  label: string
  value: string | number
  trend?: number
  trendDirection?: TrendDirection
  format?: 'currency' | 'percentage' | 'number'
  suffix?: string
}

// ============ SEGMENT OPTIMIZATION SUGGESTIONS ============

export interface SegmentOptimizationSuggestions {
  segmentId: string
  currentCount: number
  targetCount: number // 40 for experiment-ready
  relaxRule?: RelaxRuleSuggestion
  combineSegments?: CombineSegmentsSuggestion[]
  waitForGrowth?: WaitForGrowthSuggestion
}

export interface RelaxRuleSuggestion {
  ruleToRelax: SegmentRule
  suggestedChange: {
    attribute: string
    attributeLabel: string
    currentOperator: string
    currentValue: string | number
    suggestedOperator: string
    suggestedValue: string | number
  }
  impact: {
    currentCount: number
    newCount: number
    newReadiness: ExperimentReadiness
    avgArrChange: number // e.g., -0.08 for -8%
    avgMarginChange: number
    similarityScore: number // 0-1, how similar new accounts are to original
  }
  newAccountsPreview: { name: string; arr: number; margin: number }[]
}

export interface CombineSegmentsSuggestion {
  segmentId: string
  segmentName: string
  segmentCount: number
  similarityScore: number // 0-1
  combinedCount: number
  combinedReadiness: ExperimentReadiness
  comparison: { metric: string; thisValue: string; otherValue: string }[]
}

export interface WaitForGrowthSuggestion {
  currentGrowthRate: number // accounts per month
  weeksUntilReady: number
  projectedDate: string // ISO date
  confidence: 'high' | 'medium' | 'low'
}

// ============ CUSTOMERS / ANALYTICS ============

export interface CustomerCostBreakdown {
  inference: number
  storage: number
  compute: number
  total: number
}

export interface CustomerFeatureUsage {
  calls: number
  cost: number
  limit?: number | null // Optional usage limit for the feature
}

export interface CustomerUsageByFeature {
  [feature: string]: CustomerFeatureUsage
}

// Spending alert thresholds configuration
export interface SpendingAlertConfig {
  thresholds: number[] // e.g., [50, 80, 95, 100]
  enabledAlerts: number[] // Which thresholds are active
}

export interface CustomerDatapoint {
  date: string
  value: number
}

export type MarginStatus = 'healthy' | 'at_risk' | 'underwater'
export type TrendType = 'improving' | 'stable' | 'declining'

export interface Customer {
  id: string
  name: string
  email: string

  // Commercial
  plan: string
  mrr: number
  seats: number
  customerSince: string

  // Margin
  margin: number // 0-1 decimal
  marginStatus: MarginStatus
  marginTrend: TrendType
  marginTimeseries: CustomerDatapoint[]

  // Costs
  costs: CustomerCostBreakdown
  costTimeseries: CustomerDatapoint[]

  // Usage
  usage: CustomerUsageByFeature
  usageTrend: TrendType
  usageTimeseries: CustomerDatapoint[]

  // Spending Alerts
  spendingAlerts?: SpendingAlertConfig

  // AI-Native Metrics
  costPerAction: number
  actionsLast30d: number
  modelMixExpensive: number
  revenuePerAction: number

  // Segment membership
  segments: string[]
  inSegmentSince?: string
}

export interface PortfolioSummary {
  totalMrr: number
  totalCosts: number
  avgMargin: number
  marginTrendPp: number
  customersByStatus: {
    healthy: number
    atRisk: number
    underwater: number
  }
  mrrByStatus: {
    healthy: number
    atRisk: number
    underwater: number
  }
}

// Legacy aliases for backwards compatibility
export type AccountCostBreakdown = CustomerCostBreakdown
export type AccountUsageByFeature = CustomerUsageByFeature
export type MarginTrendType = TrendType
export type Account = Customer

// ============ EVENTS ============

export type EventStatus = 'active' | 'attributed' | 'invoiced'

export type CostUnit = 'usd' | 'credits'

export interface UsageEvent {
  id: string
  timestamp: string
  eventName: string
  customerId: string
  customerName: string
  customerReferenceId: string
  properties: Record<string, unknown>
  status: EventStatus
  // New cost model fields
  featureKey?: string // matched feature (e.g., "api_requests", "pdf_generation")
  costAmount?: number // derived cost amount
  costUnit?: CostUnit // "usd" or "credits"
  invoiceId?: string
}

export interface EventTimeline {
  ingested: { timestamp: string; success: boolean }
  attributed: { timestamp: string; customerId: string; customerName: string } | null
  matchedFeature: {
    timestamp: string
    featureKey: string
    costAmount: number
    costUnit: CostUnit
  } | null
  invoiced: { timestamp: string; invoiceId: string } | null
}

// ============ FEATURES ============

export interface Feature {
  id: string
  name: string
  key: string
  description: string
  isEnabled: boolean
  createdAt: string
  modifiedAt: string
  plansCount: number
}

// ============ PLANS WITH VERSIONING ============

export type PriceModel =
  | 'per_unit'
  | 'graduated'
  | 'volume'
  | 'package'
  | 'matrix'
  | 'fixed'
  | 'percentage_plus_fixed'
export type PlanVersionStatus = 'draft' | 'published' | 'archived'
export type BillingCycle = 'monthly' | 'quarterly' | 'annual'

// Tier for graduated/volume pricing
export interface PricingTier {
  firstUnit: number
  lastUnit: number | null // null = unlimited
  unitPrice: number
  flatFee?: number // optional flat fee per tier
}

export interface MatrixPriceDimension {
  key: string
  values: Array<{ value: string; unitPrice: number }>
  defaultPrice: number
}

// 2D Matrix pricing (for AI models with context × I/O pricing)
export interface Matrix2DPricing {
  dimension1: {
    key: string // e.g., "context_window"
    values: string[] // e.g., ["8k", "32k", "128k"]
  }
  dimension2: {
    key: string // e.g., "io_direction"
    values: string[] // e.g., ["input", "output"]
  }
  prices: Record<string, Record<string, number>> // prices[dim1Value][dim2Value]
  defaultPrice: number
}

export interface PackageTier {
  upTo: number | 'unlimited'
  unitPrice: number
}

export interface UsageBasedFee {
  id: string
  component: string
  featureId: string
  featureName: string
  billingCycle: BillingCycle
  priceModel: PriceModel
  // For per_unit pricing:
  unitPrice?: number
  // For graduated/volume pricing:
  tiers?: PricingTier[]
  // For matrix pricing:
  matrixDimensions?: MatrixPriceDimension[]
  defaultUnitPrice?: number // fallback for matrix
  // For package pricing:
  packageSize?: number
  packagePrice?: number
  packageTiers?: PackageTier[] // legacy support
  // For percentage_plus_fixed pricing (fintech):
  percentageRate?: number // e.g., 0.029 for 2.9%
  fixedFeePerUnit?: number // e.g., 0.30 per transaction
  // For 2D matrix pricing (AI models):
  matrix2D?: Matrix2DPricing
}

export interface FixedFee {
  id: string
  component: string
  billingCycle: BillingCycle
  amount: number
  quantity: number
}

export interface PlanVersion {
  version: number
  isDefault: boolean
  status: PlanVersionStatus
  publishedAt?: string // only set when published
  usageBasedFees: UsageBasedFee[]
  fixedFees: FixedFee[]
}

export interface Plan {
  id: string
  name: string
  description?: string
  externalId: string
  status: 'active' | 'draft' | 'archived'
  currency: string
  billingCycle: BillingCycle
  netTerms: string
  billingMode: 'in_advance' | 'arrears'
  versions: PlanVersion[]
  subscriptionCount: number
  mrr: number
  createdAt: string
  activity: Array<{ date: string; action: string }>
}

export interface SubscriptionOverride {
  featureId: string
  featureName: string
  originalPrice: number
  overridePrice: number
  reason?: string
}

export interface SubscriptionActivity {
  date: string
  action: string
  details?: string
}

export interface FixedFeeQuantity {
  feeId: string
  component: string
  originalQuantity: number
  quantity: number
}

export interface PlanSubscription {
  id: string
  customerId: string
  customerName: string
  customerEmail?: string
  planId: string
  planName: string
  startDate: string
  endDate?: string
  version: number
  billingCycle: BillingCycle
  currentPeriodStart: string
  currentPeriodEnd: string
  nextInvoiceDate: string
  hasOverrides: boolean
  overrides?: SubscriptionOverride[]
  fixedFeeQuantities?: FixedFeeQuantity[]
  activity?: SubscriptionActivity[]
  status: 'active' | 'canceled' | 'paused'
}

// ============ INVOICES ============

export type InvoiceStatus =
  | 'draft'
  | 'pending_issue'
  | 'issued'
  | 'paid'
  | 'past_due'
  | 'payment_failed'
  | 'voided'

export interface InvoiceLineItem {
  id: string
  type: 'usage_based' | 'fixed'
  featureName?: string
  dimension?: string
  quantity: number
  rate: number
  amount: number
  description: string
}

export interface Invoice {
  id: string
  customerId: string
  customerName: string
  customerEmail: string
  planId: string
  planName: string
  status: InvoiceStatus
  amount: number
  subtotal: number
  tax: number
  invoiceDate: string
  dueDate: string
  billingPeriodStart: string
  billingPeriodEnd: string
  lineItems: InvoiceLineItem[]
  isFinalized: boolean
}

// ============ DEMO STATE ============

export type DemoTab = 'segments' | 'automations' | 'settings'
export type AutomationFilter = 'all' | 'alerts' | 'actions' | 'experiments'
