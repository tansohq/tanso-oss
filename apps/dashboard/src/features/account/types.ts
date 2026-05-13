import { z } from 'zod'
import {
  accountApiKeySchema,
  accountApiKeyResponseSchema,
  subscriptionStatusSchema,
  subscriptionStatusResponseSchema,
  subscribeToPlanRequestSchema,
  changePasswordSchema
} from './schemas'

export type AccountApiKey = z.infer<typeof accountApiKeySchema>
export type AccountApiKeyResponse = z.infer<typeof accountApiKeyResponseSchema>
export type SubscriptionStatus = z.infer<typeof subscriptionStatusSchema>
export type SubscriptionStatusResponse = z.infer<typeof subscriptionStatusResponseSchema>
export type SubscribeToPlanRequest = z.infer<typeof subscribeToPlanRequestSchema>
export type ChangePasswordFormData = z.infer<typeof changePasswordSchema>

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}
