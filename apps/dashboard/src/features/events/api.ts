import { api } from '@/lib/api'
import { fetchAccountApiKey } from '@/features/account/api'
import type {
  EventsResponse,
  FetchEventsParams,
  CreateEventRequest,
  CreateEventResponse,
  FetchGroupedEventsParams,
  GroupedEventsResponse
} from './types'

export async function fetchEvents(params: FetchEventsParams = {}): Promise<EventsResponse> {
  const searchParams = new URLSearchParams()

  if (params.page !== undefined) {
    searchParams.set('page', params.page.toString())
  }
  if (params.size !== undefined) {
    searchParams.set('size', params.size.toString())
  }
  if (params.customerReferenceId) {
    searchParams.set('customerReferenceId', params.customerReferenceId)
  }
  if (params.planId) {
    searchParams.set('planId', params.planId)
  }
  if (params.featureId) {
    searchParams.set('featureId', params.featureId)
  }
  if (params.start) {
    searchParams.set('start', params.start)
  }
  if (params.end) {
    searchParams.set('end', params.end)
  }
  if (params.eventType) {
    searchParams.set('eventType', params.eventType)
  }
  if (params.model) {
    searchParams.set('model', params.model)
  }
  if (params.modelProvider) {
    searchParams.set('modelProvider', params.modelProvider)
  }
  if (params.eventName) {
    searchParams.set('eventName', params.eventName)
  }

  const queryString = searchParams.toString()
  const endpoint = `/api/v1/tanso/events${queryString ? `?${queryString}` : ''}`

  return api.get<EventsResponse>(endpoint)
}

export async function createEvent(request: CreateEventRequest): Promise<CreateEventResponse> {
  const apiKeyResponse = await fetchAccountApiKey()
  const apiKey = apiKeyResponse.data?.apiKey
  if (!apiKey) {
    throw new Error('Unable to retrieve API key. Please ensure your account has an active API key.')
  }

  // Parse meta JSON string into object if provided
  let meta: Record<string, unknown> | undefined
  if (request.meta) {
    try {
      meta = JSON.parse(request.meta)
    } catch {
      throw new Error('Invalid JSON in metadata field')
    }
  }

  // Build costInput only if at least one field is set
  const costInput =
    request.costInput?.model ||
    request.costInput?.modelProvider ||
    request.costInput?.inputTokens ||
    request.costInput?.outputTokens ||
    request.costInput?.costUnits
      ? request.costInput
      : undefined

  const body = {
    featureKey: request.featureKey,
    featureId: request.featureId || undefined,
    eventName: request.eventName || undefined,
    customerReferenceId: request.customerReferenceId || undefined,
    eventIdempotencyKey: request.eventIdempotencyKey,
    occurredAt: request.occurredAt,
    usageUnits: request.usageUnits,
    costAmount: request.costAmount,
    costInput,
    meta
  }

  const response = await fetch(`${api.getBaseUrl()}/api/v1/client/events`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${apiKey}`
    },
    body: JSON.stringify(body)
  })

  if (!response.ok) {
    let errorData: unknown = null
    try {
      errorData = await response.json()
    } catch {
      // Response may not have JSON body
    }

    const errorMessage =
      (errorData as { error?: { detail?: string } })?.error?.detail ||
      (errorData as { detail?: string })?.detail ||
      (errorData as { error?: { message?: string } })?.error?.message ||
      (errorData as { message?: string })?.message ||
      (errorData as { error?: string })?.error ||
      response.statusText

    const error = new Error(errorMessage) as Error & {
      response?: { status: number; data: unknown }
    }
    error.response = { status: response.status, data: errorData }
    throw error
  }

  return response.json()
}

export async function fetchGroupedEvents(
  params: FetchGroupedEventsParams
): Promise<GroupedEventsResponse> {
  const searchParams = new URLSearchParams()
  searchParams.set('groupBy', params.groupBy)
  if (params.start) searchParams.set('start', params.start)
  if (params.end) searchParams.set('end', params.end)
  if (params.customerReferenceId)
    searchParams.set('customerReferenceId', params.customerReferenceId)
  if (params.planId) searchParams.set('planId', params.planId)
  if (params.featureId) searchParams.set('featureId', params.featureId)
  if (params.eventType) searchParams.set('eventType', params.eventType)
  if (params.model) searchParams.set('model', params.model)
  if (params.modelProvider) searchParams.set('modelProvider', params.modelProvider)
  if (params.eventName) searchParams.set('eventName', params.eventName)

  return api.get<GroupedEventsResponse>(`/api/v1/tanso/events/grouped?${searchParams.toString()}`)
}
