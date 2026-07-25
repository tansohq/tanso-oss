import { useQuery } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type {
  CustomerBulkResponse,
  SubscriptionDto,
  SubscriptionScheduledChangeDto,
} from "@/lib/api/types"

export function useSubscriptions() {
  return useQuery({
    queryKey: ["subscriptions"],
    queryFn: () => apiFetch<SubscriptionDto[]>("/api/v1/monetization/subscriptions"),
  })
}

export function useScheduledChanges() {
  return useQuery({
    queryKey: ["subscriptions", "scheduled-changes"],
    queryFn: () =>
      apiFetch<SubscriptionScheduledChangeDto[]>(
        "/api/v1/monetization/subscriptions/scheduled-changes",
      ),
  })
}

export function useScheduledCancellations() {
  return useQuery({
    queryKey: ["subscriptions", "scheduled-cancellations"],
    queryFn: () =>
      apiFetch<SubscriptionDto[]>("/api/v1/monetization/subscriptions/scheduled-cancellations"),
  })
}

export function useCustomerOptions() {
  return useQuery({
    queryKey: ["customers"],
    queryFn: () => apiFetch<CustomerBulkResponse>("/api/v1/monetization/customers"),
  })
}
