import type {
  Segment,
  SegmentCustomer,
  SegmentFieldOption,
  Automation,
  Experiment,
  Integration,
  Simulation,
  SegmentOptimizationSuggestions,
  Customer,
  PortfolioSummary,
  UsageEvent,
  Plan,
  PlanSubscription,
  Invoice,
  Feature,
  PricingOpportunity
} from '../types'

// ============ FIELD OPTIONS FOR SEGMENT BUILDER ============

export const segmentFieldOptions: SegmentFieldOption[] = [
  // Commercial (Traditional)
  {
    value: 'mrr',
    label: 'MRR',
    category: 'commercial',
    categoryLabel: 'Commercial (Traditional)',
    type: 'numeric'
  },
  {
    value: 'arr',
    label: 'ARR',
    category: 'commercial',
    categoryLabel: 'Commercial (Traditional)',
    type: 'numeric'
  },
  {
    value: 'plan',
    label: 'Plan',
    category: 'commercial',
    categoryLabel: 'Commercial (Traditional)',
    type: 'select',
    options: [
      { value: 'trial', label: 'Trial' },
      { value: 'starter', label: 'Starter' },
      { value: 'growth', label: 'Growth' },
      { value: 'scale', label: 'Scale' },
      { value: 'enterprise', label: 'Enterprise' }
    ]
  },
  {
    value: 'seats',
    label: 'Seats',
    category: 'commercial',
    categoryLabel: 'Commercial (Traditional)',
    type: 'numeric'
  },
  {
    value: 'contract_length',
    label: 'Contract Length',
    category: 'commercial',
    categoryLabel: 'Commercial (Traditional)',
    type: 'numeric'
  },

  // Commercial (AI-Era)
  {
    value: 'carr',
    label: 'CARR (Contracted ARR)',
    category: 'commercial_ai',
    categoryLabel: 'Commercial (AI-Era)',
    type: 'numeric'
  },
  {
    value: 'uarr',
    label: 'UARR (Usage-based ARR)',
    category: 'commercial_ai',
    categoryLabel: 'Commercial (AI-Era)',
    type: 'numeric'
  },
  {
    value: 'apr',
    label: 'APR (Annual Predictable Revenue)',
    category: 'commercial_ai',
    categoryLabel: 'Commercial (AI-Era)',
    type: 'numeric'
  },
  {
    value: 'revenue_volatility',
    label: 'Revenue Volatility (30d)',
    category: 'commercial_ai',
    categoryLabel: 'Commercial (AI-Era)',
    type: 'numeric'
  },

  // Behavioral
  {
    value: 'usage_pct',
    label: 'Usage %',
    category: 'behavioral',
    categoryLabel: 'Behavioral',
    type: 'numeric'
  },
  {
    value: 'days_since_signup',
    label: 'Days Since Signup',
    category: 'behavioral',
    categoryLabel: 'Behavioral',
    type: 'numeric'
  },
  {
    value: 'api_calls_30d',
    label: 'API Calls (30d)',
    category: 'behavioral',
    categoryLabel: 'Behavioral',
    type: 'numeric'
  },
  {
    value: 'last_active',
    label: 'Last Active Date',
    category: 'behavioral',
    categoryLabel: 'Behavioral',
    type: 'date'
  },
  {
    value: 'features_used',
    label: 'Features Used',
    category: 'behavioral',
    categoryLabel: 'Behavioral',
    type: 'numeric'
  },

  // Margin & Cost (DIFFERENTIATOR)
  {
    value: 'gross_margin_pct',
    label: 'Gross Margin %',
    category: 'margin_cost',
    categoryLabel: 'Margin & Cost',
    type: 'numeric'
  },
  {
    value: 'contribution_margin_pct',
    label: 'Contribution Margin %',
    category: 'margin_cost',
    categoryLabel: 'Margin & Cost',
    type: 'numeric'
  },
  {
    value: 'cost_per_seat',
    label: 'Cost per Seat',
    category: 'margin_cost',
    categoryLabel: 'Margin & Cost',
    type: 'numeric'
  },
  {
    value: 'api_cost_30d',
    label: 'API Cost (30d)',
    category: 'margin_cost',
    categoryLabel: 'Margin & Cost',
    type: 'numeric'
  },
  {
    value: 'margin_trend_30d',
    label: 'Margin Trend (30d)',
    category: 'margin_cost',
    categoryLabel: 'Margin & Cost',
    type: 'numeric'
  },
  {
    value: 'cost_volatility_30d',
    label: 'Cost Volatility (30d)',
    category: 'margin_cost',
    categoryLabel: 'Margin & Cost',
    type: 'numeric'
  },

  // AI-Native (DIFFERENTIATOR)
  {
    value: 'usage_ramp_rate',
    label: 'Usage Ramp Rate',
    category: 'ai_native',
    categoryLabel: 'AI-Native',
    type: 'numeric'
  },
  {
    value: 'cost_efficiency_profile',
    label: 'Cost-Efficiency Profile',
    category: 'ai_native',
    categoryLabel: 'AI-Native',
    type: 'select',
    options: [
      { value: 'light', label: 'Light' },
      { value: 'moderate', label: 'Moderate' },
      { value: 'heavy', label: 'Heavy' }
    ]
  },
  {
    value: 'inference_cost_per_action',
    label: 'Inference Cost per Action',
    category: 'ai_native',
    categoryLabel: 'AI-Native',
    type: 'numeric'
  },
  {
    value: 'model_mix_expensive',
    label: 'Model Mix (% expensive models)',
    category: 'ai_native',
    categoryLabel: 'AI-Native',
    type: 'numeric'
  },
  {
    value: 'usage_volatility_30d',
    label: 'Usage Volatility (30d)',
    category: 'ai_native',
    categoryLabel: 'AI-Native',
    type: 'numeric'
  },

  // Signals
  {
    value: 'nps_score',
    label: 'NPS Score',
    category: 'signals',
    categoryLabel: 'Signals',
    type: 'numeric'
  },
  {
    value: 'support_tickets_30d',
    label: 'Support Tickets (30d)',
    category: 'signals',
    categoryLabel: 'Signals',
    type: 'numeric'
  },
  {
    value: 'churn_risk_score',
    label: 'Churn Risk Score',
    category: 'signals',
    categoryLabel: 'Signals',
    type: 'numeric'
  },
  {
    value: 'expansion_signals',
    label: 'Expansion Signals',
    category: 'signals',
    categoryLabel: 'Signals',
    type: 'numeric'
  }
]

// ============ CUSTOMERS ============

const generateMarginTrendData = (startMargin: number, trend: number, days: number = 30) => {
  const data = []
  let margin = startMargin - trend * days
  for (let i = days; i >= 0; i -= 3) {
    const date = new Date()
    date.setDate(date.getDate() - i)
    margin += trend * 3 + (Math.random() - 0.5) * 2
    data.push({
      date: date.toISOString().split('T')[0],
      margin: Math.round(margin * 10) / 10
    })
  }
  return data
}

// Segment customer pool - used to populate segment.customers fields
const segmentCustomerPool: SegmentCustomer[] = [
  {
    id: 'cust_1',
    name: 'PipelineAI',
    email: 'billing@pipelineai.io',
    mrr: 6400,
    carr: 5760,
    uarr: 640,
    grossMarginPct: -12,
    marginTrend: -9,
    costPerSeat: 28.4,
    apiCost30d: 7800,
    plan: 'Scale',
    seats: 32,
    customerSince: 'March 2025',
    inSegmentSince: '14 days',
    segments: ['High-Volume Senders', 'Enrichment Power Users'],
    activeExperiments: [
      { id: 'exp_001', name: 'Enrichment Pricing Test', variant: 'Variant B', enrolledDaysAgo: 7 }
    ],
    automationHistory: [
      {
        name: 'Margin Rescue',
        triggeredAt: '2 days ago',
        actions: ['Slack alert sent', 'Experiment enrolled']
      },
      {
        name: 'Usage Limit Warning',
        triggeredAt: '14 days ago',
        actions: ['Email sent to billing@pipelineai.io']
      }
    ],
    marginTrendData: generateMarginTrendData(-12, -0.3)
  },
  {
    id: 'cust_2',
    name: 'RevenueBot',
    email: 'accounts@revenuebot.ai',
    mrr: 9800,
    carr: 8820,
    uarr: 980,
    grossMarginPct: -6,
    marginTrend: -4,
    costPerSeat: 24.1,
    apiCost30d: 11200,
    plan: 'Scale',
    seats: 48,
    customerSince: 'January 2025',
    inSegmentSince: '7 days',
    segments: ['High-Volume Senders'],
    activeExperiments: [
      { id: 'exp_001', name: 'Enrichment Pricing Test', variant: 'Control', enrolledDaysAgo: 7 }
    ],
    automationHistory: [
      { name: 'Margin Rescue', triggeredAt: '5 days ago', actions: ['Slack alert sent'] }
    ],
    marginTrendData: generateMarginTrendData(-6, -0.13)
  },
  {
    id: 'cust_3',
    name: 'CloseFast',
    email: 'finance@closefast.io',
    mrr: 890,
    carr: 712,
    uarr: 178,
    grossMarginPct: -18,
    marginTrend: -12,
    costPerSeat: 35.6,
    apiCost30d: 1420,
    plan: 'Starter',
    seats: 5,
    customerSince: 'November 2025',
    inSegmentSince: '21 days',
    segments: ['High-Volume Senders', 'Enrichment Power Users'],
    activeExperiments: [],
    automationHistory: [
      {
        name: 'Margin Rescue',
        triggeredAt: '18 days ago',
        actions: ['Slack alert sent', 'CS task created']
      }
    ],
    marginTrendData: generateMarginTrendData(-18, -0.4)
  },
  {
    id: 'cust_4',
    name: 'LeadGenius',
    email: 'billing@leadgenius.co',
    mrr: 2100,
    carr: 1890,
    uarr: 210,
    grossMarginPct: -3,
    marginTrend: -1,
    costPerSeat: 18.2,
    apiCost30d: 2340,
    plan: 'Growth',
    seats: 15,
    customerSince: 'June 2025',
    inSegmentSince: '3 days',
    segments: ['High-Volume Senders'],
    activeExperiments: [
      { id: 'exp_001', name: 'Enrichment Pricing Test', variant: 'Variant A', enrolledDaysAgo: 3 }
    ],
    automationHistory: [],
    marginTrendData: generateMarginTrendData(-3, -0.05)
  },
  {
    id: 'cust_5',
    name: 'DealFlow',
    email: 'ops@dealflow.tech',
    mrr: 18200,
    carr: 16380,
    uarr: 1820,
    grossMarginPct: -10,
    marginTrend: -6,
    costPerSeat: 26.8,
    apiCost30d: 21400,
    plan: 'Enterprise',
    seats: 85,
    customerSince: 'August 2024',
    inSegmentSince: '18 days',
    segments: ['High-Volume Senders', 'Meeting Machines'],
    activeExperiments: [
      { id: 'exp_001', name: 'Enrichment Pricing Test', variant: 'Variant B', enrolledDaysAgo: 14 }
    ],
    automationHistory: [
      {
        name: 'Margin Rescue',
        triggeredAt: '16 days ago',
        actions: ['Slack alert sent', 'Experiment enrolled']
      }
    ],
    marginTrendData: generateMarginTrendData(-10, -0.2)
  },
  {
    id: 'cust_1',
    name: 'Outbound.io',
    email: 'admin@outbound.io',
    mrr: 18200,
    carr: 16380,
    uarr: 1820,
    grossMarginPct: 76,
    marginTrend: 2,
    costPerSeat: 9.4,
    apiCost30d: 4380,
    plan: 'Enterprise',
    seats: 85,
    customerSince: 'February 2024',
    inSegmentSince: '45 days',
    segments: ['Meeting Machines', 'Intent-Driven'],
    activeExperiments: [
      { id: 'exp_002', name: 'LinkedIn Upsell', variant: 'Variant A', enrolledDaysAgo: 21 }
    ],
    automationHistory: [
      {
        name: 'Expansion Opportunity',
        triggeredAt: '42 days ago',
        actions: ['Added to Salesforce campaign', 'Slack #sales notified']
      }
    ],
    marginTrendData: generateMarginTrendData(76, 0.07)
  },
  {
    id: 'cust_2',
    name: 'SalesForge',
    email: 'billing@salesforge.com',
    mrr: 2100,
    carr: 1890,
    uarr: 210,
    grossMarginPct: 72,
    marginTrend: 3,
    costPerSeat: 12.8,
    apiCost30d: 588,
    plan: 'Growth',
    seats: 15,
    customerSince: 'April 2025',
    inSegmentSince: '30 days',
    segments: ['Meeting Machines'],
    activeExperiments: [
      { id: 'exp_004', name: 'Expansion Pricing', variant: 'Control', enrolledDaysAgo: 14 }
    ],
    automationHistory: [],
    marginTrendData: generateMarginTrendData(72, 0.1)
  },
  {
    id: 'cust_3',
    name: 'ProspectLab',
    email: 'accounts@prospectlab.ai',
    mrr: 890,
    carr: 846,
    uarr: 44,
    grossMarginPct: 68,
    marginTrend: 1,
    costPerSeat: 14.2,
    apiCost30d: 285,
    plan: 'Starter',
    seats: 5,
    customerSince: 'December 2025',
    inSegmentSince: '60 days',
    segments: ['Intent-Driven'],
    activeExperiments: [
      { id: 'exp_003', name: 'Enterprise Packaging', variant: 'Variant A', enrolledDaysAgo: 45 }
    ],
    automationHistory: [],
    marginTrendData: generateMarginTrendData(68, 0.03)
  },
  {
    id: 'cust_4',
    name: 'ColdReach',
    email: 'billing@coldreach.io',
    mrr: 1600,
    carr: 1280,
    uarr: 320,
    grossMarginPct: 35,
    marginTrend: -14,
    costPerSeat: 22.6,
    apiCost30d: 1040,
    plan: 'Growth',
    seats: 8,
    customerSince: 'July 2025',
    inSegmentSince: '12 days',
    segments: ['Enrichment Power Users', 'LinkedIn Heavy'],
    activeExperiments: [],
    automationHistory: [
      { name: 'Cost Spike Alert', triggeredAt: '10 days ago', actions: ['Slack #finops notified'] }
    ],
    marginTrendData: generateMarginTrendData(35, -0.47)
  },
  {
    id: 'cust_5',
    name: 'SequenceHQ',
    email: 'ops@sequencehq.com',
    mrr: 3200,
    carr: 2560,
    uarr: 640,
    grossMarginPct: 40,
    marginTrend: -6,
    costPerSeat: 20.8,
    apiCost30d: 1920,
    plan: 'Scale',
    seats: 12,
    customerSince: 'May 2025',
    inSegmentSince: '8 days',
    segments: ['LinkedIn Heavy'],
    activeExperiments: [],
    automationHistory: [],
    marginTrendData: generateMarginTrendData(40, -0.2)
  }
]

// ============ SEGMENTS ============

