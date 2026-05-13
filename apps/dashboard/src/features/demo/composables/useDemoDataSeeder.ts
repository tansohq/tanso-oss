import { useQueryClient } from '@tanstack/vue-query'
import { useRoute } from 'vue-router'

// ── Shared IDs ──────────────────────────────────────────────────────────────
const PLAN_IDS = {
  starter: 'b1a2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d',
  growth: 'c2b3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e',
  enterprise: 'd3c4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f'
} as const

const CUSTOMER_IDS = {
  outbound: 'a1b2c3d4-e5f6-4789-abcd-ef0123456789',
  pipelineai: 'b2c3d4e5-f6a7-4890-bcde-f01234567890',
  prospectlab: 'c3d4e5f6-a7b8-4901-cdef-012345678901',
  dealflow: 'd4e5f6a7-b8c9-4012-def0-123456789012',
  salesforge: 'e5f6a7b8-c9d0-4123-ef01-234567890123'
} as const

const FEATURE_IDS = {
  apiAccess: 'f6a7b8c9-d0e1-4234-f012-345678901234',
  campaignAnalytics: 'a7b8c9d0-e1f2-4345-0123-456789012345',
  crmIntegrations: 'b8c9d0e1-f2a3-4456-1234-567890123456',
  emailSequences: 'c9d0e1f2-a3b4-4567-2345-678901234567',
  contactEnrichment: 'd0e1f2a3-b4c5-4678-3456-789012345678',
  webhookCallbacks: 'e1f2a3b4-c5d6-4789-4567-890123456789'
} as const

const SUBSCRIPTION_IDS = {
  outbound: '11111111-aaaa-4bbb-cccc-dddddddddddd',
  pipelineai: '22222222-bbbb-4ccc-dddd-eeeeeeeeeeee',
  prospectlab: '33333333-cccc-4ddd-eeee-ffffffffffff',
  dealflow: '44444444-dddd-4eee-ffff-aaaaaaaaaaaa',
  salesforge: '55555555-eeee-4fff-aaaa-bbbbbbbbbbbb'
} as const

// Invoice IDs are generated dynamically in buildInvoices()
const INVOICE_ID_PREFIX = '66666666'

const ACCOUNT_ID = 'bbbbbbbb-eeee-4fff-1111-222222222222'

const MONTH_AGO = '2026-02-14T12:00:00Z'
const TWO_MONTHS_AGO = '2026-01-14T12:00:00Z'
const THREE_MONTHS_AGO = '2025-12-14T12:00:00Z'
const PERIOD_START = '2026-03-01T00:00:00Z'
const PERIOD_END = '2026-03-31T23:59:59Z'

// ── Plan Data ───────────────────────────────────────────────────────────────
function buildPlans() {
  return [
    {
      id: PLAN_IDS.starter,
      key: 'starter',
      name: 'Starter',
      description: 'For early-stage sales teams getting started with outbound',
      priceAmount: 99,
      intervalMonths: '1',
      billingTiming: 'IN_ADVANCE',
      status: 'active' as const,
      createdAt: THREE_MONTHS_AGO,
      modifiedAt: MONTH_AGO,
      metadata: null
    },
    {
      id: PLAN_IDS.growth,
      key: 'growth',
      name: 'Growth',
      description: 'For scaling teams that need advanced analytics and integrations',
      priceAmount: 199,
      intervalMonths: '1',
      billingTiming: 'IN_ADVANCE',
      status: 'active' as const,
      createdAt: THREE_MONTHS_AGO,
      modifiedAt: MONTH_AGO,
      metadata: null
    },
    {
      id: PLAN_IDS.enterprise,
      key: 'enterprise',
      name: 'Enterprise',
      description: 'For large sales organizations with custom requirements',
      priceAmount: 699,
      intervalMonths: '1',
      billingTiming: 'IN_ADVANCE',
      status: 'active' as const,
      createdAt: THREE_MONTHS_AGO,
      modifiedAt: MONTH_AGO,
      metadata: null
    }
  ]
}

// ── Customer Data ───────────────────────────────────────────────────────────
function buildCustomerElements() {
  return [
    {
      id: CUSTOMER_IDS.outbound,
      referenceId: 'outbound-io',
      firstName: 'Sarah',
      lastName: 'Chen',
      email: 'sarah@outbound.io'
    },
    {
      id: CUSTOMER_IDS.pipelineai,
      referenceId: 'pipeline-ai',
      firstName: 'Marcus',
      lastName: 'Rodriguez',
      email: 'marcus@pipelineai.com'
    },
    {
      id: CUSTOMER_IDS.prospectlab,
      referenceId: 'prospect-lab',
      firstName: 'Emily',
      lastName: 'Park',
      email: 'emily@prospectlab.io'
    },
    {
      id: CUSTOMER_IDS.dealflow,
      referenceId: 'deal-flow',
      firstName: 'James',
      lastName: 'Mitchell',
      email: 'james@dealflow.co'
    },
    {
      id: CUSTOMER_IDS.salesforge,
      referenceId: 'sales-forge',
      firstName: 'Anika',
      lastName: 'Patel',
      email: 'anika@salesforge.dev'
    }
  ]
}

function buildCustomerDetails() {
  const elements = buildCustomerElements()
  return elements.map((c) => ({
    id: c.id,
    customerReferenceId: c.referenceId,
    firstName: c.firstName,
    lastName: c.lastName,
    email: c.email,
    phoneNumber: null,
    createdAt: THREE_MONTHS_AGO,
    modifiedAt: MONTH_AGO
  }))
}

