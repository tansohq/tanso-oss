import { z } from 'zod'

export const featureSchema = z.object({
  id: z.string(),
  name: z.string(),
  key: z.string(),
  description: z.string().nullable(),
  createdAt: z.string(),
  modifiedAt: z.string(),
  isEnabled: z.boolean(),
  metadata: z.record(z.unknown()).nullable().optional()
})

export const featuresResponseSchema = z
  .object({
    data: z.array(featureSchema).optional(),
    success: z.boolean().optional()
  })
  .passthrough()

export const featureDetailResponseSchema = z
  .object({
    data: featureSchema.optional(),
    success: z.boolean().optional()
  })
  .passthrough()

export const createFeatureSchema = z.object({
  name: z.string().min(1, 'Please give this feature a name'),
  key: z.string().min(1, 'Please enter a unique key'),
  description: z.string(),
  isEnabled: z.boolean(),
  metadata: z.record(z.unknown()).nullable().optional()
})

export const updateFeatureSchema = createFeatureSchema

export const featureDrawerEditSchema = z.object({
  name: z.string().min(1, 'Name is required'),
  description: z.string()
})
