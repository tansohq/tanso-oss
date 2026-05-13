import { z } from 'zod'
import {
  creditModelSchema,
  creditModelsResponseSchema,
  creditModelDetailResponseSchema,
  createCreditModelSchema,
  planCreditAllocationSchema,
  planCreditAllocationsResponseSchema,
  creditPoolSchema,
  creditPoolsResponseSchema,
  creditGrantSchema,
  creditGrantsResponseSchema,
  creditTransactionSchema,
  creditTransactionsResponseSchema
} from './schemas'

export type CreditModel = z.infer<typeof creditModelSchema>
export type CreditModelsResponse = z.infer<typeof creditModelsResponseSchema>
export type CreditModelDetailResponse = z.infer<typeof creditModelDetailResponseSchema>
export type CreateCreditModel = z.infer<typeof createCreditModelSchema>

export type PlanCreditAllocation = z.infer<typeof planCreditAllocationSchema>
export type PlanCreditAllocationsResponse = z.infer<typeof planCreditAllocationsResponseSchema>

export type CreditPool = z.infer<typeof creditPoolSchema>
export type CreditPoolsResponse = z.infer<typeof creditPoolsResponseSchema>

export type CreditGrant = z.infer<typeof creditGrantSchema>
export type CreditGrantsResponse = z.infer<typeof creditGrantsResponseSchema>

export type CreditTransaction = z.infer<typeof creditTransactionSchema>
export type CreditTransactionsResponse = z.infer<typeof creditTransactionsResponseSchema>