// ── Feature Data ────────────────────────────────────────────────────────────
function buildFeatures() {
  return [
    {
      id: FEATURE_IDS.apiAccess,
      name: 'API Access',
      key: 'api_access',
      description: 'REST API access for programmatic outbound workflows',
      createdAt: THREE_MONTHS_AGO,
      modifiedAt: MONTH_AGO,
      isEnabled: true,
      metadata: null
    },
    {
      id: FEATURE_IDS.campaignAnalytics,
      name: 'Campaign Analytics',
      key: 'campaign_analytics',
      description: 'Real-time analytics for outbound campaign performance',
      createdAt: THREE_MONTHS_AGO,
      modifiedAt: MONTH_AGO,
      isEnabled: true,
      metadata: null
    },
    {
      id: FEATURE_IDS.crmIntegrations,
      name: 'CRM Integrations',
      key: 'crm_integrations',
      description: 'Native integrations with Salesforce, HubSpot, and Pipedrive',
      createdAt: THREE_MONTHS_AGO,
      modifiedAt: MONTH_AGO,
      isEnabled: true,
      metadata: null
    },
    {
      id: FEATURE_IDS.emailSequences,
      name: 'Email Sequences',
      key: 'email_sequences',
      description: 'Automated multi-step email sequences with personalization',
      createdAt: THREE_MONTHS_AGO,
      modifiedAt: TWO_MONTHS_AGO,
      isEnabled: true,
      metadata: null
    },
    {
      id: FEATURE_IDS.contactEnrichment,
      name: 'Contact Enrichment',
      key: 'contact_enrichment',
      description: 'Enrich prospect data with firmographic and technographic signals',
      createdAt: TWO_MONTHS_AGO,
      modifiedAt: MONTH_AGO,
      isEnabled: true,
      metadata: null
    },
    {
      id: FEATURE_IDS.webhookCallbacks,
      name: 'Webhook Callbacks',
      key: 'webhook_callbacks',
      description: 'Real-time webhook notifications for engagement events',
      createdAt: TWO_MONTHS_AGO,
      modifiedAt: MONTH_AGO,
      isEnabled: false,
      metadata: null
    }
  ]
}

// ── Subscription Helper ─────────────────────────────────────────────────────
function buildSubscriptionCustomer(
  id: string,
  referenceId: string,
  firstName: string,
  lastName: string,
  email: string
) {
  return {
    id,
    referenceId,
    customerReferenceId: referenceId,
    firstName,
    lastName,
    email,
    phoneNumber: null,
    createdAt: THREE_MONTHS_AGO,
    modifiedAt: MONTH_AGO
  }
}

function buildSubscriptionPlan(
  id: string,
  key: string,
  name: string,
  description: string,
  priceAmount: number
) {
  return {
    id,
    key,
    name,
    description,
    priceAmount,
    intervalMonths: '1',
    billingTiming: 'IN_ADVANCE',
    createdAt: THREE_MONTHS_AGO,
    modifiedAt: MONTH_AGO
  }
}

// ── Subscription Data ───────────────────────────────────────────────────────
function buildSubscriptions() {
  const starterPlan = buildSubscriptionPlan(
    PLAN_IDS.starter,
    'starter',
    'Starter',
    'For early-stage sales teams getting started with outbound',
    99
  )
  const growthPlan = buildSubscriptionPlan(
    PLAN_IDS.growth,
    'growth',
    'Growth',
    'For scaling teams that need advanced analytics and integrations',
    199
  )
  const enterprisePlan = buildSubscriptionPlan(
    PLAN_IDS.enterprise,
    'enterprise',
    'Enterprise',
    'For large sales organizations with custom requirements',
    699
  )

  return [
    {
      id: SUBSCRIPTION_IDS.outbound,
      isActive: true,
      intervalMonths: '1',
      customer: buildSubscriptionCustomer(
        CUSTOMER_IDS.outbound,
        'outbound-io',
        'Sarah',
        'Chen',
        'sarah@outbound.io'
      ),
      plan: growthPlan,
      gracePeriodDays: 3,
      currentPeriodStart: PERIOD_START,
      currentPeriodEnd: PERIOD_END,
      cancelMode: null,
      cancelEffectiveAt: null,
      cancelledAt: null,
      billingAnchorDay: 1,
      metadata: null,
      scheduledChange: null
    },
    {
      id: SUBSCRIPTION_IDS.pipelineai,
      isActive: true,
      intervalMonths: '1',
      customer: buildSubscriptionCustomer(
        CUSTOMER_IDS.pipelineai,
        'pipeline-ai',
        'Marcus',
        'Rodriguez',
        'marcus@pipelineai.com'
      ),
      plan: enterprisePlan,
      gracePeriodDays: 5,
      currentPeriodStart: PERIOD_START,
      currentPeriodEnd: PERIOD_END,
      cancelMode: null,
      cancelEffectiveAt: null,
      cancelledAt: null,
      billingAnchorDay: 1,
      metadata: null,
      scheduledChange: null
    },
    {
      id: SUBSCRIPTION_IDS.prospectlab,
      isActive: true,
      intervalMonths: '1',
      customer: buildSubscriptionCustomer(
        CUSTOMER_IDS.prospectlab,
        'prospect-lab',
        'Emily',
        'Park',
        'emily@prospectlab.io'
      ),
      plan: starterPlan,
      gracePeriodDays: 3,
      currentPeriodStart: PERIOD_START,
      currentPeriodEnd: PERIOD_END,
      cancelMode: null,
      cancelEffectiveAt: null,
      cancelledAt: null,
      billingAnchorDay: 1,
      metadata: null,
      scheduledChange: null
    },
    {
      id: SUBSCRIPTION_IDS.dealflow,
      isActive: true,
      intervalMonths: '1',
      customer: buildSubscriptionCustomer(
        CUSTOMER_IDS.dealflow,
        'deal-flow',
        'James',
        'Mitchell',
        'james@dealflow.co'
      ),
      plan: growthPlan,
      gracePeriodDays: 3,
      currentPeriodStart: PERIOD_START,
      currentPeriodEnd: PERIOD_END,
      cancelMode: null,
      cancelEffectiveAt: null,
      cancelledAt: null,
      billingAnchorDay: 1,
      metadata: null,
      scheduledChange: null
    },
    {
      id: SUBSCRIPTION_IDS.salesforge,
      isActive: true,
      intervalMonths: '1',
      customer: buildSubscriptionCustomer(
        CUSTOMER_IDS.salesforge,
        'sales-forge',
        'Anika',
        'Patel',
        'anika@salesforge.dev'
      ),
      plan: enterprisePlan,
      gracePeriodDays: 5,
      currentPeriodStart: PERIOD_START,
      currentPeriodEnd: PERIOD_END,
      cancelMode: null,
      cancelEffectiveAt: null,
      cancelledAt: null,
      billingAnchorDay: 1,
      metadata: null,
      scheduledChange: null
    }
  ]
}