export const segments: Segment[] = [
  {
    id: 'segment_001',
    name: 'High-Volume Senders',
    icon: 'Send',
    color: 'red',
    customerCount: 127,
    customerTrend: 'up',
    customerTrendValue: 8,
    mrr: 89400,
    carr: 67000,
    uarr: 22400,
    grossMarginPct: -12,
    marginTrend: 'down',
    marginTrendValue: 3,
    rules: [
      {
        id: 'r1',
        field: 'gross_margin_pct',
        fieldLabel: 'Gross Margin',
        operator: 'less_than',
        operatorLabel: 'is less than',
        value: 0,
        valueLabel: '0%'
      }
    ],
    automationCount: 2,
    experimentCount: 1,
    linkedAutomationNames: ['Margin Rescue - High Value', 'Cost Spike Alert'],
    linkedExperimentNames: ['Enrichment Pricing Test'],
    lastUpdated: '2 hours ago',
    status: 'active',
    customers: segmentCustomerPool.filter((c) => c.grossMarginPct !== null && c.grossMarginPct < 0),
    marginDistribution: [
      { range: '-30% to -20%', count: 23 },
      { range: '-20% to -10%', count: 45 },
      { range: '-10% to 0%', count: 59 }
    ]
  },
  {
    id: 'segment_002',
    name: 'Enrichment Power Users',
    icon: 'Search',
    color: 'amber',
    customerCount: 12,
    customerTrend: 'up',
    customerTrendValue: 22,
    mrr: 42000,
    carr: 31500,
    uarr: 10500,
    grossMarginPct: 38,
    marginTrend: 'down',
    marginTrendValue: 18,
    rules: [
      {
        id: 'r1',
        field: 'margin_trend_30d',
        fieldLabel: 'Margin Trend (30d)',
        operator: 'at_most',
        operatorLabel: 'is at most',
        value: -20,
        valueLabel: '-20%'
      },
      {
        id: 'r2',
        field: 'usage_pct',
        fieldLabel: 'Usage Trend (30d)',
        operator: 'at_least',
        operatorLabel: 'is at least',
        value: 20,
        valueLabel: '+20%'
      }
    ],
    automationCount: 1,
    experimentCount: 1,
    linkedAutomationNames: ['Cost Spike Alert'],
    linkedExperimentNames: ['Enrichment Pricing Test'],
    lastUpdated: '4 hours ago',
    status: 'active',
    customers: segmentCustomerPool.filter((c) => c.marginTrend !== null && c.marginTrend <= -5),
    marginDistribution: [
      { range: '20% to 30%', count: 8 },
      { range: '30% to 40%', count: 14 },
      { range: '40% to 50%', count: 12 }
    ]
  },
  {
    id: 'segment_003',
    name: 'Meeting Machines',
    icon: 'Calendar',
    color: 'green',
    customerCount: 156,
    customerTrend: 'up',
    customerTrendValue: 5,
    mrr: 234000,
    carr: 210600,
    uarr: 23400,
    grossMarginPct: 78,
    marginTrend: 'up',
    marginTrendValue: 2,
    rules: [
      {
        id: 'r1',
        field: 'gross_margin_pct',
        fieldLabel: 'Gross Margin',
        operator: 'at_least',
        operatorLabel: 'is at least',
        value: 70,
        valueLabel: '70%'
      },
      {
        id: 'r2',
        field: 'usage_pct',
        fieldLabel: 'Usage Headroom',
        operator: 'at_least',
        operatorLabel: 'is at least',
        value: 50,
        valueLabel: '50%'
      }
    ],
    automationCount: 1,
    experimentCount: 2,
    linkedAutomationNames: ['Expansion Opportunity'],
    linkedExperimentNames: ['LinkedIn Upsell', 'Expansion Pricing'],
    lastUpdated: '1 hour ago',
    status: 'active',
    customers: segmentCustomerPool.filter(
      (c) => c.grossMarginPct !== null && c.grossMarginPct >= 70
    ),
    marginDistribution: [
      { range: '70% to 75%', count: 42 },
      { range: '75% to 80%', count: 68 },
      { range: '80% to 85%', count: 46 }
    ]
  },
  {
    id: 'segment_004',
    name: 'Intent-Driven',
    icon: 'Target',
    color: 'blue',
    customerCount: 412,
    customerTrend: 'stable',
    mrr: 618000,
    carr: 556200,
    uarr: 61800,
    grossMarginPct: 64,
    marginTrend: 'stable',
    rules: [
      {
        id: 'r1',
        field: 'usage_pct',
        fieldLabel: 'Usage',
        operator: 'at_least',
        operatorLabel: 'is at least',
        value: 80,
        valueLabel: '80%'
      },
      {
        id: 'r2',
        field: 'plan',
        fieldLabel: 'Plan',
        operator: 'is_any_of',
        operatorLabel: 'is any of',
        value: ['growth', 'scale', 'enterprise'],
        valueLabel: 'Growth, Scale, or Enterprise'
      }
    ],
    automationCount: 0,
    experimentCount: 1,
    linkedAutomationNames: [],
    linkedExperimentNames: ['LinkedIn Upsell'],
    lastUpdated: '30 minutes ago',
    status: 'active',
    customers: segmentCustomerPool.filter((c) => c.plan === 'Pro' || c.plan === 'Enterprise'),
    marginDistribution: [
      { range: '50% to 60%', count: 89 },
      { range: '60% to 70%', count: 187 },
      { range: '70% to 80%', count: 136 }
    ]
  },
  {
    id: 'segment_005',
    name: 'Trial Users',
    icon: 'Clock',
    color: 'slate',
    customerCount: 89,
    customerTrend: 'down',
    customerTrendValue: 12,
    mrr: 0,
    carr: 0,
    uarr: 0,
    grossMarginPct: null,
    marginTrend: 'stable',
    rules: [
      {
        id: 'r1',
        field: 'plan',
        fieldLabel: 'Plan',
        operator: 'is',
        operatorLabel: 'is',
        value: 'trial',
        valueLabel: 'Trial'
      },
      {
        id: 'r2',
        field: 'days_since_signup',
        fieldLabel: 'Days Since Signup',
        operator: 'at_most',
        operatorLabel: 'is at most',
        value: 14,
        valueLabel: '14 days'
      }
    ],
    automationCount: 1,
    experimentCount: 1,
    linkedAutomationNames: ['Slow Ramp Intervention'],
    linkedExperimentNames: ['Trial Conversion'],
    lastUpdated: '15 minutes ago',
    status: 'active',
    customers: [],
    marginDistribution: []
  },
  {
    id: 'segment_006',
    name: 'Deal Closers',
    icon: 'Trophy',
    color: 'purple',
    customerCount: 23,
    customerTrend: 'up',
    customerTrendValue: 4,
    mrr: 287500,
    carr: 273125,
    uarr: 14375,
    grossMarginPct: 72,
    marginTrend: 'up',
    marginTrendValue: 1,
    rules: [
      {
        id: 'r1',
        field: 'mrr',
        fieldLabel: 'MRR',
        operator: 'at_least',
        operatorLabel: 'is at least',
        value: 2000,
        valueLabel: '$2,000'
      },
      {
        id: 'r2',
        field: 'seats',
        fieldLabel: 'Seats',
        operator: 'at_least',
        operatorLabel: 'is at least',
        value: 20,
        valueLabel: '20'
      }
    ],
    automationCount: 0,
    experimentCount: 1,
    linkedAutomationNames: [],
    linkedExperimentNames: ['Enterprise Packaging'],
    lastUpdated: '3 hours ago',
    status: 'active',
    customers: segmentCustomerPool.filter((c) => c.mrr >= 2000 && c.seats >= 15),
    marginDistribution: [
      { range: '65% to 70%', count: 6 },
      { range: '70% to 75%', count: 11 },
      { range: '75% to 80%', count: 6 }
    ]
  },
  {
    id: 'segment_007',
    name: 'LinkedIn Heavy',
    icon: 'Linkedin',
    color: 'orange',
    customerCount: 67,
    customerTrend: 'up',
    customerTrendValue: 15,
    mrr: 78300,
    carr: 54810,
    uarr: 23490,
    grossMarginPct: 42,
    marginTrend: 'down',
    marginTrendValue: 8,
    rules: [
      {
        id: 'r1',
        field: 'cost_efficiency_profile',
        fieldLabel: 'Cost-Efficiency',
        operator: 'is',
        operatorLabel: 'is',
        value: 'heavy',
        valueLabel: 'Heavy'
      },
      {
        id: 'r2',
        field: 'gross_margin_pct',
        fieldLabel: 'Gross Margin',
        operator: 'less_than',
        operatorLabel: 'is less than',
        value: 50,
        valueLabel: '50%'
      }
    ],
    automationCount: 1,
    experimentCount: 1,
    linkedAutomationNames: ['Cost Spike Alert'],
    linkedExperimentNames: ['Enrichment Pricing Test'],
    lastUpdated: '5 hours ago',
    status: 'active',
    customers: segmentCustomerPool.filter(
      (c) => c.grossMarginPct !== null && c.grossMarginPct < 50 && c.grossMarginPct >= 0
    ),
    marginDistribution: [
      { range: '30% to 35%', count: 12 },
      { range: '35% to 40%', count: 24 },
      { range: '40% to 45%', count: 31 }
    ]
  },
  {
    id: 'segment_008',
    name: 'Slow Ramp',
    icon: 'Snail',
    color: 'yellow',
    customerCount: 41,
    customerTrend: 'down',
    customerTrendValue: 6,
    mrr: 28700,
    carr: 20090,
    uarr: 8610,
    grossMarginPct: 55,
    marginTrend: 'down',
    marginTrendValue: 2,
    rules: [
      {
        id: 'r1',
        field: 'usage_ramp_rate',
        fieldLabel: 'Ramp Rate',
        operator: 'less_than',
        operatorLabel: 'is less than',
        value: 50,
        valueLabel: '50% of Average'
      },
      {
        id: 'r2',
        field: 'days_since_signup',
        fieldLabel: 'Days Since Signup',
        operator: 'between',
        operatorLabel: 'is between',
        value: [14, 60],
        valueLabel: '14-60 days'
      }
    ],
    automationCount: 1,
    experimentCount: 0,
    linkedAutomationNames: ['Slow Ramp Intervention'],
    linkedExperimentNames: [],
    lastUpdated: '6 hours ago',
    status: 'active',
    customers: [],
    marginDistribution: [
      { range: '45% to 50%', count: 8 },
      { range: '50% to 55%', count: 18 },
      { range: '55% to 60%', count: 15 }
    ]
  }
]

// ============ AUTOMATIONS ============

export const automations: Automation[] = [
  {
    id: 'auto_001',
    name: 'Margin Rescue - High Value',
    type: 'action',
    trigger: {
      type: 'time_in_segment',
      typeLabel: 'Time in segment exceeded',
      segmentId: 'segment_001',
      segmentName: 'High-Volume Senders',
      threshold: 48,
      unit: 'hours'
    },
    conditions: [
      {
        id: 'c1',
        field: 'mrr',
        fieldLabel: 'MRR',
        operator: 'at_least',
        operatorLabel: 'is at least',
        value: 500,
        valueLabel: '$500'
      }
    ],
    actions: [
      {
        type: 'slack',
        typeLabel: 'Send Slack message',
        icon: 'MessageSquare',
        config: { channel: '#margin-alerts' }
      },
      {
        type: 'run_experiment',
        typeLabel: 'Run experiment',
        icon: 'Beaker',
        config: { experimentId: 'exp_001', experimentName: 'Enrichment Pricing Test' }
      }
    ],
    status: 'active',
    frequencyCap: { maxTriggers: 1, periodDays: 30 },
    stats: { triggeredThisWeek: 12, lastTriggered: '2 hours ago', actionsExecuted: 24 },
    pauseDuringExperiments: true,
    pausedByExperimentId: 'exp_001',
    pausedByExperimentName: 'Enrichment Pricing Test',
    pausedCustomerCount: 67
  },
  {
    id: 'auto_002',
    name: 'Usage Limit Warning',
    type: 'alert',
    trigger: {
      type: 'usage_threshold',
      typeLabel: 'Usage threshold exceeded',
      threshold: 80,
      unit: '%',
      direction: 'above'
    },
    conditions: [],
    actions: [
      {
        type: 'email',
        typeLabel: 'Send email',
        icon: 'Mail',
        config: { template: 'usage_warning', to: 'billing_contact' }
      },
      {
        type: 'in_app',
        typeLabel: 'Show in-app message',
        icon: 'Bell',
        config: { type: 'warning', message: 'Approaching usage limit' }
      }
    ],
    status: 'active',
    frequencyCap: { maxTriggers: 2, periodDays: 7 },
    stats: { triggeredThisWeek: 34, lastTriggered: '1 hour ago', actionsExecuted: 68 },
    pauseDuringExperiments: true
  },
  {
    id: 'auto_003',
    name: 'Expansion Opportunity',
    type: 'action',
    trigger: {
      type: 'segment_entry',
      typeLabel: 'Customer enters segment',
      segmentId: 'segment_003',
      segmentName: 'Meeting Machines'
    },
    conditions: [],
    actions: [
      {
        type: 'crm_update',
        typeLabel: 'Update CRM record',
        icon: 'Database',
        config: { action: 'add_to_campaign', campaign: 'expansion_2024' }
      },
      {
        type: 'slack',
        typeLabel: 'Send Slack message',
        icon: 'MessageSquare',
        config: { channel: '#sales' }
      }
    ],
    status: 'active',
    frequencyCap: { maxTriggers: 1, periodDays: 90 },
    stats: { triggeredThisWeek: 8, lastTriggered: '4 hours ago', actionsExecuted: 16 },
    pauseDuringExperiments: true,
    pausedByExperimentId: 'exp_006',
    pausedByExperimentName: 'Early Test - Discount Offer',
    pausedCustomerCount: 156
  },
  {
    id: 'auto_004',
    name: 'Slow Ramp Intervention',
    type: 'action',
    trigger: {
      type: 'ramp_rate_below',
      typeLabel: 'Ramp rate below average',
      threshold: 50,
      unit: '%'
    },
    conditions: [
      {
        id: 'c1',
        field: 'days_since_signup',
        fieldLabel: 'Days Since Signup',
        operator: 'between',
        operatorLabel: 'is between',
        value: [30, 60],
        valueLabel: '30-60 days'
      }
    ],
    actions: [
      {
        type: 'create_task',
        typeLabel: 'Create task',
        icon: 'CheckSquare',
        config: { assignTo: 'cs_team', priority: 'high' }
      },
      {
        type: 'email',
        typeLabel: 'Send email',
        icon: 'Mail',
        config: { template: 'onboarding_help', sequence: true }
      }
    ],
    status: 'active',
    frequencyCap: { maxTriggers: 1, periodDays: 14 },
    stats: { triggeredThisWeek: 15, lastTriggered: '6 hours ago', actionsExecuted: 30 },
    pauseDuringExperiments: true
  },
  {
    id: 'auto_005',
    name: 'Cost Spike Alert',
    type: 'alert',
    trigger: {
      type: 'cost_spike',
      typeLabel: 'Cost spike detected',
      threshold: 50,
      unit: '%'
    },
    conditions: [
      {
        id: 'c1',
        field: 'mrr',
        fieldLabel: 'MRR',
        operator: 'at_least',
        operatorLabel: 'is at least',
        value: 1000,
        valueLabel: '$1,000'
      }
    ],
    actions: [
      {
        type: 'slack',
        typeLabel: 'Send Slack message',
        icon: 'MessageSquare',
        config: { channel: '#finops' }
      },
      {
        type: 'create_task',
        typeLabel: 'Create task',
        icon: 'CheckSquare',
        config: { type: 'investigation', assignTo: 'finops_team' }
      }
    ],
    status: 'paused',
    frequencyCap: { maxTriggers: 1, periodDays: 7 },
    stats: { triggeredThisWeek: 3, lastTriggered: '2 days ago', actionsExecuted: 6 },
    pauseDuringExperiments: false
  },
  {
    id: 'auto_006',
    name: 'Churn Signal',
    type: 'alert',
    trigger: {
      type: 'segment_exit',
      typeLabel: 'Customer exits segment',
      segmentId: 'segment_004',
      segmentName: 'Intent-Driven'
    },
    conditions: [],
    actions: [
      { type: 'email', typeLabel: 'Send email', icon: 'Mail', config: { template: 'nps_survey' } },
      {
        type: 'slack',
        typeLabel: 'Send Slack message',
        icon: 'MessageSquare',
        config: { channel: '#cs-alerts' }
      }
    ],
    status: 'paused',
    frequencyCap: { maxTriggers: 1, periodDays: 30 },
    stats: { triggeredThisWeek: 7, lastTriggered: '1 day ago', actionsExecuted: 14 },
    pauseDuringExperiments: true
  },
  // ============ EXPERIMENTS AS AUTOMATIONS ============
  {
    id: 'auto_exp_001',
    name: 'Usage-Based Pricing Test',
    type: 'experiment',
    trigger: {
      type: 'segment_entry',
      typeLabel: 'Segment',
      segmentId: 'segment_003',
      segmentName: 'Meeting Machines'
    },
    conditions: [],
    actions: [],
    status: 'running',
    frequencyCap: { maxTriggers: 1, periodDays: 365 },
    stats: { triggeredThisWeek: 0, lastTriggered: null, actionsExecuted: 0 },
    pauseDuringExperiments: false,
    experiment: {
      segmentId: 'segment_003',
      segmentName: 'Meeting Machines',
      segmentSize: 156,
      phase: 'trending',
      trend: 'positive',
      successMetric: 'retention',
      marginFloor: 65,
      weekNumber: 5,
      totalWeeks: 8
    }
  },
  {
    id: 'auto_exp_002',
    name: 'Enterprise Packaging',
    type: 'experiment',
    trigger: {
      type: 'segment_entry',
      typeLabel: 'Segment',
      segmentId: 'segment_006',
      segmentName: 'Deal Closers'
    },
    conditions: [],
    actions: [],
    status: 'running',
    frequencyCap: { maxTriggers: 1, periodDays: 365 },
    stats: { triggeredThisWeek: 0, lastTriggered: null, actionsExecuted: 0 },
    pauseDuringExperiments: false,
    experiment: {
      segmentId: 'segment_006',
      segmentName: 'Deal Closers',
      segmentSize: 23,
      phase: 'early_data',
      successMetric: 'conversion',
      marginFloor: 60,
      weekNumber: 8,
      totalWeeks: 12
    }
  },
  {
    id: 'auto_exp_003',
    name: 'Aggressive Usage Pricing',
    type: 'experiment',
    trigger: {
      type: 'segment_entry',
      typeLabel: 'Segment',
      segmentId: 'segment_002',
      segmentName: 'Enrichment Power Users'
    },
    conditions: [],
    actions: [],
    status: 'stopped',
    frequencyCap: { maxTriggers: 1, periodDays: 365 },
    stats: { triggeredThisWeek: 0, lastTriggered: null, actionsExecuted: 0 },
    pauseDuringExperiments: false,
    experiment: {
      segmentId: 'segment_002',
      segmentName: 'Enrichment Power Users',
      segmentSize: 12,
      phase: 'stopped',
      successMetric: 'margin',
      marginFloor: 65,
      weekNumber: 3,
      totalWeeks: 8,
      stoppedReason: 'Margin dropped to 58%, below your 65% floor'
    }
  },
  {
    id: 'auto_exp_004',
    name: 'Enrichment Pricing Test',
    type: 'experiment',
    trigger: {
      type: 'segment_entry',
      typeLabel: 'Segment',
      segmentId: 'segment_001',
      segmentName: 'High-Volume Senders'
    },
    conditions: [],
    actions: [],
    status: 'completed',
    frequencyCap: { maxTriggers: 1, periodDays: 365 },
    stats: { triggeredThisWeek: 0, lastTriggered: null, actionsExecuted: 0 },
    pauseDuringExperiments: false,
    experiment: {
      segmentId: 'segment_001',
      segmentName: 'High-Volume Senders',
      segmentSize: 127,
      phase: 'ready_to_call',
      trend: 'positive',
      successMetric: 'margin',
      marginFloor: 65,
      weekNumber: 7,
      totalWeeks: 8
    }
  },
  // ============ MODEL ROUTING EXPERIMENTS ============
  {
    id: 'auto_exp_routing_001',
    name: 'Enrichment: Clearbit vs Apollo',
    type: 'experiment',
    trigger: { type: 'scheduled', typeLabel: 'API Requests', scheduleFrequency: 'daily' },
    conditions: [],
    actions: [],
    status: 'completed',
    frequencyCap: { maxTriggers: 1, periodDays: 365 },
    stats: { triggeredThisWeek: 0, lastTriggered: null, actionsExecuted: 0 },
    pauseDuringExperiments: false,
    experiment: {
      segmentId: 'routing_all',
      segmentName: 'All API Requests',
      segmentSize: 847000,
      phase: 'ready_to_call',
      trend: 'positive',
      successMetric: 'margin',
      marginFloor: 0,
      weekNumber: 3,
      totalWeeks: 7
    }
  },
  {
    id: 'auto_exp_routing_002',
    name: 'Lead Scoring: GPT-4o-mini vs Llama 3.1',
    type: 'experiment',
    trigger: { type: 'scheduled', typeLabel: 'API Requests', scheduleFrequency: 'daily' },
    conditions: [],
    actions: [],
    status: 'running',
    frequencyCap: { maxTriggers: 1, periodDays: 365 },
    stats: { triggeredThisWeek: 0, lastTriggered: null, actionsExecuted: 0 },
    pauseDuringExperiments: false,
    experiment: {
      segmentId: 'routing_classification',
      segmentName: 'Classification Requests',
      segmentSize: 312000,
      phase: 'trending',
      trend: 'negative',
      successMetric: 'margin',
      marginFloor: 0,
      weekNumber: 2,
      totalWeeks: 7
    }
  }
]

