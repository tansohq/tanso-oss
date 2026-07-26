import { useQuery } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type {
  AccountApiKeyResponse,
  AccountSettingDto,
  StripeApiKeysResponse,
  StripeImportStatusResponse,
} from "@/lib/api/types"

export function useAccountSettings() {
  return useQuery({
    queryKey: ["settings"],
    queryFn: () => apiFetch<AccountSettingDto>("/api/v1/tanso/account-settings"),
  })
}

export function useApiKey() {
  return useQuery({
    queryKey: ["api-key"],
    queryFn: () => apiFetch<AccountApiKeyResponse>("/api/v1/account/api-key"),
  })
}

export function useStripeKeys() {
  return useQuery({
    queryKey: ["stripe-keys"],
    queryFn: () => apiFetch<StripeApiKeysResponse>("/api/v1/data/stripe/api"),
  })
}

const IMPORT_RUNNING = ["PENDING", "RUNNING", "IN_PROGRESS"]

export function useStripeImportStatus(jobId: string | null) {
  return useQuery({
    queryKey: ["stripe-import", jobId],
    queryFn: () =>
      apiFetch<StripeImportStatusResponse>(`/api/v1/data/stripe/import/status/${jobId}`),
    enabled: !!jobId,
    refetchInterval: (query) =>
      query.state.data && !IMPORT_RUNNING.includes(query.state.data.status ?? "")
        ? false
        : 2000,
  })
}
