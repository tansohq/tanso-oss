import { z } from 'zod'
import { subscriptionSchema } from '@/features/subscriptions/schemas'

export const invoiceSchema = z.object({
  id: z.string(),
  amount: z.number(),
  dueDate: z.string(),
  currency: z.string(),
  subscription: subscriptionSchema,
  status: z.string(),
  metadata: z.any().nullable(),
  createdAt: z.string(),
  modifiedAt: z.string()
})

export const invoicesResponseSchema = z
  .object({
    data: z.array(invoiceSchema).optional(),
    success: z.boolean().optional()
  })
  .passthrough()

export const invoiceItemSchema = z.object({
  id: z.string(),
  invoiceId: z.string(),
  chargeAmount: z.number(),
  description: z.string().nullable(),
  createdAt: z.string(),
  modifiedAt: z.string().nullable()
})

export const invoiceDetailSchema = invoiceSchema.extend({
  items: z.array(invoiceItemSchema).optional()
})

export const invoiceDetailResponseSchema = z
  .object({
    data: invoiceDetailSchema.optional(),
    success: z.boolean().optional()
  })
  .passthrough()