// ============ EXPERIMENTS ============

export const experiments: Experiment[] = [
  {
    id: 'exp_001',
    name: 'Enrichment Cost Routing',
    status: 'running',
    phase: 'ready_to_call',
    trend: 'positive',
    successMetric: 'margin',
    autoPauseAutomations: true,
    pausedAutomationIds: ['auto_001'],
    segmentId: 'segment_001',
    segmentName: 'High-Volume Senders',
    segmentSize: 127,
    category: 'feature_routing',
    audienceType: 'feature_eligible',
    audienceDescription: 'Accounts with high API usage flagged for cost optimization',
    audienceNote: 'Only affects AI inference requests, not stored data',
    testingQuestion: 'Can smart model routing reduce costs without degrading quality?',
    controlDescription: 'Always route to GPT-4 for all requests',
    variantDescription: 'Smart routing: GPT-4 for complex queries, GPT-3.5 for simple ones',
    hypothesis: 'Smart routing will reduce API costs by 30% while maintaining quality scores',
    variants: [
      {
        id: 'v_control',
        name: 'Always GPT-4',
        description: 'Route all requests to GPT-4 (current behavior)',
        weight: 50,
        metrics: {
          accountCount: 42,
          conversionRate: 2.1,
          conversionLift: null,
          conversionPValue: null,
          revenuePerAccount: 847,
          marginPerAccount: 542,
          grossMarginPct: 64,
          marginDelta: null,
          unprofitableAccounts: 4
        },
        guardrailStatus: null
      },
      {
        id: 'v_tiered',
        name: 'Aggressive Routing',
        description: 'GPT-3.5 default, GPT-4 only for complex queries',
        weight: 25,
        metrics: {
          accountCount: 43,
          conversionRate: 2.8,
          conversionLift: 33,
          conversionPValue: 0.04,
          revenuePerAccount: 923,
          marginPerAccount: 498,
          grossMarginPct: 54,
          marginDelta: -10,
          unprofitableAccounts: 11
        },
        guardrailStatus: 'violated'
      },
      {
        id: 'v_value',
        name: 'Smart Routing',
        description: 'AI classifier routes based on query complexity',
        weight: 25,
        metrics: {
          accountCount: 42,
          conversionRate: 2.4,
          conversionLift: 14,
          conversionPValue: 0.12,
          revenuePerAccount: 912,
          marginPerAccount: 602,
          grossMarginPct: 66,
          marginDelta: 2,
          unprofitableAccounts: 3
        },
        guardrailStatus: 'healthy'
      }
    ],
    guardrails: {
      marginFloor: { enabled: true, value: 50 },
      costCeiling: { enabled: false, value: 0 },
      revenueFloor: { enabled: true, value: 10 },
      unprofitableThreshold: { enabled: false, value: 0 },
      behavior: 'soft_stop',
      evaluationWindowDays: 14
    },
    startDate: '2024-12-18',
    endDate: null,
    durationDays: 21,
    recommendation: {
      winner: 'Value-Based',
      winnerVariantId: 'v_value',
      confidence: 87,
      rationale: [
        '+14% conversion lift (p=0.12, 87% Bayesian probability)',
        '+$60/account margin improvement',
        '"Tiered Usage" wins on conversion (+33%) but VIOLATES margin guardrail'
      ]
    },
    marginTrendData: [
      { date: '2024-12-18', control: 64, variantA: 64, variantB: 64 },
      { date: '2024-12-21', control: 64, variantA: 60, variantB: 65 },
      { date: '2024-12-24', control: 64, variantA: 57, variantB: 65 },
      { date: '2024-12-27', control: 64, variantA: 55, variantB: 66 },
      { date: '2024-12-30', control: 64, variantA: 54, variantB: 66 },
      { date: '2025-01-02', control: 64, variantA: 54, variantB: 66 },
      { date: '2025-01-05', control: 64, variantA: 54, variantB: 66 },
      { date: '2025-01-08', control: 64, variantA: 54, variantB: 66 }
    ],
    unprofitableByVariant: [
      {
        variantId: 'v_control',
        variantName: 'Control',
        total: 42,
        unprofitable: 4,
        pctUnprofitable: 9.5,
        avgMarginUnprofitable: -8
      },
      {
        variantId: 'v_tiered',
        variantName: 'Tiered Usage',
        total: 43,
        unprofitable: 11,
        pctUnprofitable: 25.6,
        avgMarginUnprofitable: -15
      },
      {
        variantId: 'v_value',
        variantName: 'Value-Based',
        total: 42,
        unprofitable: 3,
        pctUnprofitable: 7.1,
        avgMarginUnprofitable: -5
      }
    ]
  },
  {
    id: 'exp_002',
    name: 'Meeting Booking Upsell',
    status: 'completed',
    phase: 'no_clear_winner',
    successMetric: 'conversion',
    autoPauseAutomations: true,
    pausedAutomationIds: [],
    segmentId: 'segment_003',
    segmentName: 'Meeting Machines',
    segmentSize: 156,
    category: 'expansion_timing',
    audienceType: 'expansion_eligible',
    audienceDescription: 'Accounts approaching 80% of their usage limit',
    testingQuestion: 'When is the best time to suggest upgrading?',
    controlDescription: 'Prompt at 80% usage (current behavior)',
    variantDescription: 'Prompt at 65% usage with projected savings',
    hypothesis: 'Earlier prompts will increase upgrade conversion by 15%',
    variants: [
      {
        id: 'v_control',
        name: 'Prompt at 80%',
        description: 'Show upgrade prompt when usage hits 80% of limit',
        weight: 50,
        metrics: {
          accountCount: 78,
          conversionRate: 2.6,
          conversionLift: null,
          conversionPValue: null,
          revenuePerAccount: 850,
          marginPerAccount: 578,
          grossMarginPct: 68,
          marginDelta: null,
          unprofitableAccounts: 2
        },
        guardrailStatus: null
      },
      {
        id: 'v_upsell',
        name: 'Prompt at 65%',
        description: 'Earlier prompt at 65% with projected savings messaging',
        weight: 50,
        metrics: {
          accountCount: 78,
          conversionRate: 3.1,
          conversionLift: 18,
          conversionPValue: 0.08,
          revenuePerAccount: 923,
          marginPerAccount: 602,
          grossMarginPct: 65,
          marginDelta: -3,
          unprofitableAccounts: 3
        },
        guardrailStatus: 'healthy'
      }
    ],
    guardrails: {
      marginFloor: { enabled: false, value: 0 },
      costCeiling: { enabled: false, value: 0 },
      revenueFloor: { enabled: false, value: 0 },
      unprofitableThreshold: { enabled: false, value: 0 },
      behavior: 'soft_stop',
      evaluationWindowDays: 14
    },
    startDate: '2024-12-25',
    endDate: null,
    durationDays: 14,
    recommendation: null,
    marginTrendData: [
      { date: '2024-12-18', control: 67, variantA: 68 },
      { date: '2024-12-22', control: 68, variantA: 67 },
      { date: '2024-12-26', control: 67, variantA: 66 },
      { date: '2024-12-30', control: 69, variantA: 67 },
      { date: '2025-01-03', control: 68, variantA: 66 },
      { date: '2025-01-07', control: 67, variantA: 65 },
      { date: '2025-01-11', control: 68, variantA: 66 },
      { date: '2025-01-15', control: 68, variantA: 65 }
    ],
    unprofitableByVariant: [
      {
        variantId: 'v_control',
        variantName: 'Prompt at 80%',
        total: 78,
        unprofitable: 2,
        pctUnprofitable: 2.6,
        avgMarginUnprofitable: -3
      },
      {
        variantId: 'v_upsell',
        variantName: 'Prompt at 65%',
        total: 78,
        unprofitable: 3,
        pctUnprofitable: 3.8,
        avgMarginUnprofitable: -4
      }
    ]
  },
  {
    id: 'exp_003',
    name: 'Enterprise: Bundle vs À La Carte',
    status: 'completed',
    phase: 'ready_to_call',
    trend: 'positive',
    successMetric: 'conversion',
    autoPauseAutomations: true,
    pausedAutomationIds: [],
    segmentId: 'segment_006',
    segmentName: 'Deal Closers',
    segmentSize: 23,
    category: 'plan_recommendation',
    audienceType: 'sales_opportunities',
    audienceDescription: 'New enterprise deals in sales pipeline',
    audienceNote: 'Applied to sales quotes, not self-serve',
    testingQuestion: 'Do enterprise buyers prefer bundled packages or à la carte pricing?',
    controlDescription: 'Present à la carte pricing with modular add-ons',
    variantDescription: 'Present all-inclusive bundle with volume discount',
    hypothesis: 'Bundles will increase deal size by 20% and reduce sales cycle',
    variants: [
      {
        id: 'v_control',
        name: 'À La Carte',
        description: 'Modular pricing with individual add-on options',
        weight: 50,
        metrics: {
          accountCount: 12,
          conversionRate: 3.2,
          conversionLift: null,
          conversionPValue: null,
          revenuePerAccount: 2180,
          marginPerAccount: 1744,
          grossMarginPct: 80,
          marginDelta: null,
          unprofitableAccounts: 0
        },
        guardrailStatus: null
      },
      {
        id: 'v_premium',
        name: 'All-Inclusive Bundle',
        description: 'Everything included at 15% volume discount',
        weight: 50,
        metrics: {
          accountCount: 11,
          conversionRate: 4.2,
          conversionLift: 31,
          conversionPValue: 0.06,
          revenuePerAccount: 2340,
          marginPerAccount: 1872,
          grossMarginPct: 80,
          marginDelta: 0,
          unprofitableAccounts: 0
        },
        guardrailStatus: 'healthy'
      }
    ],
    guardrails: {
      marginFloor: { enabled: true, value: 60 },
      costCeiling: { enabled: false, value: 0 },
      revenueFloor: { enabled: false, value: 0 },
      unprofitableThreshold: { enabled: false, value: 0 },
      behavior: 'hard_stop',
      evaluationWindowDays: 14
    },
    startDate: '2024-11-15',
    endDate: '2024-12-30',
    durationDays: 45,
    recommendation: {
      winner: 'All-Inclusive Bundle',
      winnerVariantId: 'v_premium',
      confidence: 94,
      rationale: [
        '+31% conversion lift (p=0.06)',
        '+$128/account margin improvement',
        'No guardrail violations'
      ]
    },
    marginTrendData: [
      { date: '2024-11-15', control: 80, variantA: 80 },
      { date: '2024-11-22', control: 80, variantA: 80 },
      { date: '2024-11-29', control: 80, variantA: 80 },
      { date: '2024-12-06', control: 80, variantA: 80 },
      { date: '2024-12-13', control: 80, variantA: 80 },
      { date: '2024-12-20', control: 80, variantA: 80 },
      { date: '2024-12-27', control: 80, variantA: 80 },
      { date: '2024-12-30', control: 80, variantA: 80 }
    ],
    unprofitableByVariant: [
      {
        variantId: 'v_control',
        variantName: 'À La Carte',
        total: 12,
        unprofitable: 0,
        pctUnprofitable: 0,
        avgMarginUnprofitable: 0
      },
      {
        variantId: 'v_premium',
        variantName: 'All-Inclusive Bundle',
        total: 11,
        unprofitable: 0,
        pctUnprofitable: 0,
        avgMarginUnprofitable: 0
      }
    ]
  },
  {
    id: 'exp_004',
    name: 'Expansion: Add-On vs Discount',
    status: 'running',
    phase: 'trending',
    trend: 'positive',
    successMetric: 'revenue',
    autoPauseAutomations: false,
    pausedAutomationIds: [],
    segmentId: 'segment_003',
    segmentName: 'Meeting Machines',
    segmentSize: 156,
    category: 'expansion_timing',
    audienceType: 'expansion_eligible',
    audienceDescription: 'Accounts adding seats or upgrading tiers',
    testingQuestion: 'What expansion offer maximizes revenue per account?',
    controlDescription: '15% volume discount on additional seats',
    variantDescription: 'Free premium add-on with seat expansion',
    hypothesis: 'Add-ons will increase expansion revenue by 10% vs discounts',
    variants: [
      {
        id: 'v_control',
        name: '15% Volume Discount',
        description: '15% off on additional seats beyond 10',
        weight: 50,
        metrics: {
          accountCount: 78,
          conversionRate: 3.9,
          conversionLift: null,
          conversionPValue: null,
          revenuePerAccount: 1000,
          marginPerAccount: 720,
          grossMarginPct: 72,
          marginDelta: null,
          unprofitableAccounts: 1
        },
        guardrailStatus: null
      },
      {
        id: 'v_volume',
        name: 'Free Premium Add-On',
        description: 'Analytics dashboard free with 5+ seat expansion',
        weight: 50,
        metrics: {
          accountCount: 78,
          conversionRate: 4.8,
          conversionLift: 24,
          conversionPValue: 0.03,
          revenuePerAccount: 1240,
          marginPerAccount: 918,
          grossMarginPct: 74,
          marginDelta: 2,
          unprofitableAccounts: 1
        },
        guardrailStatus: 'healthy'
      }
    ],
    guardrails: {
      marginFloor: { enabled: false, value: 0 },
      costCeiling: { enabled: false, value: 0 },
      revenueFloor: { enabled: false, value: 0 },
      unprofitableThreshold: { enabled: false, value: 0 },
      behavior: 'soft_stop',
      evaluationWindowDays: 14
    },
    startDate: '2024-12-20',
    endDate: null,
    durationDays: 19,
    recommendation: null,
    marginTrendData: [
      { date: '2024-12-20', control: 72, variantA: 72 },
      { date: '2024-12-25', control: 72, variantA: 73 },
      { date: '2024-12-30', control: 72, variantA: 74 },
      { date: '2025-01-04', control: 72, variantA: 74 },
      { date: '2025-01-08', control: 72, variantA: 74 }
    ],
    unprofitableByVariant: [
      {
        variantId: 'v_control',
        variantName: '15% Volume Discount',
        total: 78,
        unprofitable: 1,
        pctUnprofitable: 1.3,
        avgMarginUnprofitable: -2
      },
      {
        variantId: 'v_volume',
        variantName: 'Free Premium Add-On',
        total: 78,
        unprofitable: 1,
        pctUnprofitable: 1.3,
        avgMarginUnprofitable: -3
      }
    ]
  },
  {
    id: 'exp_005',
    name: 'Signup Plan Recommendation',
    status: 'draft',
    phase: 'early_data',
    successMetric: 'conversion',
    autoPauseAutomations: true,
    pausedAutomationIds: [],
    segmentId: 'segment_005',
    segmentName: 'Trial Users',
    segmentSize: 89,
    category: 'plan_recommendation',
    audienceType: 'new_signups',
    audienceDescription: 'New trial signups during the test period',
    audienceNote: 'Existing customers are unaffected',
    testingQuestion: 'Which plan should we recommend to new signups?',
    controlDescription: 'Recommend Pro plan by default',
    variantDescription: 'AI-personalized plan recommendation based on signup data',
    hypothesis: 'Personalized recommendations will increase trial-to-paid by 25%',
    variants: [
      {
        id: 'v_control',
        name: 'Recommend Pro',
        description: 'Always recommend Pro plan to all new signups',
        weight: 50,
        metrics: {
          accountCount: 0,
          conversionRate: null,
          conversionLift: null,
          conversionPValue: null,
          revenuePerAccount: null,
          marginPerAccount: null,
          grossMarginPct: null,
          marginDelta: null,
          unprofitableAccounts: 0
        },
        guardrailStatus: null
      },
      {
        id: 'v_guided',
        name: 'AI Personalized',
        description: 'ML model recommends plan based on signup profile',
        weight: 50,
        metrics: {
          accountCount: 0,
          conversionRate: null,
          conversionLift: null,
          conversionPValue: null,
          revenuePerAccount: null,
          marginPerAccount: null,
          grossMarginPct: null,
          marginDelta: null,
          unprofitableAccounts: 0
        },
        guardrailStatus: null
      }
    ],
    guardrails: {
      marginFloor: { enabled: false, value: 0 },
      costCeiling: { enabled: false, value: 0 },
      revenueFloor: { enabled: false, value: 0 },
      unprofitableThreshold: { enabled: false, value: 0 },
      behavior: 'soft_stop',
      evaluationWindowDays: 14
    },
    startDate: null,
    endDate: null,
    durationDays: null,
    recommendation: null,
    marginTrendData: [],
    unprofitableByVariant: []
  },
  {
    id: 'exp_006',
    name: 'Churn Save: Discount vs Feature Unlock',
    status: 'running',
    phase: 'early_data',
    trend: undefined,
    successMetric: 'conversion',
    autoPauseAutomations: true,
    pausedAutomationIds: ['auto_003'],
    segmentId: 'segment_003',
    segmentName: 'Meeting Machines',
    segmentSize: 156,
    category: 'retention_strategy',
    audienceType: 'churn_risk',
    audienceDescription: 'Accounts flagged as churn risk by usage patterns',
    audienceNote: 'Only offered when customer initiates cancellation',
    testingQuestion: 'What retention offer saves the most at-risk accounts?',
    controlDescription: '20% discount for 3 months',
    variantDescription: 'Unlock premium feature free for 6 months',
    hypothesis: 'Feature unlock will retain 15% more accounts than discount',
    variants: [
      {
        id: 'v_control',
        name: '20% Discount',
        description: '20% off for 3 months if they stay',
        weight: 50,
        metrics: {
          accountCount: 78,
          conversionRate: null,
          conversionLift: null,
          conversionPValue: null,
          revenuePerAccount: null,
          marginPerAccount: null,
          grossMarginPct: null,
          marginDelta: null,
          unprofitableAccounts: 0
        },
        guardrailStatus: null
      },
      {
        id: 'v_discount',
        name: 'Feature Unlock',
        description: 'Free Analytics Pro for 6 months if they stay',
        weight: 50,
        metrics: {
          accountCount: 78,
          conversionRate: null,
          conversionLift: null,
          conversionPValue: null,
          revenuePerAccount: null,
          marginPerAccount: null,
          grossMarginPct: null,
          marginDelta: null,
          unprofitableAccounts: 0
        },
        guardrailStatus: null
      }
    ],
    guardrails: {
      marginFloor: { enabled: true, value: 65 },
      costCeiling: { enabled: false, value: 0 },
      revenueFloor: { enabled: false, value: 0 },
      unprofitableThreshold: { enabled: false, value: 0 },
      behavior: 'hard_stop',
      evaluationWindowDays: 14
    },
    startDate: '2025-01-04',
    endDate: null,
    durationDays: 5,
    recommendation: null,
    marginTrendData: [
      { date: '2025-01-04', control: 78, variantA: 78 },
      { date: '2025-01-06', control: 78, variantA: 77 },
      { date: '2025-01-08', control: 78, variantA: 76 }
    ],
    unprofitableByVariant: []
  },
  {
    id: 'exp_007',
    name: 'High-Volume Senders: Enrichment Optimization',
    status: 'stopped',
    phase: 'stopped',
    stoppedReason: 'Margin dropped to 42%, below your 50% floor.',
    srmWarning: false,
    successMetric: 'revenue',
    autoPauseAutomations: true,
    pausedAutomationIds: [],
    segmentId: 'segment_007',
    segmentName: 'LinkedIn Heavy',
    segmentSize: 67,
    category: 'feature_routing',
    audienceType: 'feature_eligible',
    audienceDescription: 'High-usage accounts with above-average API costs',
    testingQuestion: 'Can aggressive cost optimization improve margins for high-usage accounts?',
    controlDescription: 'Standard model routing (GPT-4 default)',
    variantDescription: 'Aggressive cost routing (GPT-3.5 default, GPT-4 on-demand)',
    hypothesis: 'Aggressive routing will improve margins by 15pp without quality issues',
    variants: [
      {
        id: 'v_control',
        name: 'Standard Routing',
        description: 'GPT-4 default for all queries',
        weight: 50,
        metrics: {
          accountCount: 34,
          conversionRate: 2.8,
          conversionLift: null,
          conversionPValue: null,
          revenuePerAccount: 1167,
          marginPerAccount: 490,
          grossMarginPct: 42,
          marginDelta: null,
          unprofitableAccounts: 8
        },
        guardrailStatus: 'healthy'
      },
      {
        id: 'v_usage',
        name: 'Aggressive Cost Routing',
        description: 'GPT-3.5 default, GPT-4 only on explicit request',
        weight: 50,
        metrics: {
          accountCount: 33,
          conversionRate: 3.4,
          conversionLift: 21,
          conversionPValue: 0.08,
          revenuePerAccount: 1340,
          marginPerAccount: 335,
          grossMarginPct: 25,
          marginDelta: -17,
          unprofitableAccounts: 18
        },
        guardrailStatus: 'violated'
      }
    ],
    guardrails: {
      marginFloor: { enabled: true, value: 50 },
      costCeiling: { enabled: false, value: 0 },
      revenueFloor: { enabled: false, value: 0 },
      unprofitableThreshold: { enabled: false, value: 0 },
      behavior: 'hard_stop',
      evaluationWindowDays: 14
    },
    startDate: '2024-12-01',
    endDate: '2024-12-18',
    durationDays: 17,
    recommendation: null,
    marginTrendData: [
      { date: '2024-12-01', control: 42, variantA: 42 },
      { date: '2024-12-05', control: 42, variantA: 38 },
      { date: '2024-12-09', control: 42, variantA: 32 },
      { date: '2024-12-13', control: 42, variantA: 28 },
      { date: '2024-12-17', control: 42, variantA: 25 }
    ],
    unprofitableByVariant: [
      {
        variantId: 'v_control',
        variantName: 'Standard Routing',
        total: 34,
        unprofitable: 8,
        pctUnprofitable: 23.5,
        avgMarginUnprofitable: -12
      },
      {
        variantId: 'v_usage',
        variantName: 'Aggressive Cost Routing',
        total: 33,
        unprofitable: 18,
        pctUnprofitable: 54.5,
        avgMarginUnprofitable: -28
      }
    ]
  },
  // ============ MODEL ROUTING EXPERIMENTS ============
  {
    id: 'exp_routing_001',
    name: 'Enrichment: Clearbit vs Apollo',
    status: 'completed',
    phase: 'ready_to_call',
    trend: 'positive',
    successMetric: 'margin',
    autoPauseAutomations: false,
    pausedAutomationIds: [],
    segmentId: 'routing_all',
    segmentName: 'All API Requests',
    segmentSize: 847000,
    category: 'model_routing',
    audienceType: 'api_requests',
    audienceDescription: 'All summarization API requests during test period',
    audienceNote: '847K requests over 3 days',
    testingQuestion: 'Can we use a cheaper model without quality degradation?',
    controlDescription: 'Claude Sonnet 3.5 ($3.00/1M tokens)',
    variantDescription: 'GPT-4o-mini ($0.15/1M tokens)',
    hypothesis: 'GPT-4o-mini maintains 4.0+ quality at 95% cost savings',
    routing: {
      taskType: 'summarization',
      qualityThreshold: 4.0,
      totalRequests: 847000,
      controlModel: 'claude-sonnet-3.5',
      challengerModel: 'gpt-4o-mini',
      controlCostPer1M: 3.0,
      challengerCostPer1M: 0.15,
      controlMetrics: {
        requests: 423500,
        qualityScore: 4.3,
        completionRate: 0.98,
        avgLatencyMs: 1450,
        totalCost: 4235,
        costPerRequest: 0.01
      },
      challengerMetrics: {
        requests: 423500,
        qualityScore: 4.1,
        completionRate: 0.97,
        avgLatencyMs: 890,
        totalCost: 212,
        costPerRequest: 0.0005
      },
      projectedAnnualSavings: 48276
    },
    variants: [
      {
        id: 'v_control',
        name: 'Claude Sonnet 3.5',
        description: 'Current model for summarization tasks',
        weight: 50,
        metrics: {
          accountCount: 423500,
          conversionRate: null,
          conversionLift: null,
          conversionPValue: null,
          revenuePerAccount: null,
          marginPerAccount: null,
          grossMarginPct: null,
          marginDelta: null,
          unprofitableAccounts: 0
        },
        guardrailStatus: null
      },
      {
        id: 'v_challenger',
        name: 'GPT-4o-mini',
        description: 'Challenger model at 95% lower cost',
        weight: 50,
        metrics: {
          accountCount: 423500,
          conversionRate: null,
          conversionLift: null,
          conversionPValue: null,
          revenuePerAccount: null,
          marginPerAccount: null,
          grossMarginPct: null,
          marginDelta: null,
          unprofitableAccounts: 0
        },
        guardrailStatus: 'healthy'
      }
    ],
    guardrails: {
      marginFloor: { enabled: false, value: 0 },
      costCeiling: { enabled: false, value: 0 },
      revenueFloor: { enabled: false, value: 0 },
      unprofitableThreshold: { enabled: false, value: 0 },
      behavior: 'soft_stop',
      evaluationWindowDays: 7
    },
    startDate: '2025-01-06',
    endDate: '2025-01-09',
    durationDays: 3,
    recommendation: {
      winner: 'GPT-4o-mini',
      winnerVariantId: 'v_challenger',
      confidence: 99,
      rationale: [
        'Quality score 4.1 vs 4.3 (within threshold of 4.0)',
        '95% cost reduction ($212 vs $4,235)',
        'Projected annual savings: $48,276',
        'Latency improved by 39% (890ms vs 1,450ms)'
      ]
    },
    marginTrendData: [],
    unprofitableByVariant: []
  },
  {
    id: 'exp_routing_002',
    name: 'Lead Scoring: GPT-4o-mini vs Llama 3.1',
    status: 'running',
    phase: 'trending',
    trend: 'negative',
    successMetric: 'margin',
    autoPauseAutomations: false,
    pausedAutomationIds: [],
    segmentId: 'routing_classification',
    segmentName: 'Classification Requests',
    segmentSize: 312000,
    category: 'model_routing',
    audienceType: 'api_requests',
    audienceDescription: 'Classification requests for intent detection',
    audienceNote: '312K requests over 2 days (Day 2 of 7)',
    testingQuestion: 'Is open-source viable for simple classification?',
    controlDescription: 'Claude Haiku ($0.25/1M tokens)',
    variantDescription: 'Llama 3.1 8B ($0.05/1M tokens)',
    hypothesis: 'Llama 3.1 8B can match Haiku quality at 80% cost savings',
    routing: {
      taskType: 'classification',
      qualityThreshold: 4.0,
      totalRequests: 312000,
      controlModel: 'claude-haiku',
      challengerModel: 'llama-3.1-8b',
      controlCostPer1M: 0.25,
      challengerCostPer1M: 0.05,
      controlMetrics: {
        requests: 156000,
        qualityScore: 4.4,
        completionRate: 0.99,
        avgLatencyMs: 180,
        totalCost: 39,
        costPerRequest: 0.00025
      },
      challengerMetrics: {
        requests: 156000,
        qualityScore: 3.7,
        completionRate: 0.94,
        avgLatencyMs: 95,
        totalCost: 8,
        costPerRequest: 0.00005
      }
    },
    variants: [
      {
        id: 'v_control',
        name: 'Claude Haiku',
        description: 'Current model for classification tasks',
        weight: 50,
        metrics: {
          accountCount: 156000,
          conversionRate: null,
          conversionLift: null,
          conversionPValue: null,
          revenuePerAccount: null,
          marginPerAccount: null,
          grossMarginPct: null,
          marginDelta: null,
          unprofitableAccounts: 0
        },
        guardrailStatus: null
      },
      {
        id: 'v_challenger',
        name: 'Llama 3.1 8B',
        description: 'Open-source challenger at 80% lower cost',
        weight: 50,
        metrics: {
          accountCount: 156000,
          conversionRate: null,
          conversionLift: null,
          conversionPValue: null,
          revenuePerAccount: null,
          marginPerAccount: null,
          grossMarginPct: null,
          marginDelta: null,
          unprofitableAccounts: 0
        },
        guardrailStatus: 'warning'
      }
    ],
    guardrails: {
      marginFloor: { enabled: false, value: 0 },
      costCeiling: { enabled: false, value: 0 },
      revenueFloor: { enabled: false, value: 0 },
      unprofitableThreshold: { enabled: false, value: 0 },
      behavior: 'soft_stop',
      evaluationWindowDays: 7
    },
    startDate: '2025-01-07',
    endDate: null,
    durationDays: 2,
    recommendation: null,
    marginTrendData: [],
    unprofitableByVariant: []
  }
]

