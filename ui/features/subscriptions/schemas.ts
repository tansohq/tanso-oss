import { z } from "zod"

export const subscriptionSchema = z.object({
  customerId: z.string().min(1, "Pick a customer"),
  planId: z.string().min(1, "Pick a plan"),
  gracePeriod: z.coerce.number().int().min(0).optional(),
})

export type SubscriptionInput = z.infer<typeof subscriptionSchema>
