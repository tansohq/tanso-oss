import { useQuery } from "@tanstack/react-query"

import { apiFetch, queryString } from "@/lib/api/client"
import type {
  CreditPoolDto,
  CustomerBulkResponse,
  CustomerDto,
  EventGroupDto,
  PagedResponseEventDto,
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

export function useCustomerEvents(customerReferenceId: string | undefined) {
  return useQuery({
    queryKey: ["events", { customerReferenceId, page: 0, size: 10 }],
    queryFn: () =>
      apiFetch<PagedResponseEventDto>(
        `/api/v1/tanso/events${queryString({ page: 0, size: 10, customerReferenceId })}`,
      ),
    enabled: !!customerReferenceId,
  })
}

export function useCustomerUsageTotals(customerId: string, customerReferenceId?: string | null) {
  return useQuery({
    queryKey: ["events", "grouped", "CUSTOMER"],
    queryFn: () => apiFetch<EventGroupDto[]>("/api/v1/tanso/events/grouped?groupBy=CUSTOMER"),
    select: (groups) =>
      groups.find((g) => g.groupKey === customerReferenceId || g.groupKey === customerId),
  })
}