// ============ INTEGRATIONS ============

export const integrations: Integration[] = [
  {
    id: 'int_001',
    name: 'Stripe',
    provider: 'stripe',
    logo: 'CreditCard',
    status: 'connected',
    description: 'Revenue data synced automatically',
    lastSync: '2 hours ago',
    customerCount: 2847
  },
  {
    id: 'int_002',
    name: 'Clearbit',
    provider: 'clearbit',
    logo: 'Search',
    status: 'connected',
    description: 'Contact enrichment data synced automatically',
    lastSync: '1 hour ago',
    mtdCost: 12840
  },
  {
    id: 'int_003',
    name: 'Apollo.io',
    provider: 'apollo',
    logo: 'Users',
    status: 'not_connected',
    description: 'Connect to sync prospecting costs',
    lastSync: null
  },
  {
    id: 'int_004',
    name: 'Salesforce',
    provider: 'salesforce',
    logo: 'Database',
    status: 'not_connected',
    description: 'Connect to sync CRM data and pipeline',
    lastSync: null
  },
  {
    id: 'int_005',
    name: 'HubSpot',
    provider: 'hubspot',
    logo: 'Mail',
    status: 'not_connected',
    description: 'Connect to sync marketing automation costs',
    lastSync: null
  },
  {
    id: 'int_006',
    name: 'Manual CSV Upload',
    provider: 'csv',
    logo: 'Upload',
    status: 'connected',
    description: 'Upload cost data manually for any source',
    lastSync: 'Jan 5, 2025',
    recordCount: 3421
  }
]

