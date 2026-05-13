import { z } from 'zod'

export const roleSchema = z.enum([
  'TECHNICAL_FOUNDER',
  'NON_TECHNICAL_FOUNDER',
  'ENGINEER',
  'PRODUCT',
  'GROWTH_OPS',
  'OTHER'
])

export const buildingTypeSchema = z.enum([
  'B2B_AI',
  'B2C_AI',
  'SAAS_PLATFORM',
  'API_SERVICE',
  'MARKETPLACE',
  'OTHER'
])

export const goalSchema = z.enum([
  'MARGIN_ANALYTICS',
  'STRIPE_INTEGRATION',
  'PRICING_FLEXIBILITY',
  'ENFORCE_LIMITS',
  'OTHER'
])

export const intakeDataSchema = z.object({
  role: roleSchema,
  roleOther: z.string().optional(),
  buildingType: buildingTypeSchema,
  buildingTypeOther: z.string().optional(),
  goal: goalSchema,
  goalOther: z.string().optional()
})

export const intakeDataResponseSchema = z.object({
  data: intakeDataSchema.nullable(),
  success: z.boolean()
})

export const onboardingProgressSchema = z.object({
  completedSteps: z.array(z.string())
})

export const onboardingProgressResponseSchema = z.object({
  data: onboardingProgressSchema,
  success: z.boolean()
})

export const completeStepRequestSchema = z.object({
  stepKey: z.string().min(1)
})
