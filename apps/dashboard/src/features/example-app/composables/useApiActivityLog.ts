import { ref } from 'vue'

export interface ApiLog {
  id: string
  timestamp: string
  method: string
  endpoint: string
  status: 'pending' | 'success' | 'error'
  statusCode?: number
  duration?: number
  requestHeaders?: Record<string, string>
  requestBody?: unknown
  responseBody?: unknown
}

const MAX_LOGS = 50

// Module-level state (singleton, shared across all components)
const logs = ref<ApiLog[]>([])
const collapsed = ref(sessionStorage.getItem('api-activity-collapsed') !== 'false')

export function useApiActivityLog() {
  function addLog(log: ApiLog) {
    const existingIndex = logs.value.findIndex((l) => l.id === log.id)
    if (existingIndex >= 0) {
      logs.value[existingIndex] = { ...logs.value[existingIndex], ...log }
    } else {
      logs.value = [log, ...logs.value].slice(0, MAX_LOGS)
    }
  }

  function clearLogs() {
    logs.value = []
  }

  function toggleCollapsed() {
    collapsed.value = !collapsed.value
    sessionStorage.setItem('api-activity-collapsed', String(collapsed.value))
  }

  return {
    logs,
    collapsed,
    addLog,
    clearLogs,
    toggleCollapsed
  }
}
