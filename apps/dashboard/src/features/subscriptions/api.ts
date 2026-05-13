import { api } from '@/lib/api'
import type {
  SubscriptionsResponse,
  SubscriptionDetailResponse,
  CreateSubscription,
  FetchSubscriptionsParams
} from './types'

const emptyResponse: SubscriptionsResponse = {
  data: []
}

export async function fetchSubscriptions(
  params: FetchSubscriptionsParams = {}
): Promise<SubscriptionsResponse> {
  try {
    const searchParams = new URLSearchParams()

    if (params.page !== undefined) {
      searchParams.set('page', params.page.toString())
    }
    if (params.size !== undefined) {
      searchParams.set('size', params.size.toString())
    }

    const queryString = searchParams.toString()
    const endpoint = `/api/v1/monetization/subscriptions${queryString ? `?${queryString}` : ''}`

    return await api.get<SubscriptionsResponse>(endpoint)
  } catch (error) {
    const status = (error as Error & { response?: { status: number } })?.response?.status
    // Return empty response for 404 (no subscriptions exist yet)
    if (status === 404) {
      return emptyResponse
    }
    throw error
  }
}

export async function getSubscription(id: string): Promise<SubscriptionDetailResponse> {
  return api.get<SubscriptionDetailResponse>(`/api/v1/monetization/subscriptions/${id}`)
}

export async function createSubscription(
  data: CreateSubscription
): Promise<SubscriptionDetailResponse> {
  return api.post<SubscriptionDetailResponse>('/api/v1/monetization/subscriptions', data)
}

export async function cancelSubscription(id: string, cancelMode: 'IMMEDIATE' | 'END_OF_PERIOD'): Promise<void> {
  return api.post<void>(`/api/v1/monetization/subscriptions/${id}/cancel?cancelMode=${cancelMode}`)
}

export async function activateSubscription(id: string): Promise<void> {
  return api.post<void>(`/api/v1/monetization/subscriptions/${id}/activate`)
}

export async function undoScheduledCancellation(id: string): Promise<void> {
  return api.delete<void>(`/api/v1/monetization/subscriptions/${id}/scheduled-cancellation`)
}

// TODO: This endpoint is not yet documented in the OpenAPI spec
// It may need to be updated when the backend adds customer-specific subscription filtering
export async function fetchCustomerSubscriptions(
  customerUUID: string
): Promise<SubscriptionsResponse> {
  try {
    return await api.get<SubscriptionsResponse>(
      `/api/v1/monetization/subscriptions/customer/${customerUUID}/`
    )
  } catch (error) {
    const status = (error as Error & { response?: { status: number } })?.response?.status
    // Return empty response for 404/500 (no subscriptions for this customer)
    if (status === 404 || status === 500) {
      return emptyResponse
    }
    throw error
  }
}
