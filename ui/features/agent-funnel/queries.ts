import { useQuery } from "@tanstack/react-query"

import { apiFetch, isModuleOff, queryString } from "@/lib/api/client"
import type { AgentFunnelDto } from "./types"

export function useAgentFunnel(from: string, to: string) {
  return useQuery({
    queryKey: ["agent-funnel", from, to],
    queryFn: () =>
      apiFetch<AgentFunnelDto>(
        `/api/v1/tanso/agent-funnel${queryString({ from, to })}`
      ),
    enabled: !!from && !!to,
    retry: (count, error) => !isModuleOff(error) && count < 3,
  })
}
