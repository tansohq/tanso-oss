import { z } from 'zod'
import {
  planSchema,
  planStatusSchema,
  plansResponseSchema,
  planDetailResponseSchema,
  createPlanSchema,
  updatePlanSchema,
  planFeatureLinkedSchema,
  planFeatureLinkedResponseSchema,
  planFeatureLinkedListResponseSchema,
  featurePricingTypeSchema,
  featureBillingTimingSchema,
  addFeaturePricingSchema,
  linkedFeatureWithPricingSchema,
  graduatedTierSchema
} from './schemas'

export type Plan = z.infer<typeof planSchema>
export type PlanStatus = z.infer<typeof planStatusSchema>
export type PlansResponse = z.infer<typeof plansResponseSchema>
export type PlanDetailResponse = z.infer<typeof planDetailResponseSchema>
export type CreatePlan = z.infer<typeof createPlanSchema>
export type UpdatePlan = z.infer<typeof updatePlanSchema>
export type PlanFeatureLinked = z.infer<typeof planFeatureLinkedSchema>
export type PlanFeatureLinkedListResponse = z.infer<typeof planFeatureLinkedListResponseSchema>
export type PlanFeatureLinkedResponse = z.infer<typeof planFeatureLinkedResponseSchema>

// Feature pricing types
export type FeaturePricingType = z.infer<typeof featurePricingTypeSchema>
export type FeatureBillingTiming = z.infer<typeof featureBillingTimingSchema>
export type AddFeaturePricing = z.infer<typeof addFeaturePricingSchema>
export type LinkedFeatureWithPricing = z.infer<typeof linkedFeatureWithPricingSchema>
export type GraduatedTier = z.infer<typeof graduatedTierSchema>

export interface LinkedFeature {
  id: string
  name: string
  key: string
  description: string | null
  createdAt: string
  modifiedAt: string
  isEnabled: boolean
  pricingType?: 'included' | 'usage_based' | 'graduated'
  unitPrice?: number
  unitLabel?: string
  costPerUnit?: number
  tiers?: Array<{ up_to: number | 'inf'; price_per_unit: number; flat_fee?: number }>
  billingTiming?: string
  type?: string
  value?: Record<string, unknown>
  creditModelId?: string
  creditModelName?: string
  creditDenomination?: string
}

export interface FetchPlansParams {
  page?: number
  size?: number
}

// Plan revenue types (from GET /api/v1/monetization/plans/{planId}/revenue)
export interface FeatureRevenue {
  featureId: string
  featureKey: string
  featureName: string
  periodStart: string
  periodEnd: string
  units: number
  revenue: number
  model: string | null
  resetMode: string | null
}

export interface SubscriptionRevenue {
  subscriptionId: string
  customerId: string
  customerName: string
  features: FeatureRevenue[]
  totalUnits: number
  totalRevenue: number
}

export interface PlanRevenuePagedData {
  items: SubscriptionRevenue[]
  totalElements: number
  totalPages: number
  page: number
  size: number
  hasNext: boolean
}

export interface PlanRevenueData {
  planId: string
  planName: string
  periodStart: string
  periodEnd: string
  aggregateTotalUnits: number
  aggregateTotalRevenue: number
  subscriptions: PlanRevenuePagedData
}

export interface PlanRevenueResponse {
  success: boolean
  data: PlanRevenueData
}