// ============ SUMMARY STATS ============

export const segmentSummaryStats = {
  totalSegments: 8,
  totalCustomers: 2847,
  avgGrossMargin: 62,
  atRiskSegments: 3,
  unprofitableAccounts: 127,
  unprofitableCustomerDays30d: 2847,
  unprofitableCustomerDaysTrend: -12
}

export const automationSummaryStats = {
  totalAutomations: 6,
  activeAutomations: 4,
  triggeredThisWeek: 127,
  actionsExecuted: 312
}

export const experimentSummaryStats = {
  activeExperiments: 5,
  totalAccountsInTests: 703,
  avgMarginLift: 4.2,
  guardrailViolations: 2
}

// ============ SIMULATIONS ============

export const simulations: Simulation[] = [
  {
    id: 'sim_001',
    name: 'Q1 2026 Enrichment Repricing',
    segmentId: 'segment_003',
    segmentName: 'Meeting Machines',
    status: 'completed',
    timeRange: {
      start: '2024-10-01',
      end: '2024-12-31'
    },
    scenarios: [
      {
        id: 'scen_baseline',
        name: 'Baseline (Current)',
        isBaseline: true,
        pricingChange: null,
        results: {
          totalRevenue: 234000,
          avgMargin: 78,
          churnRiskCount: 2,
          customerCount: 156
        }
      },
      {
        id: 'scen_001',
        name: '10% Increase',
        isBaseline: false,
        pricingChange: { type: 'percentage_increase', value: 10, label: '+10%' },
        results: {
          totalRevenue: 251000,
          avgMargin: 81,
          churnRiskCount: 5,
          customerCount: 156
        }
      },
      {
        id: 'scen_002',
        name: '15% Increase',
        isBaseline: false,
        pricingChange: { type: 'percentage_increase', value: 15, label: '+15%' },
        results: {
          totalRevenue: 262000,
          avgMargin: 83,
          churnRiskCount: 12,
          customerCount: 156
        }
      }
    ],
    segmentPreview: {
      customerCount: 156,
      totalMrr: 234000,
      avgMargin: 78
    },
    summaryTable: [
      {
        scenarioId: 'scen_baseline',
        scenarioName: 'Baseline (Current)',
        revenue: 234000,
        revenueChange: 0,
        revenuePctChange: 0,
        margin: 78,
        marginChange: 0,
        churnRiskCount: 2,
        badges: ['lowest_risk']
      },
      {
        scenarioId: 'scen_001',
        scenarioName: '10% Increase',
        revenue: 251000,
        revenueChange: 17000,
        revenuePctChange: 7.3,
        margin: 81,
        marginChange: 3,
        churnRiskCount: 5,
        badges: ['best_margin']
      },
      {
        scenarioId: 'scen_002',
        scenarioName: '15% Increase',
        revenue: 262000,
        revenueChange: 28000,
        revenuePctChange: 12.0,
        margin: 83,
        marginChange: 5,
        churnRiskCount: 12,
        badges: ['highest_revenue']
      }
    ],
    customerImpacts: [
      {
        customerId: 'cust_1',
        customerName: 'Outbound.io',
        currentMrr: 12500,
        scenarioMrrs: {
          scen_baseline: 12500,
          scen_001: 13750,
          scen_002: 14375
        },
        changePercent: 15,
        currentMargin: 79,
        newMargin: 85,
        churnRisk: 'medium',
        contractType: 'annual'
      },
      {
        customerId: 'cust_2',
        customerName: 'PipelineAI',
        currentMrr: 3800,
        scenarioMrrs: {
          scen_baseline: 3800,
          scen_001: 4180,
          scen_002: 4370
        },
        changePercent: 15,
        currentMargin: 9,
        newMargin: 14,
        churnRisk: 'high',
        contractType: 'monthly'
      },
      {
        customerId: 'cust_3',
        customerName: 'ProspectLab',
        currentMrr: 1800,
        scenarioMrrs: {
          scen_baseline: 1800,
          scen_001: 1980,
          scen_002: 2070
        },
        changePercent: 15,
        currentMargin: 69,
        newMargin: 74,
        churnRisk: 'low',
        contractType: 'annual'
      },
      {
        customerId: 'cust_4',
        customerName: 'DealFlow',
        currentMrr: 6200,
        scenarioMrrs: {
          scen_baseline: 6200,
          scen_001: 6820,
          scen_002: 7130
        },
        changePercent: 15,
        currentMargin: 62,
        newMargin: 67,
        churnRisk: 'medium',
        contractType: 'annual'
      },
      {
        customerId: 'cust_5',
        customerName: 'SalesForge',
        currentMrr: 4400,
        scenarioMrrs: {
          scen_baseline: 4400,
          scen_001: 4840,
          scen_002: 5060
        },
        changePercent: 15,
        currentMargin: 28,
        newMargin: 33,
        churnRisk: 'high',
        contractType: 'monthly'
      }
    ],
    winningScenarioId: 'scen_001',
    createdAt: '2024-11-15',
    runAt: '2024-11-15',
    // Margin-aware simulation data
    confidenceScore: 82,
    keyInsight: 'Converting Lead Scoring from free to $0.01/use eliminates $4.1K monthly loss',
    marginImpact: {
      baselineRevenue: 234000,
      scenarioRevenue: 272400,
      revenueDelta: 38400,
      revenueDeltaPct: 16.4,
      baselineCost: 81600,
      scenarioCost: 81600,
      baselineMargin: 152400,
      scenarioMargin: 190800,
      marginDelta: 38400,
      baselineMarginPct: 65,
      scenarioMarginPct: 68,
      marginPctDelta: 3
    },
    featureAnalysis: [
      {
        featureKey: 'export_contacts',
        featureName: 'Contact Export',
        currentPrice: 0,
        newPrice: 0.02,
        volume: 12400,
        cost: 0.01,
        currentRevenue: 0,
        newRevenue: 248,
        currentMargin: -124,
        newMargin: 124,
        marginDelta: 248,
        status: 'negative'
      },
      {
        featureKey: 'lead_scoring',
        featureName: 'Lead Scoring',
        currentPrice: 0,
        newPrice: 0.01,
        volume: 89000,
        cost: 0.0024,
        currentRevenue: 0,
        newRevenue: 890,
        currentMargin: -213.6,
        newMargin: 676.4,
        marginDelta: 890,
        status: 'negative'
      },
      {
        featureKey: 'proposal_generation',
        featureName: 'Proposal Generation',
        currentPrice: 0.05,
        newPrice: 0.07,
        volume: 4200,
        cost: 0.02,
        currentRevenue: 210,
        newRevenue: 294,
        currentMargin: 126,
        newMargin: 210,
        marginDelta: 84,
        status: 'profitable'
      },
      {
        featureKey: 'api_calls',
        featureName: 'API Requests',
        currentPrice: 0.001,
        newPrice: 0.0012,
        volume: 2100000,
        cost: 0.0003,
        currentRevenue: 2100,
        newRevenue: 2520,
        currentMargin: 1470,
        newMargin: 1890,
        marginDelta: 420,
        status: 'high'
      },
      {
        featureKey: 'campaign_analytics',
        featureName: 'Campaign Analytics',
        currentPrice: 0,
        newPrice: 0.15,
        volume: 1800,
        cost: 0.08,
        currentRevenue: 0,
        newRevenue: 270,
        currentMargin: -144,
        newMargin: 126,
        marginDelta: 270,
        status: 'negative'
      }
    ]
  },
  {
    id: 'sim_002',
    name: 'Enterprise Tier Restructure',
    segmentId: 'segment_006',
    segmentName: 'Enterprise Accounts',
    status: 'running',
    timeRange: {
      start: '2024-09-01',
      end: '2024-11-30'
    },
    scenarios: [
      {
        id: 'scen_baseline',
        name: 'Baseline (Current)',
        isBaseline: true,
        pricingChange: null
      },
      {
        id: 'scen_003',
        name: '$299/seat flat',
        isBaseline: false,
        pricingChange: { type: 'flat_monthly', value: 299, label: '$299/seat/mo' }
      }
    ],
    segmentPreview: {
      customerCount: 23,
      totalMrr: 287500,
      avgMargin: 72
    },
    createdAt: '2024-11-20',
    runAt: '2024-11-20'
  },
  {
    id: 'sim_003',
    name: 'Starter Plan Optimization',
    segmentId: null,
    segmentName: 'All Customers',
    status: 'draft',
    timeRange: {
      start: '2024-11-01',
      end: '2024-12-31'
    },
    scenarios: [
      {
        id: 'scen_baseline',
        name: 'Baseline (Current)',
        isBaseline: true,
        pricingChange: null
      }
    ],
    segmentPreview: {
      customerCount: 342,
      totalMrr: 485000,
      avgMargin: 65
    },
    createdAt: '2024-11-22'
  }
]

export const simulationSummaryStats = {
  total: 3,
  draft: 1,
  running: 1,
  completed: 1,
  rolledOut: 0
}

export const integrationSummaryStats = {
  lastUpload: 'Jan 7, 2025',
  costRecords: 12847,
  coverage: 94,
  dataQuality: 'Good',
  missingAccounts: 171
}

// ============ MARGINS / ACCOUNTS ============

export const customers: Customer[] = [
  {
    id: 'cust_1',
    name: 'Outbound.io',
    email: 'billing@outbound.io',
    plan: 'Enterprise',
    mrr: 12500,
    seats: 150,
    customerSince: 'Mar 2024',
    margin: 0.79,
    marginStatus: 'healthy',
    marginTrend: 'stable',
    marginTimeseries: [
      { date: 'Dec 1', value: 0.78 },
      { date: 'Dec 8', value: 0.77 },
      { date: 'Dec 15', value: 0.79 },
      { date: 'Dec 22', value: 0.78 },
      { date: 'Dec 29', value: 0.79 },
      { date: 'Jan 5', value: 0.79 }
    ],
    costs: { inference: 2100, storage: 180, compute: 320, total: 2600 },
    costTimeseries: [
      { date: 'Dec 1', value: 2400 },
      { date: 'Dec 8', value: 2550 },
      { date: 'Dec 15', value: 2480 },
      { date: 'Dec 22', value: 2620 },
      { date: 'Dec 29', value: 2580 },
      { date: 'Jan 5', value: 2600 }
    ],
    usage: {
      enrichment: { calls: 45000, cost: 890, limit: 50000 },
      sequences: { calls: 120000, cost: 1100, limit: 200000 },
      meetings: { calls: 8000, cost: 110, limit: 10000 }
    },
    usageTrend: 'stable',
    usageTimeseries: [
      { date: 'Dec 1', value: 165000 },
      { date: 'Dec 8', value: 168000 },
      { date: 'Dec 15', value: 170000 },
      { date: 'Dec 22', value: 172000 },
      { date: 'Dec 29', value: 171000 },
      { date: 'Jan 5', value: 173000 }
    ],
    spendingAlerts: {
      thresholds: [50, 80, 95, 100],
      enabledAlerts: [50, 80, 95]
    },
    costPerAction: 0.0015,
    actionsLast30d: 173000,
    modelMixExpensive: 0.12,
    revenuePerAction: 0.0723,
    segments: ['high_value', 'enterprise'],
    inSegmentSince: 'Oct 2024'
  },
  {
    id: 'cust_2',
    name: 'PipelineAI',
    email: 'admin@pipelineai.io',
    plan: 'Scale',
    mrr: 3800,
    seats: 45,
    customerSince: 'Jun 2024',
    margin: 0.09,
    marginStatus: 'underwater',
    marginTrend: 'declining',
    marginTimeseries: [
      { date: 'Dec 1', value: 0.45 },
      { date: 'Dec 8', value: 0.37 },
      { date: 'Dec 15', value: 0.26 },
      { date: 'Dec 22', value: 0.18 },
      { date: 'Dec 29', value: 0.13 },
      { date: 'Jan 5', value: 0.09 }
    ],
    costs: { inference: 3200, storage: 90, compute: 180, total: 3470 },
    costTimeseries: [
      { date: 'Dec 1', value: 2100 },
      { date: 'Dec 8', value: 2400 },
      { date: 'Dec 15', value: 2800 },
      { date: 'Dec 22', value: 3100 },
      { date: 'Dec 29', value: 3300 },
      { date: 'Jan 5', value: 3470 }
    ],
    usage: {
      enrichment: { calls: 12000, cost: 240, limit: 15000 },
      sequences: { calls: 380000, cost: 2800, limit: 400000 },
      meetings: { calls: 5000, cost: 160, limit: 6000 }
    },
    usageTrend: 'improving',
    usageTimeseries: [
      { date: 'Dec 1', value: 320000 },
      { date: 'Dec 8', value: 345000 },
      { date: 'Dec 15', value: 365000 },
      { date: 'Dec 22', value: 380000 },
      { date: 'Dec 29', value: 390000 },
      { date: 'Jan 5', value: 397000 }
    ],
    spendingAlerts: {
      thresholds: [50, 80, 95, 100],
      enabledAlerts: [80, 95, 100]
    },
    costPerAction: 0.0087,
    actionsLast30d: 397000,
    modelMixExpensive: 0.78,
    revenuePerAction: 0.0096,
    segments: ['high_usage', 'at_risk'],
    inSegmentSince: 'Nov 2024'
  },
  {
    id: 'cust_3',
    name: 'ProspectLab',
    email: 'team@prospectlab.ai',
    plan: 'Growth',
    mrr: 1800,
    seats: 12,
    customerSince: 'Sep 2024',
    margin: 0.69,
    marginStatus: 'at_risk',
    marginTrend: 'improving',
    marginTimeseries: [
      { date: 'Dec 1', value: 0.62 },
      { date: 'Dec 8', value: 0.64 },
      { date: 'Dec 15', value: 0.66 },
      { date: 'Dec 22', value: 0.68 },
      { date: 'Dec 29', value: 0.69 },
      { date: 'Jan 5', value: 0.69 }
    ],
    costs: { inference: 420, storage: 45, compute: 85, total: 550 },
    costTimeseries: [
      { date: 'Dec 1', value: 680 },
      { date: 'Dec 8', value: 640 },
      { date: 'Dec 15', value: 610 },
      { date: 'Dec 22', value: 580 },
      { date: 'Dec 29', value: 560 },
      { date: 'Jan 5', value: 550 }
    ],
    usage: {
      enrichment: { calls: 8000, cost: 160, limit: 25000 },
      sequences: { calls: 25000, cost: 220, limit: 75000 },
      meetings: { calls: 2000, cost: 40, limit: 5000 }
    },
    usageTrend: 'declining',
    usageTimeseries: [
      { date: 'Dec 1', value: 42000 },
      { date: 'Dec 8', value: 40000 },
      { date: 'Dec 15', value: 38000 },
      { date: 'Dec 22', value: 36000 },
      { date: 'Dec 29', value: 35000 },
      { date: 'Jan 5', value: 35000 }
    ],
    spendingAlerts: {
      thresholds: [50, 80, 95, 100],
      enabledAlerts: [80, 95]
    },
    costPerAction: 0.0016,
    actionsLast30d: 35000,
    modelMixExpensive: 0.15,
    revenuePerAction: 0.0514,
    segments: ['new_customer'],
    inSegmentSince: 'Sep 2024'
  },
  {
    id: 'cust_4',
    name: 'DealFlow',
    email: 'ops@dealflow.tech',
    plan: 'Enterprise',
    mrr: 8200,
    seats: 85,
    customerSince: 'Jan 2024',
    margin: 0.34,
    marginStatus: 'at_risk',
    marginTrend: 'declining',
    marginTimeseries: [
      { date: 'Dec 1', value: 0.49 },
      { date: 'Dec 8', value: 0.45 },
      { date: 'Dec 15', value: 0.41 },
      { date: 'Dec 22', value: 0.38 },
      { date: 'Dec 29', value: 0.36 },
      { date: 'Jan 5', value: 0.34 }
    ],
    costs: { inference: 4800, storage: 220, compute: 380, total: 5400 },
    costTimeseries: [
      { date: 'Dec 1', value: 4200 },
      { date: 'Dec 8', value: 4500 },
      { date: 'Dec 15', value: 4800 },
      { date: 'Dec 22', value: 5100 },
      { date: 'Dec 29', value: 5250 },
      { date: 'Jan 5', value: 5400 }
    ],
    usage: {
      enrichment: { calls: 62000, cost: 1240, limit: 100000 },
      sequences: { calls: 290000, cost: 3200, limit: 500000 },
      meetings: { calls: 12000, cost: 360, limit: 25000 }
    },
    usageTrend: 'improving',
    usageTimeseries: [
      { date: 'Dec 1', value: 310000 },
      { date: 'Dec 8', value: 330000 },
      { date: 'Dec 15', value: 345000 },
      { date: 'Dec 22', value: 355000 },
      { date: 'Dec 29', value: 360000 },
      { date: 'Jan 5', value: 364000 }
    ],
    spendingAlerts: {
      thresholds: [50, 80, 95, 100],
      enabledAlerts: [50, 80, 95]
    },
    costPerAction: 0.0148,
    actionsLast30d: 364000,
    modelMixExpensive: 0.62,
    revenuePerAction: 0.0225,
    segments: ['enterprise', 'high_usage'],
    inSegmentSince: 'Apr 2024'
  },
  {
    id: 'cust_5',
    name: 'SalesForge',
    email: 'hello@salesforge.com',
    plan: 'Growth',
    mrr: 2400,
    seats: 28,
    customerSince: 'Aug 2024',
    margin: 0.64,
    marginStatus: 'at_risk',
    marginTrend: 'stable',
    marginTimeseries: [
      { date: 'Dec 1', value: 0.65 },
      { date: 'Dec 8', value: 0.64 },
      { date: 'Dec 15', value: 0.64 },
      { date: 'Dec 22', value: 0.64 },
      { date: 'Dec 29', value: 0.64 },
      { date: 'Jan 5', value: 0.64 }
    ],
    costs: { inference: 680, storage: 60, compute: 120, total: 860 },
    costTimeseries: [
      { date: 'Dec 1', value: 840 },
      { date: 'Dec 8', value: 850 },
      { date: 'Dec 15', value: 855 },
      { date: 'Dec 22', value: 860 },
      { date: 'Dec 29', value: 858 },
      { date: 'Jan 5', value: 860 }
    ],
    usage: {
      enrichment: { calls: 15000, cost: 300, limit: 15000 },
      sequences: { calls: 35000, cost: 320, limit: 75000 },
      meetings: { calls: 3000, cost: 60, limit: 10000 }
    },
    usageTrend: 'stable',
    usageTimeseries: [
      { date: 'Dec 1', value: 52000 },
      { date: 'Dec 8', value: 53000 },
      { date: 'Dec 15', value: 52500 },
      { date: 'Dec 22', value: 53000 },
      { date: 'Dec 29', value: 53000 },
      { date: 'Jan 5', value: 53000 }
    ],
    spendingAlerts: {
      thresholds: [50, 80, 95, 100],
      enabledAlerts: [80, 95, 100]
    },
    costPerAction: 0.0128,
    actionsLast30d: 53000,
    modelMixExpensive: 0.45,
    revenuePerAction: 0.0453,
    segments: ['pro_tier'],
    inSegmentSince: 'Oct 2024'
  }
]

