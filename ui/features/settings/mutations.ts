import { useMutation, useQueryClient } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type { AccountSettingDto, StripeImportStatusResponse } from "@/lib/api/types"
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

export function useRegisterStripeKey() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (clientStripeApiKey: string) =>
      apiFetch<void>("/api/v1/data/stripe/api", {
        method: "POST",
        body: JSON.stringify({ clientStripeApiKey }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["stripe-keys"] }),
  })
}

export function useDeleteStripeKeys() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiFetch<void>("/api/v1/data/stripe/api", { method: "DELETE" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["stripe-keys"] }),
  })
}

export function useRegisterStripeWebhook() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () =>
      apiFetch<void>("/api/v1/data/stripe/webhook/register", { method: "POST" }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["stripe-keys"] }),
  })
}

export function useStartStripeImport() {
  return useMutation({
    mutationFn: () =>
      apiFetch<StripeImportStatusResponse>("/api/v1/data/stripe/import/start-auto-create", {
        method: "POST",
      }),
  })
}
