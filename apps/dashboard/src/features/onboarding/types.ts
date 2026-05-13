import { z } from 'zod'
import {
  roleSchema,
  buildingTypeSchema,
  goalSchema,
  intakeDataSchema,
  intakeDataResponseSchema,
  onboardingProgressSchema,
  onboardingProgressResponseSchema,
  completeStepRequestSchema
} from './schemas'

export type Role = z.infer<typeof roleSchema>
export type BuildingType = z.infer<typeof buildingTypeSchema>
export type Goal = z.infer<typeof goalSchema>
export type IntakeData = z.infer<typeof intakeDataSchema>
export type IntakeDataResponse = z.infer<typeof intakeDataResponseSchema>
export type OnboardingProgress = z.infer<typeof onboardingProgressSchema>
export type OnboardingProgressResponse = z.infer<typeof onboardingProgressResponseSchema>
export type CompleteStepRequest = z.infer<typeof completeStepRequestSchema>