// Legacy alias for backwards compatibility
export const accounts = customers

export const portfolioSummary: PortfolioSummary = {
  totalMrr: 28700,
  totalCosts: 12880,
  avgMargin: 0.55,
  marginTrendPp: -3,
  customersByStatus: {
    healthy: 1,
    atRisk: 3,
    underwater: 1
  },
  mrrByStatus: {
    healthy: 12500,
    atRisk: 12400,
    underwater: 3800
  }
}

// ============ SEGMENT OPTIMIZATION SUGGESTIONS ============
// AI-powered suggestions for segments with <40 accounts

export const segmentOptimizationSuggestions: Record<string, SegmentOptimizationSuggestions> = {
  // Enrichment Power Users - 12 accounts (red, too_small)
  segment_002: {
    segmentId: 'segment_002',
    currentCount: 12,
    targetCount: 40,
    relaxRule: {
      ruleToRelax: {
        id: 'r1',
        field: 'margin_trend_30d',
        fieldLabel: 'Margin Trend (30d)',
        operator: 'at_most',
        operatorLabel: 'is at most',
        value: -20,
        valueLabel: '-20%'
      },
      suggestedChange: {
        attribute: 'margin_trend_30d',
        attributeLabel: 'Margin Trend (30d)',
        currentOperator: 'at most',
        currentValue: -20,
        suggestedOperator: 'at most',
        suggestedValue: -10
      },
      impact: {
        currentCount: 12,
        newCount: 47,
        newReadiness: 'ready',
        avgArrChange: 0.15, // +15% (higher ARR accounts included)
        avgMarginChange: 0.08, // +8% (less eroded margins)
        similarityScore: 0.86
      },
      newAccountsPreview: [
        { name: 'ReachHQ', arr: 156000, margin: 52 },
        { name: 'SDR Engine', arr: 89000, margin: 48 },
        { name: 'IntentIQ', arr: 234000, margin: 55 },
        { name: 'SignalStack', arr: 67000, margin: 44 },
        { name: 'BookMore', arr: 145000, margin: 51 }
      ]
    },
    combineSegments: [
      {
        segmentId: 'segment_007',
        segmentName: 'LinkedIn Heavy',
        segmentCount: 67,
        similarityScore: 0.78,
        combinedCount: 79,
        combinedReadiness: 'ready',
        comparison: [
          { metric: 'Avg Margin', thisValue: '38%', otherValue: '42%' },
          { metric: 'Avg MRR', thisValue: '$3,500', otherValue: '$1,168' },
          { metric: 'Margin Trend', thisValue: '-18%', otherValue: '-8%' }
        ]
      }
    ]
    // No wait for growth - this segment is stable/not growing
  },

  // Deal Closers - 23 accounts (yellow, small_sample)
  segment_006: {
    segmentId: 'segment_006',
    currentCount: 23,
    targetCount: 40,
    relaxRule: {
      ruleToRelax: {
        id: 'r2',
        field: 'seats',
        fieldLabel: 'Seats',
        operator: 'at_least',
        operatorLabel: 'is at least',
        value: 20,
        valueLabel: '20'
      },
      suggestedChange: {
        attribute: 'seats',
        attributeLabel: 'Seats',
        currentOperator: 'at least',
        currentValue: 20,
        suggestedOperator: 'at least',
        suggestedValue: 12
      },
      impact: {
        currentCount: 23,
        newCount: 48,
        newReadiness: 'ready',
        avgArrChange: -0.12, // -12% (smaller companies included)
        avgMarginChange: -0.03, // -3%
        similarityScore: 0.91
      },
      newAccountsPreview: [
        { name: 'CadenceAI', arr: 78000, margin: 68 },
        { name: 'EnrichPro', arr: 92000, margin: 71 },
        { name: 'ContactLoop', arr: 64000, margin: 65 },
        { name: 'MeetingOS', arr: 85000, margin: 70 },
        { name: 'DialPad Pro', arr: 71000, margin: 67 }
      ]
    },
    combineSegments: [
      {
        segmentId: 'segment_003',
        segmentName: 'Meeting Machines',
        segmentCount: 156,
        similarityScore: 0.82,
        combinedCount: 179,
        combinedReadiness: 'ready',
        comparison: [
          { metric: 'Avg Margin', thisValue: '72%', otherValue: '78%' },
          { metric: 'Avg MRR', thisValue: '$12,500', otherValue: '$1,500' },
          { metric: 'Expansion Signals', thisValue: 'High', otherValue: 'Very High' }
        ]
      }
    ],
    waitForGrowth: {
      currentGrowthRate: 2.8, // ~3 accounts per month
      weeksUntilReady: 6,
      projectedDate: '2025-02-20',
      confidence: 'medium'
    }
  }
}

// ============ USAGE EVENTS ============

// Generate timestamps for recent events
const now = new Date()
const generateTimestamp = (minutesAgo: number) => {
  const date = new Date(now.getTime() - minutesAgo * 60 * 1000)
  return date.toISOString()
}

export const usageEvents: UsageEvent[] = [
  {
    id: 'evt_a1b2c3d4',
    timestamp: generateTimestamp(1),
    eventName: 'feature_used',
    customerId: 'outbound-io',
    customerName: 'Outbound.io',
    customerReferenceId: 'outbound-io',
    properties: { featureKey: 'api_requests', quantity: 1, endpoint: 'process' },
    status: 'active',
    featureKey: 'api_requests',
    costAmount: 0.001,
    costUnit: 'usd'
  },
  {
    id: 'evt_e5f6g7h8',
    timestamp: generateTimestamp(2),
    eventName: 'enrichment_executed',
    customerId: 'prospectlab',
    customerName: 'ProspectLab',
    customerReferenceId: 'prospectlab',
    properties: {
      featureKey: 'lead_scoring',
      quantity: 1,
      ai: { provider: 'openai', model: 'gpt-4.1-mini', inputTokens: 1850, outputTokens: 420 }
    },
    status: 'active',
    featureKey: 'lead_scoring',
    costAmount: 0.00053,
    costUnit: 'usd'
  },
  {
    id: 'evt_i9j0k1l2',
    timestamp: generateTimestamp(3),
    eventName: 'feature_used',
    customerId: 'pipelineai',
    customerName: 'PipelineAI',
    customerReferenceId: 'pipelineai',
    properties: { featureKey: 'export_contacts', quantity: 1 },
    status: 'invoiced',
    featureKey: 'export_contacts',
    costAmount: 0.01,
    costUnit: 'usd',
    invoiceId: 'INV-0045'
  },
  {
    id: 'evt_m3n4o5p6',
    timestamp: generateTimestamp(5),
    eventName: 'feature_used',
    customerId: 'revenuebot',
    customerName: 'RevenueBot',
    customerReferenceId: 'revenuebot',
    properties: { featureKey: 'proposal_generation', quantity: 2 },
    status: 'active',
    featureKey: 'proposal_generation',
    costAmount: 6,
    costUnit: 'credits'
  },
  {
    id: 'evt_q7r8s9t0',
    timestamp: generateTimestamp(7),
    eventName: 'feature_used',
    customerId: 'dealflow',
    customerName: 'DealFlow',
    customerReferenceId: 'dealflow',
    properties: { featureKey: 'api_requests', quantity: 1, endpoint: 'transfer' },
    status: 'active',
    featureKey: 'api_requests',
    costAmount: 0.001,
    costUnit: 'usd'
  },
  {
    id: 'evt_u1v2w3x4',
    timestamp: generateTimestamp(10),
    eventName: 'enrichment_executed',
    customerId: 'outbound-io',
    customerName: 'Outbound.io',
    customerReferenceId: 'outbound-io',
    properties: {
      featureKey: 'lead_scoring',
      quantity: 1,
      ai: { provider: 'openai', model: 'gpt-4.1-mini', inputTokens: 3200, outputTokens: 850 }
    },
    status: 'attributed',
    featureKey: 'lead_scoring',
    costAmount: 0.00099,
    costUnit: 'usd'
  },
  {
    id: 'evt_y5z6a7b8',
    timestamp: generateTimestamp(12),
    eventName: 'feature_used',
    customerId: 'prospectlab',
    customerName: 'ProspectLab',
    customerReferenceId: 'prospectlab',
    properties: { featureKey: 'api_requests', quantity: 1, endpoint: 'validate' },
    status: 'active',
    featureKey: 'api_requests',
    costAmount: 0.001,
    costUnit: 'usd'
  },
  {
    id: 'evt_c9d0e1f2',
    timestamp: generateTimestamp(15),
    eventName: 'feature_used',
    customerId: 'pipelineai',
    customerName: 'PipelineAI',
    customerReferenceId: 'pipelineai',
    properties: { featureKey: 'sequence_runs', quantity: 0.75, instance_type: 'gpu-small' },
    status: 'active',
    featureKey: 'sequence_runs',
    costAmount: 0.75,
    costUnit: 'usd'
  },
  {
    id: 'evt_g3h4i5j6',
    timestamp: generateTimestamp(18),
    eventName: 'feature_used',
    customerId: 'revenuebot',
    customerName: 'RevenueBot',
    customerReferenceId: 'revenuebot',
    properties: { featureKey: 'api_requests', quantity: 1, endpoint: 'process' },
    status: 'active',
    featureKey: 'api_requests',
    costAmount: 0.001,
    costUnit: 'usd'
  },
  {
    id: 'evt_k7l8m9n0',
    timestamp: generateTimestamp(22),
    eventName: 'feature_used',
    customerId: 'dealflow',
    customerName: 'DealFlow',
    customerReferenceId: 'dealflow',
    properties: { featureKey: 'proposal_generation', quantity: 5 },
    status: 'active',
    featureKey: 'proposal_generation',
    costAmount: 15,
    costUnit: 'credits'
  }
]

export const eventStats = {
  totalIngested: 419066813,
  last7Days: [58243102, 61245891, 59872456, 60123789, 62456123, 58912345, 57213107],
  updateFrequency: 'just now'
}

// ============ PRICING PLANS ============

