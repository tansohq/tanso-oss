import { z } from 'zod'

const subscriptionCustomerSchema = z.object({
  id: z.string(),
  referenceId: z.string().optional(),
  customerReferenceId: z.string().optional(),
  firstName: z.string(),
  lastName: z.string(),
  email: z.string(),
  phoneNumber: z.string().nullable(),
  createdAt: z.string(),
  modifiedAt: z.string()
})

const subscriptionPlanSchema = z.object({
  id: z.string(),
  key: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  priceAmount: z.number().nullable(),
  intervalMonths: z.string().nullable(),
  billingTiming: z.string().nullable(),
  createdAt: z.string(),
  modifiedAt: z.string()
})

const subscriptionScheduledChangeSchema = z.object({
  id: z.string(),
  effectiveAt: z.string(),
  changeType: z.string(),
  newPlanId: z.string()
})

export const subscriptionSchema = z.object({
  id: z.string(),
  isActive: z.boolean(),
  intervalMonths: z.string(),
  customer: subscriptionCustomerSchema,
  plan: subscriptionPlanSchema,
  gracePeriodDays: z.number(),
  currentPeriodStart: z.string(),
  currentPeriodEnd: z.string(),
  cancelMode: z.string().nullable(),
  cancelEffectiveAt: z.string().nullable(),
  cancelledAt: z.string().nullable(),
  billingAnchorDay: z.number(),
  metadata: z.any().nullable(),
  scheduledChange: subscriptionScheduledChangeSchema.nullable().optional()
})

export const subscriptionsResponseSchema = z
  .object({
    data: z.array(subscriptionSchema).optional(),
    success: z.boolean().optional()
  })
  .passthrough()

export const subscriptionDetailResponseSchema = z
  .object({
    data: subscriptionSchema.optional(),
    success: z.boolean().optional()
  })
  .passthrough()

export const createSubscriptionSchema = z.object({
  planId: z.string().min(1, 'Please select a plan'),
  customerId: z.string().min(1, 'Please select a customer'),
  gracePeriod: z.number().optional()
})
