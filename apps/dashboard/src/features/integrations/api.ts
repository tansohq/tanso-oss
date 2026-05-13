import { api } from '@/lib/api'
import type { AccountSettingResponse, StripeApiKeysResponse, UpdateAccountSetting } from './schemas'

export async function fetchAccountSettings(): Promise<AccountSettingResponse> {
  return api.get<AccountSettingResponse>('/api/v1/tanso/account-settings')
}

export async function updateAccountSettings(
  data: UpdateAccountSetting
): Promise<AccountSettingResponse> {
  return api.patch<AccountSettingResponse>('/api/v1/tanso/account-settings', data)
}

export async function fetchStripeKeys(): Promise<StripeApiKeysResponse> {
  return api.get<StripeApiKeysResponse>('/api/v1/data/stripe/api')
}

export async function registerStripeApiKey(clientStripeApiKey: string): Promise<void> {
  return api.post('/api/v1/data/stripe/api', { clientStripeApiKey })
}

export async function deleteStripeKeys(): Promise<void> {
  return api.delete('/api/v1/data/stripe/api')
}

export async function registerStripeWebhook(): Promise<void> {
  return api.post('/api/v1/data/stripe/webhook/register')
}

// Stripe Import API
export interface DiscoveredProduct {
  stripeProductId: string
  name: string
  description: string | null
  alreadyMapped: boolean
}

export interface DiscoveredCustomer {
  stripeCustomerId: string
  name: string | null
  email: string | null
  alreadyMapped: boolean
}

export interface DiscoveredSubscription {
  stripeSubscriptionId: string
  stripeCustomerId: string
  stripeProductId: string | null
  status: string
  alreadyMapped: boolean
}

export interface StripeDiscoveryResponse {
  products: DiscoveredProduct[]
  customers: DiscoveredCustomer[]
  subscriptions: DiscoveredSubscription[]
}

export interface StripeImportStatusResponse {
  jobId: string
  status: string
  totalItems: number
  processedItems: number
  failedItems: number
  errorDetails: string | null
  createdAt: string
  updatedAt: string
}

export interface ProductMapping {
  stripeProductId: string
  tansoPlanId: string
}

export interface CustomerMapping {
  stripeCustomerId: string
  tansoCustomerId: string | null
  autoCreate: boolean
}

export interface StripeImportStartRequest {
  productMappings: ProductMapping[]
  customerMappings: CustomerMapping[]
}

export async function discoverStripeObjects(request: { includeProducts: boolean; includeCustomers: boolean; includeSubscriptions: boolean }) {
  return api.post<{ data: StripeDiscoveryResponse; success: boolean }>('/api/v1/data/stripe/import/discover', request)
}

export async function startStripeImport(request: StripeImportStartRequest) {
  return api.post<{ data: StripeImportStatusResponse; success: boolean }>('/api/v1/data/stripe/import/start', request)
}

export async function getStripeImportStatus(jobId: string) {
  return api.get<{ data: StripeImportStatusResponse; success: boolean }>(`/api/v1/data/stripe/import/status/${jobId}`)
}

export async function mapStripeProduct(request: { stripeProductId: string; tansoPlanId: string }) {
  return api.post('/api/v1/data/stripe/import/map-product', request)
}

export async function startAutoCreateStripeImport() {
  return api.post<{ data: StripeImportStatusResponse; success: boolean }>('/api/v1/data/stripe/import/start-auto-create')
}

// Observe-mode Stripe sync
export interface StripeObserveSyncResponse {
  customersSynced: number
  plansSynced: number
  subscriptionsSynced: number
  errors: number
  warnings: string[]
}

export async function syncStripeObserve() {
  return api.post<{ data: StripeObserveSyncResponse; success: boolean }>('/api/v1/data/stripe/import/observe-sync')
}
