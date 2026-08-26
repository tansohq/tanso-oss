import { useQuery } from "@tanstack/react-query"

import { apiFetch, isModuleOff } from "@/lib/api/client"
import type {
  AnalyticsResponseDto,
  ModelsAnalyticsResponseDto,
  RevenueBridgeResponseDto,
} from "@/lib/api/types"

export function usePortfolio() {
  return useQuery({
    queryKey: ["analytics", "portfolio"],
    queryFn: () => apiFetch<AnalyticsResponseDto>("/api/v1/analytics/portfolio"),
    retry: (count, error) => !isModuleOff(error) && count < 3,
  })
}

export function useRevenueBridge(periods = 6) {
  return useQuery({
    queryKey: ["analytics", "revenue-bridge", periods],
    queryFn: () =>
      apiFetch<RevenueBridgeResponseDto>(`/api/v1/analytics/revenue-bridge?periods=${periods}`),
  })
}

export function useModelsAnalytics() {
  return useQuery({
    queryKey: ["analytics", "models"],
    queryFn: () => apiFetch<ModelsAnalyticsResponseDto>("/api/v1/analytics/models"),
  })
}
