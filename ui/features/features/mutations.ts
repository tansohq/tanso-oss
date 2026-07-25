import { useMutation, useQueryClient } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type { FeatureDto } from "@/lib/api/types"
import type { FeatureInput } from "./schemas"

export function useCreateFeature() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: FeatureInput) =>
      apiFetch<FeatureDto>("/api/v1/monetization/features", {
        method: "POST",
        body: JSON.stringify(input),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["features"] }),
  })
}

export function useUpdateFeature(featureId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: FeatureInput) =>
      apiFetch<FeatureDto>(`/api/v1/monetization/features/${featureId}`, {
        method: "PATCH",
        body: JSON.stringify(input),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["features"] }),
  })
}
