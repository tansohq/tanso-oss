import { useMutation, useQueryClient } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type { AccountSettingDto } from "@/lib/api/types"
import type { AccountSettingsInput } from "./schemas"

export function useUpdateAccountSettings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: AccountSettingsInput) =>
      apiFetch<AccountSettingDto>("/api/v1/tanso/account-settings", {
        method: "PATCH",
        body: JSON.stringify({
          ...input,
          stripeCheckoutSuccessUrl: input.stripeCheckoutSuccessUrl || undefined,
          stripeCheckoutCancelUrl: input.stripeCheckoutCancelUrl || undefined,
        }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["settings"] }),
  })
}
