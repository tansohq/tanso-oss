import { computed, toRef, type MaybeRef } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import {
  fetchCreditModels,
  getCreditModel,
  fetchPlanCreditAllocations,
  fetchCustomerCreditPools,
  fetchPoolGrants,
  fetchPoolTransactions
} from './api'

export function useCreditModelsQuery() {
  return useQuery({
    queryKey: ['credit-models'],
    queryFn: () => fetchCreditModels()
  })
}

export function useCreditModelQuery(id: MaybeRef<string>) {
  const idRef = toRef(id)
  return useQuery({
    queryKey: computed(() => ['credit-model', idRef.value]),
    queryFn: () => getCreditModel(idRef.value),
    enabled: computed(() => !!idRef.value)
  })
}

export function usePlanCreditAllocationsQuery(planId: MaybeRef<string>) {
  const planIdRef = toRef(planId)
  return useQuery({
    queryKey: computed(() => ['plan-credit-allocations', planIdRef.value]),
    queryFn: () => fetchPlanCreditAllocations(planIdRef.value),
    enabled: computed(() => !!planIdRef.value)
  })
}

export function useCustomerCreditPoolsQuery(customerId: MaybeRef<string>) {
  const customerIdRef = toRef(customerId)
  return useQuery({
    queryKey: computed(() => ['customer-credit-pools', customerIdRef.value]),
    queryFn: () => fetchCustomerCreditPools(customerIdRef.value),
    enabled: computed(() => !!customerIdRef.value),
    staleTime: 0
  })
}

export function usePoolGrantsQuery(poolId: MaybeRef<string>) {
  const poolIdRef = toRef(poolId)
  return useQuery({
    queryKey: computed(() => ['pool-grants', poolIdRef.value]),
    queryFn: () => fetchPoolGrants(poolIdRef.value),
    enabled: computed(() => !!poolIdRef.value)
  })
}

export function usePoolTransactionsQuery(poolId: MaybeRef<string>) {
  const poolIdRef = toRef(poolId)
  return useQuery({
    queryKey: computed(() => ['pool-transactions', poolIdRef.value]),
    queryFn: () => fetchPoolTransactions(poolIdRef.value),
    enabled: computed(() => !!poolIdRef.value)
  })
}