export const pricingPlans: Plan[] = [
  {
    id: 'plan_starter',
    name: 'Starter',
    externalId: 'starter',
    status: 'active',
    currency: 'USD',
    billingCycle: 'monthly',
    netTerms: 'Due on issue',
    billingMode: 'in_advance',
    subscriptionCount: 45,
    mrr: 8955,
    createdAt: '2025-10-15',
    versions: [
      {
        version: 1,
        isDefault: false,
        status: 'archived',
        publishedAt: '2025-10-15',
        usageBasedFees: [
          {
            id: 'fee_starter_api_v1',
            component: 'API requests',
            featureId: 'feat_metered_1',
            featureName: 'API Requests',
            billingCycle: 'monthly',
            priceModel: 'per_unit',
            unitPrice: 0.01
          }
        ],
        fixedFees: [
          {
            id: 'fee_starter_platform_v1',
            component: 'Platform access',
            billingCycle: 'monthly',
            amount: 49,
            quantity: 1
          }
        ]
      },
      {
        version: 2,
        isDefault: true,
        status: 'published',
        publishedAt: '2025-12-01',
        usageBasedFees: [
          {
            id: 'fee_starter_api_v2',
            component: 'API requests',
            featureId: 'feat_metered_1',
            featureName: 'API Requests',
            billingCycle: 'monthly',
            priceModel: 'graduated',
            tiers: [
              { firstUnit: 0, lastUnit: 10000, unitPrice: 0 },
              { firstUnit: 10001, lastUnit: 50000, unitPrice: 0.008 },
              { firstUnit: 50001, lastUnit: null, unitPrice: 0.005 }
            ]
          },
          {
            id: 'fee_starter_tokens_v2',
            component: 'Token usage',
            featureId: 'feat_metered_3',
            featureName: 'Tokens Consumed',
            billingCycle: 'monthly',
            priceModel: 'per_unit',
            unitPrice: 0.0008
          }
        ],
        fixedFees: [
          {
            id: 'fee_starter_platform_v2',
            component: 'Platform access',
            billingCycle: 'monthly',
            amount: 99,
            quantity: 1
          }
        ]
      }
    ],
    activity: [
      { date: '2025-12-01', action: 'Version 2 set as Default' },
      { date: '2025-12-01', action: 'Version 2 published' },
      { date: '2025-10-15', action: 'Plan created' }
    ]
  },
  {
    id: 'plan_growth',
    name: 'Growth',
    externalId: 'growth',
    status: 'active',
    currency: 'USD',
    billingCycle: 'monthly',
    netTerms: 'Due on issue',
    billingMode: 'in_advance',
    subscriptionCount: 28,
    mrr: 13972,
    createdAt: '2025-09-01',
    versions: [
      {
        version: 1,
        isDefault: false,
        status: 'archived',
        publishedAt: '2025-09-01',
        usageBasedFees: [
          {
            id: 'fee_growth_api_v1',
            component: 'API requests',
            featureId: 'feat_metered_1',
            featureName: 'API Requests',
            billingCycle: 'monthly',
            priceModel: 'per_unit',
            unitPrice: 0.02
          }
        ],
        fixedFees: [
          {
            id: 'fee_growth_platform_v1',
            component: 'Platform access',
            billingCycle: 'monthly',
            amount: 199,
            quantity: 1
          }
        ]
      },
      {
        version: 2,
        isDefault: false,
        status: 'archived',
        publishedAt: '2025-11-01',
        usageBasedFees: [
          {
            id: 'fee_growth_api_v2',
            component: 'API requests',
            featureId: 'feat_metered_1',
            featureName: 'API Requests',
            billingCycle: 'monthly',
            priceModel: 'matrix',
            matrixDimensions: [
              {
                key: 'endpoint',
                values: [
                  { value: 'auth', unitPrice: 0.03 },
                  { value: 'process', unitPrice: 0.15 },
                  { value: 'transfer', unitPrice: 0.5 },
                  { value: 'validate', unitPrice: 0.003 }
                ],
                defaultPrice: 0.01
              }
            ],
            defaultUnitPrice: 0.01
          },
          {
            id: 'fee_growth_tokens_v2',
            component: 'Token usage',
            featureId: 'feat_metered_3',
            featureName: 'Tokens Consumed',
            billingCycle: 'monthly',
            priceModel: 'per_unit',
            unitPrice: 0.001
          }
        ],
        fixedFees: [
          {
            id: 'fee_growth_platform_v2',
            component: 'Platform access',
            billingCycle: 'monthly',
            amount: 199,
            quantity: 1
          }
        ]
      },
      {
        version: 3,
        isDefault: true,
        status: 'published',
        publishedAt: '2026-01-05',
        usageBasedFees: [
          {
            id: 'fee_growth_api_v3',
            component: 'API requests',
            featureId: 'feat_metered_1',
            featureName: 'API Requests',
            billingCycle: 'monthly',
            priceModel: 'matrix',
            matrixDimensions: [
              {
                key: 'endpoint',
                values: [
                  { value: 'auth', unitPrice: 0.05 },
                  { value: 'process', unitPrice: 0.25 },
                  { value: 'transfer', unitPrice: 0.75 },
                  { value: 'validate', unitPrice: 0.005 }
                ],
                defaultPrice: 0.01
              }
            ],
            defaultUnitPrice: 0.01
          },
          {
            id: 'fee_growth_tokens_v3',
            component: 'Token usage',
            featureId: 'feat_metered_3',
            featureName: 'Tokens Consumed',
            billingCycle: 'monthly',
            priceModel: 'per_unit',
            unitPrice: 0.001
          },
          {
            id: 'fee_growth_storage_v3',
            component: 'Storage',
            featureId: 'feat_metered_2',
            featureName: 'Storage (GB)',
            billingCycle: 'monthly',
            priceModel: 'volume',
            tiers: [
              { firstUnit: 0, lastUnit: 10, unitPrice: 0 },
              { firstUnit: 0, lastUnit: 100, unitPrice: 2.0 },
              { firstUnit: 0, lastUnit: null, unitPrice: 1.5 }
            ]
          }
        ],
        fixedFees: [
          {
            id: 'fee_growth_platform_v3',
            component: 'Platform access',
            billingCycle: 'monthly',
            amount: 199,
            quantity: 1
          }
        ]
      }
    ],
    activity: [
      { date: '2026-01-05', action: 'Version 3 set as Default' },
      { date: '2026-01-05', action: 'Version 3 published' },
      { date: '2025-11-01', action: 'Version 2 published' },
      { date: '2025-09-01', action: 'Plan created' }
    ]
  },
  {
    id: 'plan_enterprise',
    name: 'Enterprise',
    externalId: 'enterprise',
    status: 'active',
    currency: 'USD',
    billingCycle: 'annual',
    netTerms: 'Net 30',
    billingMode: 'arrears',
    subscriptionCount: 12,
    mrr: 5773,
    createdAt: '2025-08-01',
    versions: [
      {
        version: 1,
        isDefault: false,
        status: 'archived',
        publishedAt: '2025-08-01',
        usageBasedFees: [
          {
            id: 'fee_ent_api_v1',
            component: 'API requests',
            featureId: 'feat_metered_1',
            featureName: 'API Requests',
            billingCycle: 'monthly',
            priceModel: 'per_unit',
            unitPrice: 0.03
          }
        ],
        fixedFees: [
          {
            id: 'fee_ent_platform_v1',
            component: 'Platform access',
            billingCycle: 'monthly',
            amount: 499,
            quantity: 1
          }
        ]
      },
      {
        version: 2,
        isDefault: true,
        status: 'published',
        publishedAt: '2025-12-15',
        usageBasedFees: [
          {
            id: 'fee_ent_api_v2',
            component: 'API requests',
            featureId: 'feat_metered_1',
            featureName: 'API Requests',
            billingCycle: 'monthly',
            priceModel: 'matrix',
            matrixDimensions: [
              {
                key: 'endpoint',
                values: [
                  { value: 'auth', unitPrice: 0.04 },
                  { value: 'process', unitPrice: 0.2 },
                  { value: 'transfer', unitPrice: 0.6 },
                  { value: 'validate', unitPrice: 0.004 }
                ],
                defaultPrice: 0.01
              }
            ],
            defaultUnitPrice: 0.01
          },
          {
            id: 'fee_ent_tokens_v2',
            component: 'Token usage',
            featureId: 'feat_metered_3',
            featureName: 'Tokens Consumed',
            billingCycle: 'monthly',
            priceModel: 'graduated',
            tiers: [
              { firstUnit: 0, lastUnit: 1000000, unitPrice: 0.002 },
              { firstUnit: 1000001, lastUnit: 10000000, unitPrice: 0.0015 },
              { firstUnit: 10000001, lastUnit: null, unitPrice: 0.001 }
            ]
          },
          {
            id: 'fee_ent_storage_v2',
            component: 'Storage',
            featureId: 'feat_metered_2',
            featureName: 'Storage (GB)',
            billingCycle: 'monthly',
            priceModel: 'volume',
            tiers: [
              { firstUnit: 0, lastUnit: 50, unitPrice: 2.0 },
              { firstUnit: 0, lastUnit: 500, unitPrice: 1.5 },
              { firstUnit: 0, lastUnit: null, unitPrice: 1.0 }
            ]
          },
          {
            id: 'fee_ent_compute_v2',
            component: 'Compute hours',
            featureId: 'feat_metered_4',
            featureName: 'Compute Hours',
            billingCycle: 'monthly',
            priceModel: 'package',
            packageSize: 10,
            packagePrice: 4.5
          }
        ],
        fixedFees: [
          {
            id: 'fee_ent_platform_v2',
            component: 'Platform access',
            billingCycle: 'monthly',
            amount: 499,
            quantity: 1
          },
          {
            id: 'fee_ent_support_v2',
            component: 'Priority support',
            billingCycle: 'monthly',
            amount: 200,
            quantity: 1
          }
        ]
      },
      {
        version: 3,
        isDefault: false,
        status: 'draft',
        usageBasedFees: [
          {
            id: 'fee_ent_api_v3',
            component: 'API requests',
            featureId: 'feat_metered_1',
            featureName: 'API Requests',
            billingCycle: 'monthly',
            priceModel: 'matrix',
            matrixDimensions: [
              {
                key: 'endpoint',
                values: [
                  { value: 'auth', unitPrice: 0.03 },
                  { value: 'process', unitPrice: 0.15 },
                  { value: 'transfer', unitPrice: 0.45 },
                  { value: 'validate', unitPrice: 0.003 }
                ],
                defaultPrice: 0.008
              }
            ],
            defaultUnitPrice: 0.008
          },
          {
            id: 'fee_ent_tokens_v3',
            component: 'Token usage',
            featureId: 'feat_metered_3',
            featureName: 'Tokens Consumed',
            billingCycle: 'monthly',
            priceModel: 'graduated',
            tiers: [
              { firstUnit: 0, lastUnit: 2000000, unitPrice: 0.0018 },
              { firstUnit: 2000001, lastUnit: 20000000, unitPrice: 0.0012 },
              { firstUnit: 20000001, lastUnit: null, unitPrice: 0.0008 }
            ]
          },
          {
            id: 'fee_ent_storage_v3',
            component: 'Storage',
            featureId: 'feat_metered_2',
            featureName: 'Storage (GB)',
            billingCycle: 'monthly',
            priceModel: 'volume',
            tiers: [
              { firstUnit: 0, lastUnit: 100, unitPrice: 1.8 },
              { firstUnit: 0, lastUnit: 1000, unitPrice: 1.2 },
              { firstUnit: 0, lastUnit: null, unitPrice: 0.8 }
            ]
          },
          {
            id: 'fee_ent_compute_v3',
            component: 'Compute hours',
            featureId: 'feat_metered_4',
            featureName: 'Compute Hours',
            billingCycle: 'monthly',
            priceModel: 'package',
            packageSize: 10,
            packagePrice: 4.0
          }
        ],
        fixedFees: [
          {
            id: 'fee_ent_platform_v3',
            component: 'Platform access',
            billingCycle: 'monthly',
            amount: 449,
            quantity: 1
          },
          {
            id: 'fee_ent_support_v3',
            component: 'Priority support',
            billingCycle: 'monthly',
            amount: 150,
            quantity: 1
          }
        ]
      }
    ],
    activity: [
      { date: '2026-01-10', action: 'Version 3 created (draft)' },
      { date: '2025-12-15', action: 'Version 2 set as Default' },
      { date: '2025-12-15', action: 'Version 2 published' },
      { date: '2025-08-01', action: 'Plan created' }
    ]
  }
]

export const planSubscriptions: PlanSubscription[] = [
  {
    id: 'sub_001',
    customerId: 'outbound-io',
    customerName: 'Outbound.io',
    customerEmail: 'billing@outbound.io',
    planId: 'plan_starter',
    planName: 'Starter',
    startDate: '2025-09-15',
    version: 2,
    billingCycle: 'monthly',
    currentPeriodStart: '2026-01-01',
    currentPeriodEnd: '2026-01-31',
    nextInvoiceDate: '2026-02-01',
    hasOverrides: false,
    status: 'active',
    activity: [
      { date: '2025-09-15', action: 'Subscription created', details: 'Started on Starter plan v2' },
      { date: '2025-11-01', action: 'Plan version upgraded', details: 'Migrated from v1 to v2' }
    ]
  },
  {
    id: 'sub_002',
    customerId: 'prospectlab',
    customerName: 'ProspectLab',
    customerEmail: 'finance@prospectlab.ai',
    planId: 'plan_growth',
    planName: 'Growth',
    startDate: '2025-10-01',
    version: 2,
    billingCycle: 'monthly',
    currentPeriodStart: '2026-01-01',
    currentPeriodEnd: '2026-01-31',
    nextInvoiceDate: '2026-02-01',
    hasOverrides: false,
    status: 'active',
    activity: [
      { date: '2025-10-01', action: 'Subscription created', details: 'Started on Growth plan v2' }
    ]
  },
  {
    id: 'sub_003',
    customerId: 'pipelineai',
    customerName: 'PipelineAI',
    customerEmail: 'ap@pipelineai.io',
    planId: 'plan_enterprise',
    planName: 'Enterprise',
    startDate: '2025-08-20',
    version: 3,
    billingCycle: 'annual',
    currentPeriodStart: '2025-08-20',
    currentPeriodEnd: '2026-08-19',
    nextInvoiceDate: '2026-08-20',
    hasOverrides: true,
    overrides: [
      {
        featureId: 'api_requests',
        featureName: 'API Requests',
        originalPrice: 0.001,
        overridePrice: 0.0008,
        reason: 'Volume discount - enterprise deal'
      },
      {
        featureId: 'proposal_generation',
        featureName: 'Proposal Generation',
        originalPrice: 0.05,
        overridePrice: 0.04,
        reason: 'Negotiated rate'
      }
    ],
    status: 'active',
    activity: [
      {
        date: '2025-08-20',
        action: 'Subscription created',
        details: 'Started on Enterprise plan v3'
      },
      {
        date: '2025-08-20',
        action: 'Overrides applied',
        details: 'Custom pricing for API requests and PDF generation'
      },
      {
        date: '2025-12-01',
        action: 'Usage alert triggered',
        details: 'Reached 80% of API request allocation'
      }
    ]
  },
  {
    id: 'sub_004',
    customerId: 'revenuebot',
    customerName: 'RevenueBot',
    customerEmail: 'billing@revenuebot.ai',
    planId: 'plan_starter',
    planName: 'Starter',
    startDate: '2025-07-10',
    version: 2,
    billingCycle: 'monthly',
    currentPeriodStart: '2026-01-10',
    currentPeriodEnd: '2026-02-09',
    nextInvoiceDate: '2026-02-10',
    hasOverrides: false,
    status: 'active',
    activity: [
      { date: '2025-07-10', action: 'Subscription created', details: 'Started on Starter plan v1' },
      { date: '2025-10-15', action: 'Plan version upgraded', details: 'Migrated from v1 to v2' }
    ]
  },
  {
    id: 'sub_005',
    customerId: 'dealflow',
    customerName: 'DealFlow',
    customerEmail: 'accounts@dealflow.tech',
    planId: 'plan_enterprise',
    planName: 'Enterprise',
    startDate: '2025-11-05',
    version: 3,
    billingCycle: 'quarterly',
    currentPeriodStart: '2025-11-05',
    currentPeriodEnd: '2026-02-04',
    nextInvoiceDate: '2026-02-05',
    hasOverrides: false,
    status: 'active',
    activity: [
      {
        date: '2025-11-05',
        action: 'Subscription created',
        details: 'Started on Enterprise plan v3'
      }
    ]
  },
  {
    id: 'sub_006',
    customerId: 'closefast',
    customerName: 'CloseFast',
    customerEmail: 'billing@closefast.io',
    planId: 'plan_growth',
    planName: 'Growth',
    startDate: '2025-06-01',
    endDate: '2025-12-31',
    version: 2,
    billingCycle: 'monthly',
    currentPeriodStart: '2025-12-01',
    currentPeriodEnd: '2025-12-31',
    nextInvoiceDate: '2026-01-01',
    hasOverrides: false,
    status: 'canceled',
    activity: [
      { date: '2025-06-01', action: 'Subscription created', details: 'Started on Growth plan v2' },
      {
        date: '2025-12-15',
        action: 'Cancellation requested',
        details: 'Customer requested cancellation effective end of term'
      },
      { date: '2025-12-31', action: 'Subscription canceled', details: 'Subscription ended' }
    ]
  },
  {
    id: 'sub_007',
    customerId: 'coldreach',
    customerName: 'ColdReach',
    customerEmail: 'finance@coldreach.io',
    planId: 'plan_starter',
    planName: 'Starter',
    startDate: '2025-09-01',
    version: 2,
    billingCycle: 'monthly',
    currentPeriodStart: '2026-01-01',
    currentPeriodEnd: '2026-01-31',
    nextInvoiceDate: '2026-02-01',
    hasOverrides: false,
    status: 'paused',
    activity: [
      { date: '2025-09-01', action: 'Subscription created', details: 'Started on Starter plan v2' },
      {
        date: '2026-01-05',
        action: 'Subscription paused',
        details: 'Customer requested pause for 30 days'
      }
    ]
  }
]

