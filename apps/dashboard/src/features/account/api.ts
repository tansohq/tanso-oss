import { api } from '@/lib/api'
import { env } from '@/lib/env'
import type {
  AccountApiKeyResponse,
  SubscriptionStatusResponse,
  SubscribeToPlanRequest,
  ChangePasswordRequest
} from './types'

export interface CsvImportResult {
  totalRows: number
  imported: number
}

export interface CsvUploadInfo {
  id: string
  fileName: string
  rowCount: number
  headers: string
  createdAt: string
}

export async function fetchCsvUploads(): Promise<{ success: boolean; data: CsvUploadInfo[] }> {
  return api.get('/api/v1/tanso/csv-import')
}

export async function uploadCsv(file: File): Promise<{ success: boolean; data: CsvImportResult }> {
  const formData = new FormData()
  formData.append('file', file)

  const token = api.getToken()
  const response = await fetch(`${env.apiBaseUrl}/api/v1/tanso/csv-import`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: formData
  })

  if (!response.ok) {
    const errorData = await response.json().catch(() => null)
    throw new Error(errorData?.message || errorData?.error?.message || 'CSV upload failed')
  }

  return response.json()
}

export async function deleteCsvUpload(id: string): Promise<{ success: boolean }> {
  return api.delete(`/api/v1/tanso/csv-import/${id}`)
}

export async function fetchAccountApiKey(): Promise<AccountApiKeyResponse> {
  return api.get<AccountApiKeyResponse>('/api/v1/account/api-key')
}

export async function rotateAccountApiKey(): Promise<AccountApiKeyResponse> {
  return api.post<AccountApiKeyResponse>('/api/v1/account/api-key')
}

export async function fetchSubscriptionStatus(): Promise<SubscriptionStatusResponse> {
  return api.get<SubscriptionStatusResponse>('/api/v1/account/subscription-status')
}

export async function subscribeToPlan(
  request: SubscribeToPlanRequest
): Promise<{ success: boolean }> {
  return api.post<{ success: boolean }>('/api/v1/account/subscribe', request)
}

export async function changePassword(
  request: ChangePasswordRequest
): Promise<{ success: boolean }> {
  return api.post<{ success: boolean }>('/api/v1/account/change-password', request)
}

export async function requestFeature(feature: string): Promise<{ success: boolean }> {
  return api.post<{ success: boolean }>('/api/v1/account/feature-request', { feature })
}
