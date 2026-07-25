import { z } from "zod"

export const creditModelSchema = z.object({
  name: z.string().min(1, "Name is required"),
  denomination: z.string().min(1, "Denomination is required"),
  description: z.string().optional(),
  hardLimit: z.boolean(),
  rolloverPolicy: z.enum(["NONE", "FULL", "CAPPED"]),
  rolloverCap: z.coerce.number().min(0).optional(),
})

export type CreditModelInput = z.infer<typeof creditModelSchema>

export const creditPoolSchema = z.object({
  name: z.string().min(1, "Name is required"),
  denomination: z.string().min(1, "Denomination is required"),
  currency: z.string().optional(),
  customerId: z.string().optional(),
  hardLimit: z.boolean(),
  rolloverPolicy: z.enum(["NONE", "FULL", "CAPPED"]),
  rolloverCap: z.coerce.number().min(0).optional(),
})

export type CreditPoolInput = z.infer<typeof creditPoolSchema>

export const creditGrantSchema = z.object({
  amount: z.coerce.number().positive("Amount must be positive"),
  grantType: z.enum(["PLAN_INCLUDED", "PURCHASED", "PROMOTIONAL", "REFUND", "SYSTEM", "ROLLOVER"]),
  expiresAt: z.string().optional(),
  description: z.string().optional(),
})

export type CreditGrantInput = z.infer<typeof creditGrantSchema>

export const creditWeightValueSchema = z.coerce
  .number({ error: "Weight must be a number" })
  .positive("Weight must be positive")
  .max(1_000_000, "Weight exceeds maximum 1,000,000")
  .refine((v) => {
    const decimals = String(v).split(".")[1] ?? ""
    return decimals.length <= 6
  }, "At most 6 decimal places")
