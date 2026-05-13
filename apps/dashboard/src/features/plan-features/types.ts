import { z } from 'zod'
import {
  linkFeatureSchema,
  planFeatureLinkedDiffRequestSchema,
  planFeatureLinkedDiffResponseSchema,
  planFeatureRuleSchema,
  planFeatureRuleResponseSchema,
  planFeatureLinkedDiffApiResponseSchema
} from './schemas'

export type LinkFeature = z.infer<typeof linkFeatureSchema>
export type PlanFeatureLinkedDiffRequest = z.infer<typeof planFeatureLinkedDiffRequestSchema>
export type PlanFeatureLinkedDiffResponse = z.infer<typeof planFeatureLinkedDiffResponseSchema>
export type PlanFeatureRule = z.infer<typeof planFeatureRuleSchema>
export type PlanFeatureRuleResponse = z.infer<typeof planFeatureRuleResponseSchema>
export type PlanFeatureLinkedDiffApiResponse = z.infer<
  typeof planFeatureLinkedDiffApiResponseSchema
>
