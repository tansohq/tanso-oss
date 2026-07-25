import { useQuery } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type {
  CreditPoolDto,
  CustomerBulkResponse,
  CustomerDto,
  SubscriptionDto,
} from "@/lib/api/types"

export function useCustomers() {
  return useQuery({
    queryKey: ["customers"],
    queryFn: () => apiFetch<CustomerBulkResponse>("/api/v1/monetization/customers"),
  })
}

export function useCustomer(customerId: string) {
  return useQuery({
    queryKey: ["customers", customerId],
    queryFn: () => apiFetch<CustomerDto>(`/api/v1/monetization/customers/${customerId}`),
  })
}

export function useCustomerSubscriptions(customerId: string) {
  return useQuery({
    queryKey: ["customers", customerId, "subscriptions"],
    queryFn: () =>
      apiFetch<SubscriptionDto[]>(`/api/v1/monetization/subscriptions/customer/${customerId}/`),
  })
}

export function useCustomerCreditPools(customerId: string) {
  return useQuery({
    queryKey: ["customers", customerId, "pools"],
    queryFn: () =>
      apiFetch<CreditPoolDto[]>(`/api/v1/monetization/credits/pools/customer/${customerId}`),
  })
}
