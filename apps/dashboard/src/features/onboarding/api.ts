import { api } from '@/lib/api'
import { env } from '@/lib/env'
import type {
  IntakeData,
  IntakeDataResponse,
  OnboardingProgressResponse
} from './types'

const REPLICATED_STEPS = new Set(['intake_completed', 'intake_skipped', 'mode_selected'])

function replicateToOtherEnvironment(endpoint: string, data: unknown): void {
  const currentEnv = localStorage.getItem('tanso_environment') || 'production'

  let otherBaseUrl: string
  let otherToken: string | null

  if (currentEnv === 'sandbox') {
    otherBaseUrl = env.apiBaseUrl
    otherToken = localStorage.getItem('auth_token')
  } else {
    otherBaseUrl = env.sandboxApiBaseUrl || ''
    otherToken = localStorage.getItem('auth_token_sandbox')
  }

  if (!otherBaseUrl || !otherToken) return

  fetch(`${otherBaseUrl}${endpoint}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${otherToken}`
    },
    body: JSON.stringify(data)
  }).then(res => {
    if (res && !res.ok) {
      console.warn(`[onboarding] cross-env replication failed: ${res.status} ${endpoint}`)
    }
  }).catch((err) => {
    console.warn('[onboarding] cross-env replication error:', err)
  })
}

export async function saveIntakeData(data: IntakeData): Promise<{ success: boolean }> {
  const result = await api.post<{ success: boolean }>('/api/v1/tanso/account/onboarding/intake', data)
  replicateToOtherEnvironment('/api/v1/tanso/account/onboarding/intake', data)
  return result
}

export async function fetchIntakeData(): Promise<IntakeDataResponse> {
  return api.get<IntakeDataResponse>('/api/v1/tanso/account/onboarding/intake')
}

export async function fetchOnboardingProgress(): Promise<OnboardingProgressResponse> {
  return api.get<OnboardingProgressResponse>('/api/v1/tanso/account/onboarding/progress')
}

export async function completeOnboardingStep(stepKey: string): Promise<OnboardingProgressResponse> {
  const result = await api.post<OnboardingProgressResponse>('/api/v1/tanso/account/onboarding/progress/complete', {
    stepKey
  })
  if (REPLICATED_STEPS.has(stepKey)) {
    replicateToOtherEnvironment('/api/v1/tanso/account/onboarding/progress/complete', { stepKey })
  }
  return result
}
