interface ApiErrorResponse {
  message?: string
  error?: string | { message?: string; detail?: string }
  statusCode?: number
  errors?: Array<{ message: string; field?: string; constraint?: string }>
  details?: Array<{ message: string; field?: string }>
  validationErrors?: Record<string, string[]>
}

export interface ParsedError {
  message: string
  type:
    | 'duplicate'
    | 'permission'
    | 'not_found'
    | 'validation'
    | 'network'
    | 'unauthorized'
    | 'rate_limit'
    | 'server_error'
    | 'unknown'
}

export function parseApiError(error: unknown): ParsedError {
  const response = extractErrorResponse(error)

  if (error instanceof TypeError && (
    error.message === 'Failed to fetch' ||
    error.message === 'Network request failed' ||
    error.message === 'Load failed' ||
    error.message.includes('NetworkError')
  )) {
    return {
      message: 'Network error. Please check your connection and try again.',
      type: 'network'
    }
  }

  const statusCode = response?.statusCode || extractStatusCode(error)
  // Handle nested error: { error: { message: "...", detail: "..." } } or { message: "..." } or { error: "..." }
  const nestedError = typeof response?.error === 'object' ? response.error : null
  const errorDetail = nestedError?.detail
  const nestedMessage =
    nestedError?.message || (typeof response?.error === 'string' ? response.error : null)

  // Prefer detail over generic "Bad Request" messages
  const errorMessage =
    errorDetail ||
    (nestedMessage && !nestedMessage.toLowerCase().includes('bad request')
      ? nestedMessage
      : null) ||
    response?.message ||
    (error instanceof Error ? error.message : '') ||
    ''
  const lowerErrorMessage = errorMessage.toLowerCase()

  // Check message content FIRST (before status codes) for better error detection
  if (
    lowerErrorMessage.includes('duplicate') ||
    lowerErrorMessage.includes('already exists') ||
    lowerErrorMessage.includes('unique constraint')
  ) {
    return {
      message:
        extractDuplicateMessage(errorMessage) ||
        'This item already exists. Please use a different identifier.',
      type: 'duplicate'
    }
  }

  if (statusCode === 409) {
    return {
      message:
        extractDuplicateMessage(errorMessage) ||
        'This item already exists. Please use a different identifier.',
      type: 'duplicate'
    }
  }

  if (statusCode === 401) {
    return {
      message: 'Your session has expired. Please sign in again.',
      type: 'unauthorized'
    }
  }

  if (statusCode === 429) {
    return {
      message: "You're making requests too quickly. Please wait a moment and try again.",
      type: 'rate_limit'
    }
  }

  if (statusCode === 403) {
    return {
      message:
        errorMessage ||
        "You don't have permission to perform this action. Contact your administrator if you need access.",
      type: 'permission'
    }
  }

  if (statusCode === 404) {
    return {
      message: 'This item could not be found. It may have been deleted.',
      type: 'not_found'
    }
  }

  if (statusCode === 400 || response?.errors?.length) {
    const validationMessage = response?.errors?.[0]?.message || errorMessage

    // Try to show the actual error; only fall back to generic if truly nothing useful
    if (validationMessage && !isBareMinimalMessage(validationMessage)) {
      return {
        message: validationMessage,
        type: 'validation'
      }
    }

    // Check for detail field directly on the response data
    const detail = (response as Record<string, unknown>)?.detail as string | undefined
    if (detail) {
      return { message: detail, type: 'validation' }
    }

    // Also check the original Error message (api.ts may have extracted a detail)
    const originalMessage = error instanceof Error ? error.message : ''
    if (originalMessage && !isBareMinimalMessage(originalMessage)) {
      return { message: originalMessage, type: 'validation' }
    }

    return {
      message: 'Please check your input and try again.',
      type: 'validation'
    }
  }

  if (statusCode && statusCode >= 500) {
    return {
      message: 'Something went wrong on our end. Please try again in a few minutes.',
      type: 'server_error'
    }
  }

  if (errorMessage) {
    return {
      message: errorMessage,
      type: 'unknown'
    }
  }

  return {
    message: 'Something went wrong. Please try again, or contact support if this continues.',
    type: 'unknown'
  }
}

function extractErrorResponse(error: unknown): ApiErrorResponse | null {
  if (!error || typeof error !== 'object') return null

  if ('response' in error && error.response && typeof error.response === 'object') {
    const response = error.response as Record<string, unknown>
    if ('data' in response && response.data && typeof response.data === 'object') {
      return response.data as ApiErrorResponse
    }
    return response as ApiErrorResponse
  }

  if ('message' in error || 'error' in error || 'statusCode' in error) {
    return error as ApiErrorResponse
  }

  return null
}

function extractStatusCode(error: unknown): number | null {
  if (!error || typeof error !== 'object') return null

  if ('response' in error && error.response && typeof error.response === 'object') {
    const response = error.response as Record<string, unknown>
    if ('status' in response && typeof response.status === 'number') {
      return response.status
    }
  }

  if ('status' in error && typeof error.status === 'number') {
    return error.status
  }

  return null
}

function extractDuplicateMessage(errorMessage: string): string | null {
  const lowerMessage = errorMessage.toLowerCase()

  // Extract key value if present
  const keyMatch = errorMessage.match(/key[:\s]+["']?([^"'\s,]+)["']?/i)
  const keyValue = keyMatch ? keyMatch[1] : null

  // Plan-specific duplicate
  if (lowerMessage.includes('plan')) {
    if (keyValue) {
      return `A plan with the key "${keyValue}" already exists. Please choose a different key.`
    }
    return 'A plan with this key already exists. Please choose a different key.'
  }

  // Feature-specific duplicate
  if (lowerMessage.includes('feature')) {
    if (keyValue) {
      return `A feature with the key "${keyValue}" already exists. Please choose a different key.`
    }
    return 'A feature with this key already exists. Please choose a different key.'
  }

  // Customer email duplicate
  if (lowerMessage.includes('email')) {
    return 'A customer with this email already exists.'
  }

  // Customer reference ID duplicate
  if (lowerMessage.includes('reference') || lowerMessage.includes('externalclientcustomerid')) {
    return 'A customer with this reference ID already exists.'
  }

  // Generic key duplicate
  if (keyValue) {
    return `An item with the key "${keyValue}" already exists. Please choose a different key.`
  }

  return null
}

function isBareMinimalMessage(msg: string): boolean {
  const lower = msg.toLowerCase().trim()
  return (
    !lower ||
    lower === 'bad request' ||
    lower.startsWith('bad request errorid=') ||
    lower.startsWith('bad request (errorid=') ||
    /^errorid=[a-z0-9-]+$/i.test(lower)
  )
}
