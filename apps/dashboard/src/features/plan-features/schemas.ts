import { z } from 'zod'

export const linkFeatureSchema = z.object({
  featureId: z.string().uuid(),
  isEnabled: z.boolean(),
  type: z.string(),
  value: z.record(z.any())
})

export const planFeatureLinkedDiffRequestSchema = z.object({
  features: z.array(linkFeatureSchema)
})

export const planFeatureLinkedDiffResponseSchema = z.object({
  addedFeatures: z.array(
    z.object({
      id: z.string(),
      name: z.string(),
      key: z.string(),
      description: z.string().nullable(),
      createdAt: z.string(),
      modifiedAt: z.string(),
      isEnabled: z.boolean()
    })
  ),
  removedFeatures: z.array(
    z.object({
      id: z.string(),
      name: z.string(),
      key: z.string(),
      description: z.string().nullable(),
      createdAt: z.string(),
      modifiedAt: z.string(),
      isEnabled: z.boolean()
    })
  )
})

export const planFeatureRuleSchema = z.object({
  id: z.string().uuid(),
  planId: z.string().uuid(),
  featureId: z.string().uuid(),
  value: z.record(z.any()),
  type: z.string(),
  enabled: z.boolean()
})

export const planFeatureRuleResponseSchema = z
  .object({
    data: planFeatureRuleSchema.optional()
  })
  .passthrough()

export const planFeatureLinkedDiffApiResponseSchema = z
  .object({
    data: planFeatureLinkedDiffResponseSchema.optional()
  })
  .passthrough()
