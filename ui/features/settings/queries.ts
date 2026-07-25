import { useQuery } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type { AccountApiKeyResponse, AccountSettingDto } from "@/lib/api/types"

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
