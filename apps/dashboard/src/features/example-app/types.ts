export interface SimulatedInvoice {
  id: string
  date: string
  planName: string
  amount: number
  currency: string
  status: 'Paid' | 'Pending'
}

export interface EnrichedFeature {
  id: string
  name: string
  key: string
  pricingType: 'included' | 'usage_based' | 'graduated'
  unitPrice?: number
  unitLabel?: string
  maxUsage?: number | null
  tiers?: Array<{ up_to: number | 'inf'; price_per_unit: number; flat_fee?: number }>
}

export interface EnrichedPlan {
  id: string
  key: string
  name: string
  description: string | null
  priceAmount: number
  intervalMonths: string | null
  currency: string
  status: string | null
  features: EnrichedFeature[]
}
