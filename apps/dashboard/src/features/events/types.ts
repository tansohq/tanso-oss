import { z } from 'zod'
import { eventSchema, pagedEventsSchema, eventsResponseSchema, createEventSchema } from './schemas'

export type Event = z.infer<typeof eventSchema>
export type PagedEvents = z.infer<typeof pagedEventsSchema>
export type EventsResponse = z.infer<typeof eventsResponseSchema>
export type CreateEventRequest = z.infer<typeof createEventSchema>

export interface CreateEventResponse {
  success: boolean
  data?: { usageLimitExceeded?: boolean; message?: string }
}

export interface FetchEventsParams {
  page?: number
  size?: number
  customerReferenceId?: string
  planId?: string
  featureId?: string
  start?: string
  end?: string
  eventType?: string
  model?: string
  modelProvider?: string
  eventName?: string
}

export type GroupBy = 'MODEL' | 'MODEL_PROVIDER' | 'CUSTOMER' | 'FEATURE' | 'EVENT_NAME'

export interface EventGroup {
  groupKey: string
  groupLabel: string
  eventCount: number
  totalCost: number
  totalRevenue: number
  totalUsageUnits: number
  lastOccurredAt: string
}

export interface FetchGroupedEventsParams {
  groupBy: GroupBy
  start?: string
  end?: string
  customerReferenceId?: string
  planId?: string
  featureId?: string
  eventType?: string
  model?: string
  modelProvider?: string
  eventName?: string
}

export interface GroupedEventsResponse {
  success: boolean
  data: EventGroup[]
}
