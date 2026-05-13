import { z } from 'zod'
import {
  featureSchema,
  featuresResponseSchema,
  featureDetailResponseSchema,
  createFeatureSchema,
  updateFeatureSchema
} from './schemas'

export type Feature = z.infer<typeof featureSchema>
export type FeaturesResponse = z.infer<typeof featuresResponseSchema>
export type FeatureDetailResponse = z.infer<typeof featureDetailResponseSchema>
export type CreateFeature = z.infer<typeof createFeatureSchema>
export type UpdateFeature = z.infer<typeof updateFeatureSchema>

export interface FetchFeaturesParams {
  page?: number
  size?: number
}
