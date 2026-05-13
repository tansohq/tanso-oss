import { api } from '@/lib/api'
import { fetchAccountApiKey } from '@/features/account/api'
import { loggedFetch } from './lib/loggedFetch'

let cachedApiKey: string | null = null

async function getApiKey(): Promise<string> {
  if (cachedApiKey) return cachedApiKey
  const response = await fetchAccountApiKey()
  const apiKey = response.data?.apiKey
  if (!apiKey) {
    throw new Error('Unable to retrieve API key.')
  }
  cachedApiKey = apiKey
  return apiKey
}

export function clearCachedApiKey() {
  cachedApiKey = null
}

export interface EntitlementData {
  referenceCustomerId: string
  featureKey: string
  allowed: boolean
  meta?: {
    reason?: {
      description?: string
    }
  }
  usage?: {
    used: number
    limit: number | null
    remaining: number | null
  }
}

interface EntitlementApiResponse {
  data?: EntitlementData
  success?: boolean
}

export async function checkEntitlement(
  customerReferenceId: string,
  featureKey: string,
  record: boolean = true
): Promise<EntitlementData> {
  const apiKey = await getApiKey()
  const recordParam = record ? '' : '?record=false'
  const response = await loggedFetch(
    `${api.getBaseUrl()}/api/v1/client/entitlements/${encodeURIComponent(customerReferenceId)}/${encodeURIComponent(featureKey)}${recordParam}`,
    {
      headers: {
        Authorization: `Bearer ${apiKey}`
      }
    }
  )
  if (!response.ok) {
    throw new Error(`Entitlement check failed: ${response.statusText}`)
  }
  const body: EntitlementApiResponse = await response.json()
  if (!body.data) {
    throw new Error('Entitlement response missing data')
  }
  return body.data
}

export interface TrackEventParams {
  featureKey: string
  customerReferenceId: string
  eventName: string
  usageUnits?: number
  subscriptionId?: string
}

export interface EventIngestionData {
  usageLimitExceeded?: boolean
  message?: string
}

interface EventApiResponse {
  data?: EventIngestionData
  success?: boolean
}

export async function trackEvent(params: TrackEventParams): Promise<EventIngestionData> {
  const apiKey = await getApiKey()
  const response = await loggedFetch(`${api.getBaseUrl()}/api/v1/client/events`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${apiKey}`
    },
    body: JSON.stringify({
      featureKey: params.featureKey,
      customerReferenceId: params.customerReferenceId,
      eventName: params.eventName,
      eventIdempotencyKey: `example_${Date.now()}_${Math.random().toString(36).slice(2)}`,
      occurredAt: new Date().toISOString(),
      usageUnits: params.usageUnits ?? 1,
      ...(params.subscriptionId ? { subscriptionId: params.subscriptionId } : {})
    })
  })
  if (!response.ok) {
    throw new Error(`Event tracking failed: ${response.statusText}`)
  }
  const body: EventApiResponse = await response.json()
  return body.data ?? {}
}
