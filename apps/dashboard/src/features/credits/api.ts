import { api } from '@/lib/api'
import type {
  CreditModelsResponse,
  CreditModelDetailResponse,
  CreateCreditModel,
  PlanCreditAllocationsResponse,
  CreditPoolsResponse,
  CreditGrantsResponse,
  CreditTransactionsResponse
} from './types'

// Credit Models
export async function fetchCreditModels(): Promise<CreditModelsResponse> {
  return api.get<CreditModelsResponse>('/api/v1/monetization/credits/models')
}

export async function getCreditModel(id: string): Promise<CreditModelDetailResponse> {
  return api.get<CreditModelDetailResponse>(`/api/v1/monetization/credits/models/${id}`)
}

export async function createCreditModel(data: CreateCreditModel): Promise<CreditModelDetailResponse> {
  return api.post<CreditModelDetailResponse>('/api/v1/monetization/credits/models', data)
}

export async function deleteCreditModel(id: string): Promise<void> {
  return api.delete<void>(`/api/v1/monetization/credits/models/${id}`)
}

// Plan Credit Allocations
export async function fetchPlanCreditAllocations(planId: string): Promise<PlanCreditAllocationsResponse> {
  return api.get<PlanCreditAllocationsResponse>(`/api/v1/monetization/credits/plans/${planId}/allocations`)
}

export async function createPlanCreditAllocation(
  creditModelId: string,
  planId: string,
  data: { creditAmount: number; grantExpiresMonths?: number; hardLimit?: boolean }
): Promise<void> {
  const params = new URLSearchParams()
  params.set('creditAmount', data.creditAmount.toString())
  if (data.grantExpiresMonths != null) {
    params.set('grantExpiresMonths', data.grantExpiresMonths.toString())
  }
  if (data.hardLimit != null) {
    params.set('hardLimit', data.hardLimit.toString())
  }
  return api.post<void>(
    `/api/v1/monetization/credits/models/${creditModelId}/plans/${planId}?${params.toString()}`
  )
}

export async function deletePlanCreditAllocation(
  creditModelId: string,
  planId: string
): Promise<void> {
  return api.delete<void>(`/api/v1/monetization/credits/models/${creditModelId}/plans/${planId}`)
}

// Credit Pools
export async function fetchCustomerCreditPools(customerId: string): Promise<CreditPoolsResponse> {
  return api.get<CreditPoolsResponse>(`/api/v1/monetization/credits/pools/customer/${customerId}`)
}

// Pool Grants
export async function fetchPoolGrants(poolId: string): Promise<CreditGrantsResponse> {
  return api.get<CreditGrantsResponse>(`/api/v1/monetization/credits/pools/${poolId}/grants`)
}

// Pool Transactions
export async function fetchPoolTransactions(poolId: string): Promise<CreditTransactionsResponse> {
  return api.get<CreditTransactionsResponse>(`/api/v1/monetization/credits/pools/${poolId}/transactions`)
}

// Manual Grant
export async function grantCredits(
  poolId: string,
  data: { amount: number; reason?: string }
): Promise<CreditGrantsResponse> {
  return api.post<CreditGrantsResponse>(
    '/api/v1/monetization/credits/grants',
    {
      creditPoolId: poolId,
      amount: data.amount,
      grantType: 'MANUAL',
      description: data.reason
    }
  )
}
