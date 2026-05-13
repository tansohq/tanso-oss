import { useQuery } from '@tanstack/vue-query'
import { fetchCustomers, getCustomer } from './api'
import type { MaybeRef } from 'vue'
import { toValue } from 'vue'

export function useCustomersQuery() {
  return useQuery({
    queryKey: ['customers'],
    queryFn: fetchCustomers
  })
}

export function useCustomerQuery(customerId: MaybeRef<string>) {
  return useQuery({
    queryKey: ['customer', customerId],
    queryFn: () => getCustomer(toValue(customerId)),
    enabled: !!toValue(customerId)
  })
}