// ── Invoice Data ────────────────────────────────────────────────────────────
function buildInvoices() {
  const subscriptions = buildSubscriptions()
  const invoices: Array<{
    id: string; amount: number; dueDate: string; currency: string;
    subscription: ReturnType<typeof buildSubscriptions>[0];
    status: string; metadata: null; createdAt: string; modifiedAt: string
  }> = []

  // Generate 12 months of invoices — all 3 plan types present early, each grows
  // sub[0] Outbound/Growth ($199):       month 11 — founding customer
  // sub[1] PipelineAI/Enterprise ($699): month 9  — first enterprise deal
  // sub[2] ProspectLab/Starter ($99):    month 7  — small team signs up
  // sub[3] DealFlow/Growth ($199):       month 3  — second growth customer
  // sub[4] SalesForge/Enterprise ($699): month 1  — second enterprise deal
  // All 3 plan types visible from early on, each segment grows via usage
  const customerJoinMonth = [11, 9, 7, 3, 1]

  for (let monthsAgo = 11; monthsAgo >= 0; monthsAgo--) {
    const d = new Date(2026, 2 - monthsAgo, 1) // March = month index 2
    const y = d.getFullYear()
    const mo = String(d.getMonth() + 1).padStart(2, '0')
    const periodStart = `${y}-${mo}-05T12:00:00Z`
    const dueDate = `${y}-${mo}-15T12:00:00Z`

    const isCurrentMonth = monthsAgo === 0

    for (let si = 0; si < subscriptions.length; si++) {
      // Skip if this customer hasn't joined yet
      if (monthsAgo > customerJoinMonth[si]) continue

      const sub = subscriptions[si]
      const idx = invoices.length
      const id = `${INVOICE_ID_PREFIX}-${String(idx).padStart(4, '0')}-4aaa-bbbb-cccccccccccc`
      const status = isCurrentMonth
        ? (si < 2 ? 'PAID' : 'PENDING')
        : 'PAID'

      // Compound growth — each customer's revenue grows exponentially
      const monthsActive = customerJoinMonth[si] - monthsAgo
      const growthRate = [0.12, 0.14, 0.15, 0.12, 0.14][si]
      const amount = Math.round(sub.plan.priceAmount * Math.pow(1 + growthRate, monthsActive))

      invoices.push({
        id,
        amount,
        dueDate,
        currency: 'USD',
        subscription: sub,
        status,
        metadata: null,
        createdAt: periodStart,
        modifiedAt: status === 'PAID' ? `${y}-${mo}-10T12:00:00Z` : periodStart
      })
    }
  }

  return invoices
}

