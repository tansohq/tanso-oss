import { z } from "zod"

export const planSchema = z.object({
  key: z.string().min(1, "Key is required"),
  name: z.string().min(1, "Name is required"),
  description: z.string().optional(),
  priceAmount: z.coerce.number().min(0, "Price must be 0 or more"),
  intervalMonths: z.coerce.number().int().min(1, "Interval must be at least 1 month"),
  billingTiming: z.enum(["IN_ADVANCE", "IN_ARREARS"]),
  status: z.enum(["DRAFT", "ACTIVE", "ARCHIVED"]),
})

export type PlanInput = z.infer<typeof planSchema>
