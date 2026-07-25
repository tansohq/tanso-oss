import { useMutation, useQueryClient } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type { SubscriptionInput } from "./schemas"

export function useCreateSubscription() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: SubscriptionInput) =>
      apiFetch<unknown>("/api/v1/monetization/subscriptions", {
        method: "POST",
        body: JSON.stringify(input),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["subscriptions"] }),
  })
}