// ── Event Data ──────────────────────────────────────────────────────────────
function buildEvents() {
  const eventDefs = [
    { eventName: 'chat_completion', featureKey: 'api_access', featureId: FEATURE_IDS.apiAccess, model: 'gpt-4o', modelProvider: 'OpenAI', cost: 0.0282, revenue: 0.0845, usage: 1500 },
    { eventName: 'chat_completion', featureKey: 'api_access', featureId: FEATURE_IDS.apiAccess, model: 'gpt-4o-mini', modelProvider: 'OpenAI', cost: 0.0042, revenue: 0.0126, usage: 800 },
    { eventName: 'email_generated', featureKey: 'email_sequences', featureId: FEATURE_IDS.emailSequences, model: 'claude-3-5-sonnet', modelProvider: 'Anthropic', cost: 0.0450, revenue: 0.1200, usage: 2000 },
    { eventName: 'email_generated', featureKey: 'email_sequences', featureId: FEATURE_IDS.emailSequences, model: 'gpt-4o-mini', modelProvider: 'OpenAI', cost: 0.0060, revenue: 0.0180, usage: 600 },
    { eventName: 'contact_enriched', featureKey: 'contact_enrichment', featureId: FEATURE_IDS.contactEnrichment, model: 'claude-3-5-sonnet', modelProvider: 'Anthropic', cost: 0.0380, revenue: 0.1500, usage: 1200 },
    { eventName: 'campaign_analyzed', featureKey: 'campaign_analytics', featureId: FEATURE_IDS.campaignAnalytics, model: 'gpt-4o', modelProvider: 'OpenAI', cost: 0.0150, revenue: 0.0500, usage: 800 },
    { eventName: 'crm_synced', featureKey: 'crm_integrations', featureId: FEATURE_IDS.crmIntegrations, model: 'gpt-4o-mini', modelProvider: 'OpenAI', cost: 0.0035, revenue: 0.0120, usage: 500 },
    { eventName: 'embedding_generated', featureKey: 'api_access', featureId: FEATURE_IDS.apiAccess, model: 'embed-english-v3.0', modelProvider: 'Cohere', cost: 0.0010, revenue: 0.0050, usage: 5000 },
  ]

  const customerIds = Object.values(CUSTOMER_IDS)
  const customerRefIds = ['cus_outbound', 'cus_pipelineai', 'cus_prospectlab', 'cus_dealflow', 'cus_salesforge']

  const totalEvents = 1200
  const threeMonthsMs = 90 * 24 * 3600000
  const subscriptionIds = Object.values(SUBSCRIPTION_IDS)

  return Array.from({ length: totalEvents }, (_, i) => {
    const dayOffset = Math.floor((i / totalEvents) * 90)
    const hourJitter = Math.floor((i * 7 + i * i) % 24)
    const minuteJitter = (i * 13) % 60
    const timestamp = new Date(
      Date.now() - threeMonthsMs + dayOffset * 24 * 3600000 + hourJitter * 3600000 + minuteJitter * 60000
    ).toISOString()

    const def = eventDefs[i % eventDefs.length]

    return {
      id: `eeeeeeee-${String(i).padStart(4, '0')}-4aaa-bbbb-cccccccccccc`,
      accountId: ACCOUNT_ID,
      eventIdempotencyKey: `idem-${Date.now()}-${i}`,
      flowId: null,
      eventName: def.eventName,
      occurredAt: timestamp,
      customerId: customerIds[i % customerIds.length],
      customerReferenceId: customerRefIds[i % customerRefIds.length],
      featureId: def.featureId,
      featureKey: def.featureKey,
      subscriptionId: subscriptionIds[i % 5],
      entitlementId: null,
      invoiceId: null,
      model: def.model,
      modelProvider: def.modelProvider,
      revenueAmount: def.revenue,
      revenueUnit: 'USD',
      costAmount: def.cost,
      costUnit: 'USD',
      usageUnits: def.usage,
      usageUnitType: def.featureKey,
      properties: { isEntitled: true },
      meta: {
        inputTokens: Math.floor(def.usage * 0.6),
        outputTokens: Math.floor(def.usage * 0.4),
      },
      context: { sys_cost_source: 'account_default', sys_model: def.model, sys_model_provider: def.modelProvider },
      ingestError: null,
      createdAt: timestamp,
      modifiedAt: timestamp,
      eventType: 'CLIENT_TRACKED'
    }
  }).sort((a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime())
}

// ── Analytics Data ──────────────────────────────────────────────────────────
function buildAnalyticsPortfolio() {
  return {
    summary: {
      totalMrr: 1895,
      totalCosts: 423,
      avgMargin: 0.777,
      customersByStatus: {
        healthy: 4,
        atRisk: 1,
        underwater: 0
      },
      mrrByStatus: {
        healthy: 1796,
        atRisk: 99,
        underwater: 0
      },
      ltv: 2840,
      nrr: 108.5,
      totalEffectiveMrr: 1852,
      totalRgp: 1472,
      rgpMargin: 0.777,
      pendingCancelCount: 0,
      pendingDowngradeCount: 0,
      criticalChurnCount: 0,
      pendingCancelMrr: 0,
      highRiskMrr: 99,
      avgChurnScore: 12.3,
      revenueBurnDown: {
        next30Days: 1895,
        next60Days: 3790,
        next90Days: 5685
      },
      creditImpact: {
        totalCredits: 0,
        creditInvoiceCount: 0,
        netEffectiveMrr: 1895,
        creditToMrrRatio: 0
      },
      topModelsByCost: [
        { model: 'gpt-4o', modelProvider: 'OpenAI', cost: 195, revenue: 520, usageUnits: 42000 },
        { model: 'claude-3-5-sonnet', modelProvider: 'Anthropic', cost: 108, revenue: 290, usageUnits: 18500 },
        { model: 'gpt-4o-mini', modelProvider: 'OpenAI', cost: 90, revenue: 238, usageUnits: 28000 },
        { model: 'embed-english-v3.0', modelProvider: 'Cohere', cost: 30, revenue: 80, usageUnits: 156000 }
      ]
    },
    customers: [
      {
        customerId: CUSTOMER_IDS.outbound,
        customerName: 'Sarah Chen',
        email: 'sarah@outbound.io',
        customerReferenceId: 'cus_outbound',
        planId: PLAN_IDS.growth,
        planName: 'Growth',
        mrr: 199,
        effectiveMrr: 199,
        projectedUsageRevenue: 24,
        totalCost: 52,
        margin: 0.739,
        marginStatus: 'healthy' as const,
        churnRisk: 'low',
        churnScoreDetails: {
          score: 8,
          riskLabel: 'Low',
          daysSinceLastUsage: 1,
          supportTicketCount: 0,
          npsScore: 9
        },
        featureProfitability: [
          { featureId: FEATURE_IDS.apiAccess, featureName: 'API Access', featureKey: 'api_access', revenue: 45, cost: 12, margin: 0.733, usageUnits: 8500, modelBreakdown: [
            { model: 'gpt-4o', modelProvider: 'OpenAI', cost: 8, revenue: 30, usageUnits: 5500 },
            { model: 'gpt-4o-mini', modelProvider: 'OpenAI', cost: 4, revenue: 15, usageUnits: 3000 }
          ] },
          { featureId: FEATURE_IDS.emailSequences, featureName: 'Email Sequences', featureKey: 'email_sequences', revenue: 82, cost: 18, margin: 0.780, usageUnits: 3200, modelBreakdown: [
            { model: 'claude-3-5-sonnet', modelProvider: 'Anthropic', cost: 12, revenue: 55, usageUnits: 2100 },
            { model: 'gpt-4o-mini', modelProvider: 'OpenAI', cost: 6, revenue: 27, usageUnits: 1100 }
          ] },
          { featureId: FEATURE_IDS.campaignAnalytics, featureName: 'Campaign Analytics', featureKey: 'campaign_analytics', revenue: 38, cost: 10, margin: 0.737, usageUnits: 1400, modelBreakdown: [
            { model: 'gpt-4o', modelProvider: 'OpenAI', cost: 10, revenue: 38, usageUnits: 1400 }
          ] },
          { featureId: FEATURE_IDS.crmIntegrations, featureName: 'CRM Integrations', featureKey: 'crm_integrations', revenue: 34, cost: 12, margin: 0.647, usageUnits: 900, modelBreakdown: [
            { model: 'gpt-4o-mini', modelProvider: 'OpenAI', cost: 12, revenue: 34, usageUnits: 900 }
          ] }
        ]
      },
      {
        customerId: CUSTOMER_IDS.pipelineai,
        customerName: 'Marcus Rodriguez',
        email: 'marcus@pipelineai.com',
        customerReferenceId: 'cus_pipelineai',
        planId: PLAN_IDS.enterprise,
        planName: 'Enterprise',
        mrr: 699,
        effectiveMrr: 699,
        projectedUsageRevenue: 85,
        totalCost: 154,
        margin: 0.780,
        marginStatus: 'healthy' as const,
        churnRisk: 'low',
        churnScoreDetails: {
          score: 5,
          riskLabel: 'Low',
          daysSinceLastUsage: 0,
          supportTicketCount: 1,
          npsScore: 10
        },
        featureProfitability: [
          { featureId: FEATURE_IDS.apiAccess, featureName: 'API Access', featureKey: 'api_access', revenue: 180, cost: 42, margin: 0.767, usageUnits: 34000, modelBreakdown: [
            { model: 'gpt-4o', modelProvider: 'OpenAI', cost: 28, revenue: 120, usageUnits: 22000 },
            { model: 'gpt-4o-mini', modelProvider: 'OpenAI', cost: 14, revenue: 60, usageUnits: 12000 }
          ] },
          { featureId: FEATURE_IDS.emailSequences, featureName: 'Email Sequences', featureKey: 'email_sequences', revenue: 245, cost: 48, margin: 0.804, usageUnits: 12800, modelBreakdown: [
            { model: 'claude-3-5-sonnet', modelProvider: 'Anthropic', cost: 32, revenue: 165, usageUnits: 8400 },
            { model: 'gpt-4o-mini', modelProvider: 'OpenAI', cost: 16, revenue: 80, usageUnits: 4400 }
          ] },
          { featureId: FEATURE_IDS.contactEnrichment, featureName: 'Contact Enrichment', featureKey: 'contact_enrichment', revenue: 156, cost: 38, margin: 0.756, usageUnits: 6200, modelBreakdown: [
            { model: 'claude-3-5-sonnet', modelProvider: 'Anthropic', cost: 38, revenue: 156, usageUnits: 6200 }
          ] },
          { featureId: FEATURE_IDS.campaignAnalytics, featureName: 'Campaign Analytics', featureKey: 'campaign_analytics', revenue: 72, cost: 14, margin: 0.806, usageUnits: 4100, modelBreakdown: [
            { model: 'gpt-4o', modelProvider: 'OpenAI', cost: 14, revenue: 72, usageUnits: 4100 }
          ] },
          { featureId: FEATURE_IDS.crmIntegrations, featureName: 'CRM Integrations', featureKey: 'crm_integrations', revenue: 46, cost: 12, margin: 0.739, usageUnits: 1800, modelBreakdown: [
            { model: 'gpt-4o-mini', modelProvider: 'OpenAI', cost: 12, revenue: 46, usageUnits: 1800 }
          ] }
        ]
      },
      {
        customerId: CUSTOMER_IDS.prospectlab,
        customerName: 'Emily Park',
        email: 'emily@prospectlab.io',
        customerReferenceId: 'cus_prospectlab',
        planId: PLAN_IDS.starter,
        planName: 'Starter',
        mrr: 99,
        effectiveMrr: 99,
        projectedUsageRevenue: 2,
        totalCost: 31,
        margin: 0.687,
        marginStatus: 'at_risk' as const,
        churnRisk: 'medium',
        churnScoreDetails: {
          score: 42,
          riskLabel: 'Medium',
          daysSinceLastUsage: 8,
          supportTicketCount: 3,
          npsScore: 6
        },
        featureProfitability: [
          { featureId: FEATURE_IDS.apiAccess, featureName: 'API Access', featureKey: 'api_access', revenue: 18, cost: 8, margin: 0.556, usageUnits: 2100, modelBreakdown: [
            { model: 'gpt-4o', modelProvider: 'OpenAI', cost: 5, revenue: 12, usageUnits: 1400 },
            { model: 'gpt-4o-mini', modelProvider: 'OpenAI', cost: 3, revenue: 6, usageUnits: 700 }
          ] },
          { featureId: FEATURE_IDS.emailSequences, featureName: 'Email Sequences', featureKey: 'email_sequences', revenue: 32, cost: 14, margin: 0.563, usageUnits: 900, modelBreakdown: [
            { model: 'claude-3-5-sonnet', modelProvider: 'Anthropic', cost: 9, revenue: 22, usageUnits: 600 },
            { model: 'gpt-4o-mini', modelProvider: 'OpenAI', cost: 5, revenue: 10, usageUnits: 300 }
          ] }
        ]
      },
      {
        customerId: CUSTOMER_IDS.dealflow,
        customerName: 'James Mitchell',
        email: 'james@dealflow.co',
        customerReferenceId: 'cus_dealflow',
        planId: PLAN_IDS.growth,
        planName: 'Growth',
        mrr: 199,
        effectiveMrr: 199,
        projectedUsageRevenue: 31,
        totalCost: 48,
        margin: 0.759,
        marginStatus: 'healthy' as const,
        churnRisk: 'low',
        churnScoreDetails: {
          score: 11,
          riskLabel: 'Low',
          daysSinceLastUsage: 2,
          supportTicketCount: 0,
          npsScore: 8
        },
        featureProfitability: [
          { featureId: FEATURE_IDS.apiAccess, featureName: 'API Access', featureKey: 'api_access', revenue: 52, cost: 14, margin: 0.731, usageUnits: 9800, modelBreakdown: [
            { model: 'gpt-4o', modelProvider: 'OpenAI', cost: 9, revenue: 35, usageUnits: 6500 },
            { model: 'gpt-4o-mini', modelProvider: 'OpenAI', cost: 5, revenue: 17, usageUnits: 3300 }
          ] },
          { featureId: FEATURE_IDS.emailSequences, featureName: 'Email Sequences', featureKey: 'email_sequences', revenue: 78, cost: 16, margin: 0.795, usageUnits: 4100, modelBreakdown: [
            { model: 'claude-3-5-sonnet', modelProvider: 'Anthropic', cost: 10, revenue: 52, usageUnits: 2700 },
            { model: 'gpt-4o-mini', modelProvider: 'OpenAI', cost: 6, revenue: 26, usageUnits: 1400 }
          ] },
          { featureId: FEATURE_IDS.campaignAnalytics, featureName: 'Campaign Analytics', featureKey: 'campaign_analytics', revenue: 42, cost: 10, margin: 0.762, usageUnits: 2200, modelBreakdown: [
            { model: 'gpt-4o', modelProvider: 'OpenAI', cost: 10, revenue: 42, usageUnits: 2200 }
          ] },
          { featureId: FEATURE_IDS.crmIntegrations, featureName: 'CRM Integrations', featureKey: 'crm_integrations', revenue: 27, cost: 8, margin: 0.704, usageUnits: 1100, modelBreakdown: [
            { model: 'gpt-4o-mini', modelProvider: 'OpenAI', cost: 8, revenue: 27, usageUnits: 1100 }
          ] }
        ]
      },
      {
        customerId: CUSTOMER_IDS.salesforge,
        customerName: 'Anika Patel',
        email: 'anika@salesforge.dev',
        customerReferenceId: 'cus_salesforge',
        planId: PLAN_IDS.enterprise,
        planName: 'Enterprise',
        mrr: 699,
        effectiveMrr: 699,
        projectedUsageRevenue: 120,
        totalCost: 138,
        margin: 0.803,
        marginStatus: 'healthy' as const,
        churnRisk: 'low',
        churnScoreDetails: {
          score: 3,
          riskLabel: 'Low',
          daysSinceLastUsage: 0,
          supportTicketCount: 0,
          npsScore: 10
        },
        featureProfitability: [
          { featureId: FEATURE_IDS.apiAccess, featureName: 'API Access', featureKey: 'api_access', revenue: 195, cost: 38, margin: 0.805, usageUnits: 42000, modelBreakdown: [
            { model: 'gpt-4o', modelProvider: 'OpenAI', cost: 18, revenue: 105, usageUnits: 22000 },
            { model: 'gpt-4o-mini', modelProvider: 'OpenAI', cost: 8, revenue: 50, usageUnits: 12000 },
            { model: 'embed-english-v3.0', modelProvider: 'Cohere', cost: 12, revenue: 40, usageUnits: 8000 }
          ] },
          { featureId: FEATURE_IDS.emailSequences, featureName: 'Email Sequences', featureKey: 'email_sequences', revenue: 280, cost: 52, margin: 0.814, usageUnits: 15600, modelBreakdown: [
            { model: 'claude-3-5-sonnet', modelProvider: 'Anthropic', cost: 34, revenue: 188, usageUnits: 10400 },
            { model: 'gpt-4o-mini', modelProvider: 'OpenAI', cost: 18, revenue: 92, usageUnits: 5200 }
          ] },
          { featureId: FEATURE_IDS.contactEnrichment, featureName: 'Contact Enrichment', featureKey: 'contact_enrichment', revenue: 124, cost: 28, margin: 0.774, usageUnits: 8400, modelBreakdown: [
            { model: 'claude-3-5-sonnet', modelProvider: 'Anthropic', cost: 28, revenue: 124, usageUnits: 8400 }
          ] },
          { featureId: FEATURE_IDS.campaignAnalytics, featureName: 'Campaign Analytics', featureKey: 'campaign_analytics', revenue: 58, cost: 10, margin: 0.828, usageUnits: 3800, modelBreakdown: [
            { model: 'gpt-4o', modelProvider: 'OpenAI', cost: 10, revenue: 58, usageUnits: 3800 }
          ] },
          { featureId: FEATURE_IDS.crmIntegrations, featureName: 'CRM Integrations', featureKey: 'crm_integrations', revenue: 32, cost: 6, margin: 0.813, usageUnits: 2100, modelBreakdown: [
            { model: 'gpt-4o-mini', modelProvider: 'OpenAI', cost: 6, revenue: 32, usageUnits: 2100 }
          ] },
          { featureId: FEATURE_IDS.webhookCallbacks, featureName: 'Webhook Callbacks', featureKey: 'webhook_callbacks', revenue: 10, cost: 4, margin: 0.600, usageUnits: 1200, modelBreakdown: [
            { model: 'gpt-4o-mini', modelProvider: 'OpenAI', cost: 4, revenue: 10, usageUnits: 1200 }
          ] }
        ]
      }
    ]
  }
}

// ── Seeder Composable ───────────────────────────────────────────────────────
export function useDemoDataSeeder() {
  const queryClient = useQueryClient()
  const route = useRoute()
  const isObserveDemo = route.query.mode === 'observe'

  // Prevent queries from refetching (demo data is static) — set FIRST before any data
  queryClient.setDefaultOptions({
    queries: {
      staleTime: Infinity,
      retry: false,
      refetchOnWindowFocus: false,
      refetchOnMount: false,
      refetchOnReconnect: false
    }
  })

  // Plans
  queryClient.setQueryData(['plans'], {
    data: buildPlans(),
    success: true
  })

  // Individual plan details
  const plans = buildPlans()
  for (const plan of plans) {
    queryClient.setQueryData(['plan', plan.id], {
      data: plan,
      success: true
    })
  }

  // Customers
  queryClient.setQueryData(['customers'], {
    data: { customers: buildCustomerElements() },
    success: true
  })

  // Individual customer details
  const customerDetails = buildCustomerDetails()
  for (const customer of customerDetails) {
    queryClient.setQueryData(['customer', customer.id], {
      data: customer,
      success: true
    })
  }

  // Features (default params = {})
  queryClient.setQueryData(['features', {}], {
    data: buildFeatures(),
    success: true
  })

  // Individual feature details
  const features = buildFeatures()
  for (const feature of features) {
    queryClient.setQueryData(['feature', feature.id], {
      data: feature,
      success: true
    })
  }

  // Subscriptions (default params = {})
  const subscriptions = buildSubscriptions()
  queryClient.setQueryData(['subscriptions', {}], {
    data: subscriptions,
    success: true
  })

  // Individual subscription details
  for (const sub of subscriptions) {
    queryClient.setQueryData(['subscription', sub.id], {
      data: sub,
      success: true
    })
  }

  // Customer subscriptions
  for (const sub of subscriptions) {
    queryClient.setQueryData(['customer-subscriptions', sub.customer.id], {
      data: [sub],
      success: true
    })
  }

  // Plan features (linked features per plan)
  const planFeatureLinks = [
    {
      plan: plans[0], // Starter
      features: [features[0], features[3]] // API Access, Email Sequences
    },
    {
      plan: plans[1], // Growth
      features: [features[0], features[1], features[2], features[3]] // API, Analytics, CRM, Sequences
    },
    {
      plan: plans[2], // Enterprise
      features: [features[0], features[1], features[4]] // API Access, Campaign Analytics, Contact Enrichment
    }
  ]
  queryClient.setQueryData(['plans', 'features'], {
    data: planFeatureLinks,
    success: true
  })
  for (const link of planFeatureLinks) {
    queryClient.setQueryData(['plan-features', link.plan.id], {
      data: link,
      success: true
    })
  }

  // Invoices
  queryClient.setQueryData(['invoices'], {
    data: buildInvoices(),
    success: true
  })

  // Individual invoice details
  const invoices = buildInvoices()
  for (const invoice of invoices) {
    queryClient.setQueryData(['invoice', invoice.id], {
      data: { ...invoice, items: [] },
      success: true
    })
  }

  // Events — use queryDefaults so ANY events query (regardless of date filters) returns mock data
  const events = buildEvents()
  queryClient.setQueryDefaults(['events'], {
    queryFn: ({ queryKey }: { queryKey: readonly unknown[] }) => {
      const params = (queryKey[1] as Record<string, unknown>) ?? {}
      const size = (params.size as number) ?? 20
      const page = (params.page as number) ?? 0
      // For chart queries (size=500), return all events; for table queries, paginate
      const sliceStart = page * size
      const sliceEnd = sliceStart + size
      const pageEvents = events.slice(sliceStart, sliceEnd)
      return {
        data: {
          items: pageEvents,
          totalElements: events.length,
          totalPages: Math.ceil(events.length / size),
          page,
          size
        },
        success: true
      }
    },
    staleTime: Infinity,
    retry: false,
    refetchOnWindowFocus: false,
    refetchOnMount: false,
    refetchOnReconnect: false
  })

  // Analytics portfolio
  queryClient.setQueryData(['analytics', 'portfolio'], {
    data: buildAnalyticsPortfolio(),
    success: true
  })

  // Models analytics
  queryClient.setQueryData(['analytics', 'models'], {
    data: {
      totalModels: 4,
      totalCost: 1128,
      totalRevenue: 3320,
      totalEvents: 1200,
      providerBreakdown: [
        { provider: 'OpenAI', cost: 285, percentage: 0.6738 },
        { provider: 'Anthropic', cost: 108, percentage: 0.2553 },
        { provider: 'Cohere', cost: 30, percentage: 0.0709 }
      ],
      models: [
        {
          model: 'gpt-4o',
          modelProvider: 'OpenAI',
          eventCount: 480,
          customerCount: 5,
          featureCount: 4,
          totalCost: 520,
          totalRevenue: 1650,
          totalUsage: 112000,
          avgCostPerEvent: 1.083,
          margin: 0.685,
          lastSeen: new Date().toISOString()
        },
        {
          model: 'gpt-4o-mini',
          modelProvider: 'OpenAI',
          eventCount: 340,
          customerCount: 4,
          featureCount: 3,
          totalCost: 238,
          totalRevenue: 825,
          totalUsage: 78000,
          avgCostPerEvent: 0.70,
          margin: 0.712,
          lastSeen: new Date().toISOString()
        },
        {
          model: 'claude-3-5-sonnet',
          modelProvider: 'Anthropic',
          eventCount: 260,
          customerCount: 3,
          featureCount: 3,
          totalCost: 290,
          totalRevenue: 655,
          totalUsage: 49000,
          avgCostPerEvent: 1.115,
          margin: 0.557,
          lastSeen: new Date().toISOString()
        },
        {
          model: 'embed-english-v3.0',
          modelProvider: 'Cohere',
          eventCount: 120,
          customerCount: 2,
          featureCount: 1,
          totalCost: 80,
          totalRevenue: 190,
          totalUsage: 420000,
          avgCostPerEvent: 0.667,
          margin: 0.579,
          lastSeen: new Date().toISOString()
        }
      ]
    },
    success: true
  })

  // AI Insights (hardcoded for demo — themed around AI SDR company)
  queryClient.setQueryData(['analytics', 'insights'], {
    data: [
      {
        id: 'insight-1',
        severity: 'CRITICAL',
        title: 'claude-3-5-sonnet costs 59% more per event than gpt-4o with similar output quality',
        description: 'Contact Enrichment and Email Sequences both route to claude-3-5-sonnet at $1.12/event. The same tasks on gpt-4o-mini cost $0.70/event. Switching these two features alone would save $142/month — a 12.6% reduction in total AI spend.',
        category: 'model_comparison',
        featureKey: 'contact_enrichment',
        customerId: null,
        createdAt: new Date().toISOString()
      },
      {
        id: 'insight-2',
        severity: 'WARNING',
        title: 'ProspectLab (Starter) has a 31% cost ratio — 2x your portfolio average',
        description: 'Emily Park generates $31 in AI costs against $99 MRR. She accounts for 3% of revenue but 7% of costs. Her API Access pattern shows single-record calls instead of batching — 2,100 calls for what other customers achieve in 400. A batching guide or rate limit could cut her cost ratio in half.',
        category: 'customer_profitability',
        featureKey: 'api_access',
        customerId: CUSTOMER_IDS.prospectlab,
        createdAt: new Date().toISOString()
      },
      {
        id: 'insight-3',
        severity: 'POSITIVE',
        title: 'Email Sequences delivers 80% margin at scale — your strongest feature',
        description: 'Across 1,200 events, Email Sequences generates $717 revenue on $148 cost (80% margin). It is used by all 5 customers and scales well — cost per sequence drops as volume increases. This is the feature to lead with in upsells.',
        category: 'margin_analysis',
        featureKey: 'email_sequences',
        customerId: null,
        createdAt: new Date().toISOString()
      },
      {
        id: 'insight-4',
        severity: 'WARNING',
        title: 'CRM Integrations margin is eroding — down from 74% to 65% this month',
        description: 'CRM sync events increased 40% month-over-month but revenue per sync stayed flat. The cost increase is driven by gpt-4o usage for data mapping. Consider caching repeat mappings or switching to gpt-4o-mini for known CRM schemas.',
        category: 'cost_optimization',
        featureKey: 'crm_integrations',
        customerId: null,
        createdAt: new Date().toISOString()
      },
      {
        id: 'insight-5',
        severity: 'POSITIVE',
        title: 'SalesForge (Enterprise) is your most efficient customer at $0.19/event',
        description: 'Anika Patel generates 42,000 API calls at $138 total cost — $0.003/call. Her usage patterns (large batches, embedding-heavy workloads) suggest she would be a strong case study for enterprise prospects evaluating cost efficiency.',
        category: 'customer_profitability',
        featureKey: null,
        customerId: CUSTOMER_IDS.salesforge,
        createdAt: new Date().toISOString()
      }
    ],
    success: true
  })

  // Account settings
  queryClient.setQueryData(['account-settings'], {
    data: {
      stripeMode: 'FULL_SYNC' as const,
      stripeEnabled: true,
      stripeCheckoutSuccessUrl: null,
      stripeCheckoutCancelUrl: null,
      currency: 'USD',
      platformMode: (isObserveDemo ? 'OBSERVE' : 'FULL') as 'OBSERVE' | 'FULL'
    },
    success: true
  })

  // Account API key
  queryClient.setQueryData(['account', 'api-key'], {
    data: { apiKey: 'tnso_live_sk_a1b2c3d4e5f6g7h8i9j0' },
    success: true
  })

  // Credit pools per customer (empty — no active credit pools in demo)
  for (const customerId of Object.values(CUSTOMER_IDS)) {
    queryClient.setQueryData(['customer-credit-pools', customerId], {
      data: [],
      success: true
    })
  }

}
