import { z } from 'zod'

export const eventSchema = z.object({
  id: z.string().uuid(),
  accountId: z.string().uuid(),
  eventIdempotencyKey: z.string().nullable(),
  flowId: z.string().nullable(),
  eventName: z.string().nullable(),
  occurredAt: z.string().nullable(),
  customerId: z.string().uuid().nullable(),
  customerReferenceId: z.string().nullable(),
  featureId: z.string().uuid().nullable(),
  subscriptionId: z.string().uuid().nullable(),
  entitlementId: z.string().uuid().nullable(),
  invoiceId: z.string().uuid().nullable(),
  revenueAmount: z.number().nullable(),
  revenueUnit: z.string().nullable(),
  costAmount: z.number().nullable(),
  costUnit: z.string().nullable(),
  usageUnits: z.number().nullable(),
  usageUnitType: z.string().nullable(),
  properties: z.object({ isEntitled: z.boolean().optional() }).passthrough().nullable(),
  meta: z.record(z.unknown()).nullable(),
  context: z.record(z.unknown()).nullable(),
  customerIsNative: z.boolean().nullable(),
  featureIsNative: z.boolean().nullable(),
  subscriptionIsNative: z.boolean().nullable(),
  entitlementIsNative: z.boolean().nullable(),
  invoiceIsNative: z.boolean().nullable(),
  ingestError: z.string().nullable(),
  createdAt: z.string(),
  modifiedAt: z.string(),
  eventType: z.string().nullable(),
  model: z.string().nullable().optional(),
  modelProvider: z.string().nullable().optional(),
  featureKey: z.string().nullable().optional(),
  inputTokens: z.number().nullable().optional(),
  outputTokens: z.number().nullable().optional()
})

export const pagedEventsSchema = z.object({
  items: z.array(eventSchema),
  totalElements: z.number(),
  totalPages: z.number(),
  page: z.number(),
  size: z.number()
})

export const eventsResponseSchema = z
  .object({
    data: pagedEventsSchema.optional(),
    success: z.boolean().optional()
  })
  .passthrough()

export const createEventSchema = z.object({
  featureKey: z.string().min(1, 'Feature is required'),
  featureId: z.string().uuid().optional(),
  eventName: z.string().min(1, 'Event name is required').max(128),
  customerReferenceId: z.string().optional(),
  eventIdempotencyKey: z.string().min(1, 'Idempotency key is required'),
  occurredAt: z.string().min(1, 'Occurred at is required'),
  usageUnits: z.coerce.number().optional(),
  costAmount: z.coerce.number().optional(),
  costInput: z
    .object({
      model: z.string().optional(),
      modelProvider: z.string().optional(),
      costUnits: z.coerce.number().optional(),
      inputTokens: z.coerce.number().optional(),
      outputTokens: z.coerce.number().optional()
    })
    .optional(),
  meta: z.string().optional()
})
