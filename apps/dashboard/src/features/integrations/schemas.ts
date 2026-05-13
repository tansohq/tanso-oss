import { z } from 'zod'

export const stripeModeSchema = z.enum(['NONE', 'PAYMENT_PASS_THROUGH', 'FULL_SYNC', 'STRIPE_INTEGRATION'])

export const platformModeSchema = z.enum(['OBSERVE', 'FULL'])

export const accountSettingSchema = z.object({
  stripeMode: stripeModeSchema,
  stripeEnabled: z.boolean(),
  stripeCheckoutSuccessUrl: z.string().nullable(),
  stripeCheckoutCancelUrl: z.string().nullable(),
  currency: z.string().default('USD'),
  platformMode: platformModeSchema.default('FULL')
})

export const accountSettingResponseSchema = z.object({
  data: accountSettingSchema,
  success: z.boolean()
})

export const updateAccountSettingSchema = z.object({
  stripeMode: stripeModeSchema.optional(),
  currency: z.string().optional(),
  stripeCheckoutSuccessUrl: z.string().nullable().optional(),
  stripeCheckoutCancelUrl: z.string().nullable().optional(),
  platformMode: platformModeSchema.optional()
})

export type StripeMode = z.infer<typeof stripeModeSchema>
export type PlatformMode = z.infer<typeof platformModeSchema>
export type AccountSetting = z.infer<typeof accountSettingSchema>
export type AccountSettingResponse = z.infer<typeof accountSettingResponseSchema>
export type UpdateAccountSetting = z.infer<typeof updateAccountSettingSchema>

export const stripeApiKeysSchema = z.object({
  stripeApiKey: z.string().nullable(),
  webhookSecret: z.string().nullable()
})

export const stripeApiKeysResponseSchema = z.object({
  data: stripeApiKeysSchema,
  success: z.boolean()
})

export type StripeApiKeys = z.infer<typeof stripeApiKeysSchema>
export type StripeApiKeysResponse = z.infer<typeof stripeApiKeysResponseSchema>
