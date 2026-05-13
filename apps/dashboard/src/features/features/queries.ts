import { computed, toRef, type MaybeRef } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { fetchFeatures, getFeature } from './api'
import type { FetchFeaturesParams } from './types'

export function useFeaturesQuery(params: FetchFeaturesParams = {}) {
  return useQuery({
    queryKey: ['features', params],
    queryFn: () => fetchFeatures(params)
  })
}

export function useFeatureQuery(uuid: MaybeRef<string>) {
  const uuidRef = toRef(uuid)
  return useQuery({
    queryKey: computed(() => ['feature', uuidRef.value]),
    queryFn: () => getFeature(uuidRef.value),
    enabled: computed(() => !!uuidRef.value)
  })
}
