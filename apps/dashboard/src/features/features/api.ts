import { api } from '@/lib/api'
import type {
  FeaturesResponse,
  CreateFeature,
  UpdateFeature,
  FeatureDetailResponse,
  FetchFeaturesParams
} from './types'

export async function fetchFeatures(params: FetchFeaturesParams = {}): Promise<FeaturesResponse> {
  const searchParams = new URLSearchParams()

  if (params.page !== undefined) {
    searchParams.set('page', params.page.toString())
  }
  if (params.size !== undefined) {
    searchParams.set('size', params.size.toString())
  }

  const queryString = searchParams.toString()
  const endpoint = `/api/v1/monetization/features${queryString ? `?${queryString}` : ''}`

  return api.get<FeaturesResponse>(endpoint)
}

export async function createFeature(data: CreateFeature): Promise<FeatureDetailResponse> {
  return api.post<FeatureDetailResponse>('/api/v1/monetization/features', data)
}

export async function getFeature(uuid: string): Promise<FeatureDetailResponse> {
  return api.get<FeatureDetailResponse>(`/api/v1/monetization/features/${uuid}`)
}

export async function updateFeature(
  uuid: string,
  data: UpdateFeature
): Promise<FeatureDetailResponse> {
  return api.patch<FeatureDetailResponse>(`/api/v1/monetization/features/${uuid}`, data)
}

export async function deleteFeature(uuid: string): Promise<void> {
  return api.delete<void>(`/api/v1/monetization/features/${uuid}`)
}

export async function deleteFeaturesBulk(ids: string[]): Promise<void> {
  return api.delete<void>('/api/v1/monetization/features', { ids })
}
