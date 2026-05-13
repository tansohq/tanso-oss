import { z } from 'zod'

export const planStatusSchema = z.enum(['draft', 'active', 'archived'])

export const planSchema = z.object({
  id: z.string(),
  key: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  priceAmount: z.number().nullable(),
  intervalMonths: z.string().nullable(),
  billingTiming: z.string().nullable(),
  status: planStatusSchema.nullable().optional(),
  createdAt: z.string(),
  modifiedAt: z.string(),
  metadata: z.record(z.unknown()).nullable().optional()
})

export const plansResponseSchema = z
  .object({
    data: z.array(planSchema).optional(),
    success: z.boolean().optional()
  })
  .passthrough()

export const planDetailResponseSchema = z
  .object({
    data: planSchema.optional(),
    success: z.boolean().optional()
  })
  .passthrough()

export const basePriceSchema = z.object({
  priceAmount: z.number().min(0, 'Amount must be 0 or greater'),
  billingTiming: z.string().min(1, 'Please select billing timing')
})

export const createPlanSchema = z.object({
  key: z.string().min(1, 'Please enter a unique key').regex(/^[a-z][a-z0-9_]*$/, 'Key must be lowercase letters, numbers, and underscores'),
  name: z.string().min(1, 'Please give this plan a name'),
  description: z.string().min(1, 'Please add a description'),
  intervalMonths: z.string().min(1, 'Please select a billing interval'),
  metadata: z.record(z.unknown()).nullable().optional()
})

export const updatePlanSchema = z.object({
  key: z.string().min(1, 'Please enter a unique key').regex(/^[a-z][a-z0-9_]*$/, 'Key must be lowercase letters, numbers, and underscores').optional(),
  name: z.string().min(1, 'Please give this plan a name'),
  description: z.string().min(1, 'Please add a description'),
  intervalMonths: z.string().min(1, 'Please select a billing interval'),
  priceAmount: z.number().min(0, 'Amount must be 0 or greater').optional(),
  billingTiming: z.string().optional(),
  metadata: z.record(z.unknown()).nullable().optional()
})

export const featureSchema = z.object({
  id: z.string(),
  name: z.string(),
  key: z.string(),
  description: z.string().nullable(),
  createdAt: z.string(),
  modifiedAt: z.string(),
  isEnabled: z.boolean(),
  type: z.string().optional(),
  value: z
    .object({
      model: z.string().optional(),
      price_per_unit: z.number().optional(),
      usage_unit_type: z.string().optional(),
      cost_per_unit: z.number().optional(),
      tiers: z
        .array(
          z.object({
            up_to: z.union([z.number(), z.literal('inf')]),
            price_per_unit: z.number(),
            flat_fee: z.number().optional()
          })
        )
        .optional()
    })
    .optional()
})

// Graduated tier schema
export const graduatedTierSchema = z.object({
  up_to: z.union([z.number().int().positive(), z.literal('inf')]),
  price_per_unit: z.number().min(0, 'Price per unit must be 0 or greater'),
  flat_fee: z.number().min(0).default(0)
})

export const graduatedPricingValueSchema = z.object({
  model: z.literal('graduated'),
  usage_unit_type: z.string().min(1, 'Unit type is required'),
  tiers: z.array(graduatedTierSchema).min(1, 'At least one tier is required'),
  cost_per_unit: z.number().min(0).optional()
})

// Feature pricing types
export const featurePricingTypeSchema = z.enum(['included', 'usage_based', 'graduated'])

export const featureBillingTimingSchema = z.enum(['IN_ARREARS', 'IN_ADVANCE'])

// Schema for adding a feature with pricing configuration
export const addFeaturePricingSchema = z.discriminatedUnion('pricingType', [
  z.object({
    pricingType: z.literal('included')
  }),
  z.object({
    pricingType: z.literal('usage_based'),
    unitPrice: z.number().min(0, 'Unit price must be 0 or greater'),
    unitLabel: z.string().min(1, 'Please enter a unit label'),
    costPerUnit: z.number().min(0, 'Cost must be 0 or greater'),
    billingTiming: featureBillingTimingSchema
  }),
  z.object({
    pricingType: z.literal('graduated'),
    unitLabel: z.string().min(1, 'Please enter a unit label'),
    tiers: z.array(graduatedTierSchema).min(1, 'At least one tier is required'),
    costPerUnit: z.number().min(0, 'Cost must be 0 or greater').optional(),
    billingTiming: featureBillingTimingSchema
  })
])

// Schema for a linked feature with its pricing configuration
export const linkedFeatureWithPricingSchema = z.object({
  id: z.string(),
  name: z.string(),
  key: z.string(),
  description: z.string().nullable(),
  createdAt: z.string(),
  modifiedAt: z.string(),
  isEnabled: z.boolean(),
  // Pricing configuration
  pricingType: featurePricingTypeSchema,
  unitPrice: z.number().optional(),
  unitLabel: z.string().optional(),
  costPerUnit: z.number().optional(),
  billingTiming: featureBillingTimingSchema.optional(),
  tiers: z
    .array(
      z.object({
        up_to: z.union([z.number(), z.literal('inf')]),
        price_per_unit: z.number(),
        flat_fee: z.number().optional()
      })
    )
    .optional()
})

export const planFeatureLinkedSchema = z.object({
  plan: planSchema,
  features: z.array(featureSchema)
})

export const planFeatureLinkedListResponseSchema = z
  .object({
    data: z.array(planFeatureLinkedSchema).optional(),
    success: z.boolean().optional()
  })
  .passthrough()

export const planFeatureLinkedResponseSchema = z
  .object({
    data: planFeatureLinkedSchema.optional(),
    success: z.boolean().optional()
  })
  .passthrough()
