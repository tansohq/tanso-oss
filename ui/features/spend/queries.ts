import { useQuery } from "@tanstack/react-query"

import { apiFetch } from "@/lib/api/client"
import type { VendorConnectionDto } from "./types"

export function useVendorConnections() {
  return useQuery({
    queryKey: ["vendor-connections"],
    queryFn: () => apiFetch<VendorConnectionDto[]>("/api/v1/spend/connections"),
  })
}
