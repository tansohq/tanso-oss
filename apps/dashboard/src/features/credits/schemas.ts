import { z } from 'zod'

export const creditModelSchema = z.object({
  id: z.string(),
  name: z.string(),
  denomination: z.string(),
  description: z.string().nullable().optional(),
  hardLimit: z.boolean().optional(),
  rolloverPolicy: z.string().optional(),
  createdAt: z.string()
})

export const creditModelsResponseSchema = z
  .object({
    data: z.array(creditModelSchema).optional(),
    success: z.boolean().optional()
  })
  .passthrough()

export const creditModelDetailResponseSchema = z
  .object({
    data: creditModelSchema.optional(),
    success: z.boolean().optional()
  })
  .passthrough()

export const createCreditModelSchema = z.object({
  name: z.string().min(1, 'Name is required'),
  denomination: z.string().min(1, 'Denomination is required'),
  description: z.string().optional()
})

export const planCreditAllocationSchema = z.object({
  id: z.string(),
  creditModelId: z.string(),
  creditModelName: z.string().optional(),
  denomination: z.string().optional(),
  planId: z.string(),
  creditAmount: z.number(),
  grantExpiresMonths: z.number().nullable().optional(),
  hardLimit: z.boolean().nullable().optional(),
  createdAt: z.string()
})

export const planCreditAllocationsResponseSchema = z
  .object({
    data: z.array(planCreditAllocationSchema).optional(),
    success: z.boolean().optional()
  })
  .passthrough()

export const creditPoolSchema = z.object({
  id: z.string(),
  customerId: z.string(),
  creditModelId: z.string(),
  denomination: z.string().optional(),
  balance: z.number(),
  status: z.string(),
  hardLimit: z.boolean().optional(),
  createdAt: z.string()
})

export const creditPoolsResponseSchema = z
  .object({
    data: z.array(creditPoolSchema).optional(),
    success: z.boolean().optional()
  })
  .passthrough()

export const creditGrantSchema = z.object({
  id: z.string(),
  poolId: z.string(),
  amount: z.number(),
  remainingAmount: z.number().optional(),
  source: z.string().optional(),
  expiresAt: z.string().nullable().optional(),
  createdAt: z.string()
})

export const creditGrantsResponseSchema = z
  .object({
    data: z.array(creditGrantSchema).optional(),
    success: z.boolean().optional()
  })
  .passthrough()

export const creditTransactionSchema = z.object({
  id: z.string(),
  poolId: z.string(),
  amount: z.number(),
  type: z.string(),
  description: z.string().nullable().optional(),
  createdAt: z.string()
})

export const creditTransactionsResponseSchema = z
  .object({
    data: z.array(creditTransactionSchema).optional(),
    success: z.boolean().optional()
  })
  .passthrough()
