import { useQuery } from '@tanstack/vue-query'
import { fetchSubscriptions, getSubscription, fetchCustomerSubscriptions } from './api'
import type { FetchSubscriptionsParams } from './types'
import type { MaybeRef } from 'vue'
import { toValue } from 'vue'

export function useSubscriptionsQuery(params: FetchSubscriptionsParams = {}) {
  return useQuery({
    queryKey: ['subscriptions', params],
    queryFn: () => fetchSubscriptions(params)
  })
}

export function useSubscriptionQuery(id: MaybeRef<string>) {
  return useQuery({
    queryKey: ['subscription', id],
    queryFn: () => getSubscription(toValue(id)),
    enabled: !!toValue(id)
  })
}

export function useCustomerSubscriptionsQuery(customerUUID: MaybeRef<string>) {
  return useQuery({
    queryKey: ['customer-subscriptions', customerUUID],
    queryFn: () => fetchCustomerSubscriptions(toValue(customerUUID)),
    enabled: !!toValue(customerUUID)
  })
}
