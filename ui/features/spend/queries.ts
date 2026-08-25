import { useQuery } from "@tanstack/react-query"

import { ApiError, apiFetch } from "@/lib/api/client"
import type { VendorConnectionDto } from "./types"

/** True when the build side is switched off (APP_MODULES_BUILD_ENABLED=false): the routes 404. */
export function isBuildSideOff(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404
}

export function useVendorConnections() {
  return useQuery({
    queryKey: ["vendor-connections"],
    queryFn: () => apiFetch<VendorConnectionDto[]>("/api/v1/spend/connections"),
    retry: (count, error) => !isBuildSideOff(error) && count < 3,
  })
}