export const planRevenueByMonth = {
  labels: ['Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec', 'Jan', 'Feb', 'Mar'],
  data: {
    starter: [1800, 2200, 2800, 3400, 4000, 4800, 5500, 6200, 7000, 8200, 10100, 12800],
    growth: [2400, 3100, 4000, 5000, 6200, 7500, 8800, 10200, 11800, 14500, 18200, 23400],
    enterprise: [800, 1200, 1800, 2500, 3200, 4000, 4800, 5800, 7000, 9000, 12000, 16200]
  }
}

// ============ INVOICES ============

export const invoices: Invoice[] = [
  {
    id: 'INV-0047',
    customerId: 'outbound-io',
    customerName: 'Outbound.io',
    customerEmail: 'billing@outbound.io',
    planId: 'plan_enterprise',
    planName: 'Enterprise',
    status: 'draft',
    amount: 8742,
    subtotal: 8742,
    tax: 0,
    invoiceDate: '2026-01-09',
    dueDate: '2026-02-08',
    billingPeriodStart: '2026-01-01',
    billingPeriodEnd: '2026-01-20',
    isFinalized: false,
    lineItems: [
      {
        id: 'li_001',
        type: 'usage_based',
        featureName: 'API Requests',
        dimension: 'auth',
        quantity: 258260,
        rate: 0.04,
        amount: 10330,
        description: 'API requests (auth)'
      },
      {
        id: 'li_002',
        type: 'usage_based',
        featureName: 'API Requests',
        dimension: 'process',
        quantity: 110475,
        rate: 0.2,
        amount: 22095,
        description: 'API requests (process)'
      },
      {
        id: 'li_003',
        type: 'usage_based',
        featureName: 'API Requests',
        dimension: 'transfer',
        quantity: 36846,
        rate: 0.6,
        amount: 22108,
        description: 'API requests (transfer)'
      },
      {
        id: 'li_004',
        type: 'usage_based',
        featureName: 'API Requests',
        dimension: 'validate',
        quantity: 147988,
        rate: 0.004,
        amount: 592,
        description: 'API requests (validate)'
      },
      {
        id: 'li_005',
        type: 'usage_based',
        featureName: 'Tokens Consumed',
        quantity: 3245000,
        rate: 0.0015,
        amount: 4868,
        description: 'Token usage'
      },
      {
        id: 'li_006',
        type: 'usage_based',
        featureName: 'Storage (GB)',
        quantity: 42,
        rate: 1.5,
        amount: 63,
        description: 'Storage'
      },
      {
        id: 'li_007',
        type: 'fixed',
        quantity: 1,
        rate: 499,
        amount: 499,
        description: 'Platform access fee'
      },
      {
        id: 'li_008',
        type: 'fixed',
        quantity: 1,
        rate: 200,
        amount: 200,
        description: 'Priority support'
      }
    ]
  },
  {
    id: 'INV-0046',
    customerId: 'prospectlab',
    customerName: 'ProspectLab',
    customerEmail: 'finance@prospectlab.ai',
    planId: 'plan_starter',
    planName: 'Starter',
    status: 'issued',
    amount: 2156,
    subtotal: 2156,
    tax: 0,
    invoiceDate: '2026-01-08',
    dueDate: '2026-01-08',
    billingPeriodStart: '2025-12-08',
    billingPeriodEnd: '2026-01-07',
    isFinalized: true,
    lineItems: [
      {
        id: 'li_101',
        type: 'usage_based',
        featureName: 'API Requests',
        quantity: 125000,
        rate: 0.008,
        amount: 1000,
        description: 'API requests (10K-50K tier)'
      },
      {
        id: 'li_102',
        type: 'usage_based',
        featureName: 'API Requests',
        quantity: 82000,
        rate: 0.005,
        amount: 410,
        description: 'API requests (50K+ tier)'
      },
      {
        id: 'li_103',
        type: 'usage_based',
        featureName: 'Tokens Consumed',
        quantity: 834000,
        rate: 0.0008,
        amount: 667,
        description: 'Token usage'
      },
      {
        id: 'li_104',
        type: 'fixed',
        quantity: 1,
        rate: 99,
        amount: 99,
        description: 'Platform access fee'
      }
    ]
  },
  {
    id: 'INV-0045',
    customerId: 'pipelineai',
    customerName: 'PipelineAI',
    customerEmail: 'accounts@pipelineai.io',
    planId: 'plan_growth',
    planName: 'Growth',
    status: 'paid',
    amount: 4891,
    subtotal: 4891,
    tax: 0,
    invoiceDate: '2026-01-05',
    dueDate: '2026-01-05',
    billingPeriodStart: '2025-12-05',
    billingPeriodEnd: '2026-01-04',
    isFinalized: true,
    lineItems: [
      {
        id: 'li_201',
        type: 'usage_based',
        featureName: 'API Requests',
        dimension: 'auth',
        quantity: 45200,
        rate: 0.05,
        amount: 2260,
        description: 'API requests (auth)'
      },
      {
        id: 'li_202',
        type: 'usage_based',
        featureName: 'API Requests',
        dimension: 'process',
        quantity: 28900,
        rate: 0.25,
        amount: 7225,
        description: 'API requests (process)'
      },
      {
        id: 'li_203',
        type: 'usage_based',
        featureName: 'Tokens Consumed',
        quantity: 1420000,
        rate: 0.001,
        amount: 1420,
        description: 'Token usage'
      },
      {
        id: 'li_204',
        type: 'usage_based',
        featureName: 'Storage (GB)',
        quantity: 28,
        rate: 2.0,
        amount: 56,
        description: 'Storage (10-100GB tier)'
      },
      {
        id: 'li_205',
        type: 'fixed',
        quantity: 1,
        rate: 199,
        amount: 199,
        description: 'Platform access fee'
      }
    ]
  },
  {
    id: 'INV-0044',
    customerId: 'revenuebot',
    customerName: 'RevenueBot',
    customerEmail: 'billing@revenuebot.ai',
    planId: 'plan_enterprise',
    planName: 'Enterprise',
    status: 'past_due',
    amount: 12470,
    subtotal: 12470,
    tax: 0,
    invoiceDate: '2026-01-03',
    dueDate: '2026-02-02',
    billingPeriodStart: '2025-12-01',
    billingPeriodEnd: '2025-12-31',
    isFinalized: true,
    lineItems: [
      {
        id: 'li_301',
        type: 'usage_based',
        featureName: 'API Requests',
        dimension: 'process',
        quantity: 189500,
        rate: 0.2,
        amount: 37900,
        description: 'API requests (process)'
      },
      {
        id: 'li_302',
        type: 'usage_based',
        featureName: 'Tokens Consumed',
        quantity: 8920000,
        rate: 0.0015,
        amount: 13380,
        description: 'Token usage'
      },
      {
        id: 'li_303',
        type: 'usage_based',
        featureName: 'Storage (GB)',
        quantity: 156,
        rate: 1.5,
        amount: 234,
        description: 'Storage'
      },
      {
        id: 'li_304',
        type: 'usage_based',
        featureName: 'Compute Hours',
        quantity: 342,
        rate: 0.5,
        amount: 171,
        description: 'Compute time'
      },
      {
        id: 'li_305',
        type: 'fixed',
        quantity: 1,
        rate: 499,
        amount: 499,
        description: 'Platform access fee'
      },
      {
        id: 'li_306',
        type: 'fixed',
        quantity: 1,
        rate: 200,
        amount: 200,
        description: 'Priority support'
      }
    ]
  },
  {
    id: 'INV-0043',
    customerId: 'dealflow',
    customerName: 'DealFlow',
    customerEmail: 'ap@dealflow.tech',
    planId: 'plan_growth',
    planName: 'Growth',
    status: 'paid',
    amount: 3422,
    subtotal: 3422,
    tax: 0,
    invoiceDate: '2025-12-28',
    dueDate: '2025-12-28',
    billingPeriodStart: '2025-11-28',
    billingPeriodEnd: '2025-12-27',
    isFinalized: true,
    lineItems: [
      {
        id: 'li_401',
        type: 'usage_based',
        featureName: 'API Requests',
        dimension: 'auth',
        quantity: 32100,
        rate: 0.05,
        amount: 1605,
        description: 'API requests (auth)'
      },
      {
        id: 'li_402',
        type: 'usage_based',
        featureName: 'API Requests',
        dimension: 'process',
        quantity: 18400,
        rate: 0.25,
        amount: 4600,
        description: 'API requests (process)'
      },
      {
        id: 'li_403',
        type: 'usage_based',
        featureName: 'Tokens Consumed',
        quantity: 890000,
        rate: 0.001,
        amount: 890,
        description: 'Token usage'
      },
      {
        id: 'li_404',
        type: 'usage_based',
        featureName: 'Storage (GB)',
        quantity: 15,
        rate: 2.0,
        amount: 30,
        description: 'Storage (10-100GB tier)'
      },
      {
        id: 'li_405',
        type: 'fixed',
        quantity: 1,
        rate: 199,
        amount: 199,
        description: 'Platform access fee'
      }
    ]
  }
]

export const invoiceStatusColors: Record<string, string> = {
  draft: 'bg-gray-100 text-gray-700',
  pending_issue: 'bg-blue-100 text-blue-700',
  issued: 'bg-teal-100 text-teal-700',
  paid: 'bg-green-100 text-green-700',
  past_due: 'bg-red-100 text-red-700',
  payment_failed: 'bg-red-100 text-red-700',
  voided: 'bg-gray-100 text-gray-500'
}

export const invoiceStatusLabels: Record<string, string> = {
  draft: 'Draft',
  pending_issue: 'Pending Issue',
  issued: 'Issued',
  paid: 'Paid',
  past_due: 'Past Due',
  payment_failed: 'Payment Failed',
  voided: 'Voided'
}

// ============ FEATURES ============

export const features: Feature[] = [
  {
    id: 'feat_1',
    name: 'Campaign Analytics',
    key: 'campaign_analytics',
    description: 'Access to advanced analytics dashboard with custom reports',
    isEnabled: true,
    createdAt: '2025-09-15T10:00:00Z',
    modifiedAt: '2025-12-01T14:30:00Z',
    plansCount: 2
  },
  {
    id: 'feat_2',
    name: 'API Access',
    key: 'api_access',
    description: 'Programmatic access to platform APIs',
    isEnabled: true,
    createdAt: '2025-08-20T09:00:00Z',
    modifiedAt: '2025-11-15T11:00:00Z',
    plansCount: 3
  },
  {
    id: 'feat_3',
    name: 'CRM Integrations',
    key: 'crm_integrations',
    description: 'Build custom integrations with third-party services',
    isEnabled: true,
    createdAt: '2025-10-01T08:00:00Z',
    modifiedAt: '2025-12-20T16:45:00Z',
    plansCount: 1
  },
  {
    id: 'feat_4',
    name: 'Priority Support',
    key: 'priority_support',
    description: '24/7 priority support with dedicated account manager',
    isEnabled: true,
    createdAt: '2025-07-10T12:00:00Z',
    modifiedAt: '2025-10-05T09:15:00Z',
    plansCount: 1
  },
  {
    id: 'feat_5',
    name: 'White Label',
    key: 'white_label',
    description: 'Remove branding and customize with your own logo',
    isEnabled: false,
    createdAt: '2025-11-01T14:00:00Z',
    modifiedAt: '2025-11-01T14:00:00Z',
    plansCount: 1
  },
  {
    id: 'feat_6',
    name: 'SSO/SAML',
    key: 'sso_saml',
    description: 'Enterprise single sign-on with SAML 2.0 support',
    isEnabled: true,
    createdAt: '2025-06-15T10:30:00Z',
    modifiedAt: '2025-09-22T08:00:00Z',
    plansCount: 1
  },
  {
    id: 'feat_7',
    name: 'Audit Logs',
    key: 'audit_logs',
    description: 'Detailed audit logs for compliance and security',
    isEnabled: true,
    createdAt: '2025-08-01T11:00:00Z',
    modifiedAt: '2025-12-10T13:20:00Z',
    plansCount: 2
  },
  {
    id: 'feat_8',
    name: 'Data Export',
    key: 'data_export',
    description: 'Export data in CSV, JSON, or Parquet formats',
    isEnabled: true,
    createdAt: '2025-09-01T09:00:00Z',
    modifiedAt: '2025-11-28T15:00:00Z',
    plansCount: 3
  },
  {
    id: 'feat_metered_1',
    name: 'API Requests',
    key: 'api_requests',
    description: 'Track and bill for API request usage',
    isEnabled: true,
    createdAt: '2025-10-15T10:00:00Z',
    modifiedAt: '2025-12-01T14:30:00Z',
    plansCount: 3
  },
  {
    id: 'feat_metered_2',
    name: 'Contact Storage (GB)',
    key: 'contact_storage_gb',
    description: 'Track and bill for contact database storage',
    isEnabled: true,
    createdAt: '2025-11-01T09:00:00Z',
    modifiedAt: '2025-12-15T11:00:00Z',
    plansCount: 3
  },
  {
    id: 'feat_metered_3',
    name: 'Enrichment Credits',
    key: 'enrichment_credits',
    description: 'Track and bill for contact enrichment usage',
    isEnabled: true,
    createdAt: '2025-10-20T08:00:00Z',
    modifiedAt: '2025-12-10T16:45:00Z',
    plansCount: 2
  },
  {
    id: 'feat_metered_4',
    name: 'Sequence Runs',
    key: 'sequence_runs',
    description: 'Track and bill for automated sequence execution',
    isEnabled: true,
    createdAt: '2025-12-01T12:00:00Z',
    modifiedAt: '2025-12-20T09:15:00Z',
    plansCount: 1
  }
]

// ============ PRICING OPPORTUNITIES ============

export const pricingOpportunities: PricingOpportunity[] = [
  {
    id: 'opp_001',
    type: 'negative_margin',
    severity: 'high',
    featureKey: 'lead_scoring',
    featureName: 'Lead Scoring',
    title: 'Lead Scoring losing $213.60/month',
    description:
      "This feature costs $0.0024 per use but is offered free. With 89,000 monthly uses, you're losing $213.60/month.",
    currentPrice: 0,
    suggestedPrice: 0.01,
    volume: 89000,
    cost: 0.0024,
    monthlyLoss: 213.6,
    potentialGain: 676.4,
    affectedCustomers: 156
  },
  {
    id: 'opp_002',
    type: 'negative_margin',
    severity: 'high',
    featureKey: 'campaign_analytics',
    featureName: 'Campaign Analytics',
    title: 'Campaign Analytics losing $144/month',
    description:
      "This feature costs $0.08 per report but is offered free. With 1,800 monthly reports, you're losing $144/month.",
    currentPrice: 0,
    suggestedPrice: 0.15,
    volume: 1800,
    cost: 0.08,
    monthlyLoss: 144,
    potentialGain: 126,
    affectedCustomers: 42
  },
  {
    id: 'opp_003',
    type: 'negative_margin',
    severity: 'high',
    featureKey: 'export_contacts',
    featureName: 'Contact Export',
    title: 'Contact Export losing $124/month',
    description:
      "This feature costs $0.01 per export but is offered free. With 12,400 monthly exports, you're losing $124/month.",
    currentPrice: 0,
    suggestedPrice: 0.02,
    volume: 12400,
    cost: 0.01,
    monthlyLoss: 124,
    potentialGain: 124,
    affectedCustomers: 89
  },
  {
    id: 'opp_004',
    type: 'underpriced',
    severity: 'medium',
    featureKey: 'proposal_generation',
    featureName: 'Proposal Generation',
    title: 'Proposal Generation margin could be higher',
    description:
      'Currently priced at $0.05 with $0.02 cost (60% margin). Industry standard is 70%+ margin for similar features.',
    currentPrice: 0.05,
    suggestedPrice: 0.07,
    volume: 4200,
    cost: 0.02,
    potentialGain: 84,
    affectedCustomers: 67
  },
  {
    id: 'opp_005',
    type: 'tiering_opportunity',
    severity: 'low',
    featureKey: 'api_calls',
    featureName: 'API Requests',
    title: 'API Requests has tiering opportunity',
    description:
      'Top 10% of users account for 68% of volume. Consider tiered pricing to capture more value from power users.',
    currentPrice: 0.001,
    suggestedPrice: 0.0015,
    volume: 2100000,
    cost: 0.0003,
    potentialGain: 1050,
    affectedCustomers: 23
  }
]
