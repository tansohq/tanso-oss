import { useQuery } from "@tanstack/react-query"

import { apiFetch, queryString } from "@/lib/api/client"
import type { EventGroupDto, PagedResponseEventDto } from "@/lib/api/types"

export type EventFilters = {
  page: number
  size: number
  eventName?: string
  customerReferenceId?: string
  model?: string
}

export function useEvents(filters: EventFilters) {
  return useQuery({
    queryKey: ["events", filters],
    queryFn: () =>
      apiFetch<PagedResponseEventDto>(`/api/v1/tanso/events${queryString(filters)}`),
    placeholderData: (previous) => previous,
  })
}

export type EventGroupBy = "MODEL" | "MODEL_PROVIDER" | "CUSTOMER" | "FEATURE" | "EVENT_NAME"

export function useGroupedEvents(groupBy: EventGroupBy) {
  return useQuery({
    queryKey: ["events", "grouped", groupBy],
    queryFn: () => apiFetch<EventGroupDto[]>(`/api/v1/tanso/events/grouped?groupBy=${groupBy}`),
  })
}
