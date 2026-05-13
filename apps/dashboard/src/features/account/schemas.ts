import { z } from 'zod'

export const accountApiKeySchema = z.object({
  apiKey: z.string()
})

export const accountApiKeyResponseSchema = z
  .object({
    data: accountApiKeySchema.optional(),
    success: z.boolean().optional()
  })
  .passthrough()

export const subscriptionStatusSchema = z.object({
  hasActiveSubscription: z.boolean(),
  planName: z.string().nullish(),
  planPriceAmount: z.number().nullish(),
  planIntervalMonths: z.string().nullish(),
  planKey: z.string().nullish()
})

export const subscriptionStatusResponseSchema = z.object({
  data: subscriptionStatusSchema.optional(),
  success: z.boolean().optional()
})

export const subscribeToPlanRequestSchema = z.object({
  planId: z.string().uuid('Please select a valid plan')
})

export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Current password is required'),
    newPassword: z.string().min(8, 'Password must be at least 8 characters'),
    confirmNewPassword: z.string()
  })
  .refine((data) => data.newPassword === data.confirmNewPassword, {
    message: 'Passwords do not match',
    path: ['confirmNewPassword']
  })
