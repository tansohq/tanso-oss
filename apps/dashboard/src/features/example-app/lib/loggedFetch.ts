import { useApiActivityLog } from '../composables/useApiActivityLog'

function parseHeaders(init?: RequestInit): Record<string, string> {
  const result: Record<string, string> = {}
  if (!init?.headers) return result
  const headers = init.headers
  if (headers instanceof Headers) {
    headers.forEach((value, key) => {
      result[key] = key.toLowerCase() === 'authorization' ? redactBearer(value) : value
    })
  } else if (Array.isArray(headers)) {
    for (const [key, value] of headers) {
      result[key] = key.toLowerCase() === 'authorization' ? redactBearer(value) : value
    }
  } else {
    for (const [key, value] of Object.entries(headers)) {
      result[key] = key.toLowerCase() === 'authorization' ? redactBearer(value) : value
    }
  }
  return result
}

function redactBearer(value: string): string {
  if (value.startsWith('Bearer ') && value.length > 15) {
    return `Bearer ${value.slice(7, 11)}...${value.slice(-4)}`
  }
  return value
}

function parseRequestBody(init?: RequestInit): unknown {
  if (!init?.body) return undefined
  if (typeof init.body === 'string') {
    try {
      return JSON.parse(init.body)
    } catch {
      return init.body
    }
  }
  return undefined
}

export async function loggedFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const { addLog } = useApiActivityLog()

  const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
  const method = init?.method?.toUpperCase() ?? 'GET'
  const id = `${Date.now()}_${Math.random().toString(36).slice(2)}`
  const start = performance.now()

  addLog({
    id,
    timestamp: new Date().toISOString(),
    method,
    endpoint: url,
    status: 'pending',
    requestHeaders: parseHeaders(init),
    requestBody: parseRequestBody(init)
  })

  const response = await fetch(input, init)
  const duration = Math.round(performance.now() - start)

  // Clone so the original response body remains consumable by the caller
  const cloned = response.clone()
  let responseBody: unknown
  try {
    responseBody = await cloned.json()
  } catch {
    responseBody = undefined
  }

  addLog({
    id,
    timestamp: new Date().toISOString(),
    method,
    endpoint: url,
    status: response.ok ? 'success' : 'error',
    statusCode: response.status,
    duration,
    responseBody
  })

  return response
}
