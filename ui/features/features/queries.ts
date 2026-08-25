import { useQuery } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import { newestFirst } from "@/lib/newest-first"
import type { FeatureDto } from "@/lib/api/types"

export function useFeatures() {
  return useQuery({
    queryKey: ["features"],
    queryFn: () => apiFetch<FeatureDto[]>("/api/v1/monetization/features"),
    select: newestFirst,
  })
}
