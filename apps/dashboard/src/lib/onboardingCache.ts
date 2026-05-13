import { fetchIntakeData, fetchOnboardingProgress } from '@/features/onboarding/api'
import { fetchAccountSettings } from '@/features/integrations/api'

interface OnboardingStatus {
  intakeCompleted: boolean
  modeSelected: boolean
  isObserveMode: boolean
}

let onboardingCache: { status: OnboardingStatus; timestamp: number } | null = null
const CACHE_TTL_MS = 60000

export async function checkOnboardingStatus(): Promise<OnboardingStatus> {
  if (onboardingCache && Date.now() - onboardingCache.timestamp < CACHE_TTL_MS) {
    return onboardingCache.status
  }

  try {
    const [intakeResponse, progressResponse, settingsResponse] = await Promise.all([
      fetchIntakeData(),
      fetchOnboardingProgress(),
      fetchAccountSettings()
    ])

    const steps = progressResponse.data?.completedSteps ?? []
    const intakeCompleted =
      intakeResponse.data != null ||
      steps.includes('intake_completed') ||
      steps.includes('intake_skipped')
    const modeSelected = steps.includes('mode_selected')
    const isObserveMode = settingsResponse.data.platformMode !== 'FULL'

    const status = { intakeCompleted, modeSelected, isObserveMode }
    onboardingCache = { status, timestamp: Date.now() }
    try { localStorage.setItem('tanso_last_observe_mode', String(isObserveMode)) } catch { /* ignore */ }
    return status
  } catch (error) {
    console.error('Failed to check onboarding status:', error)
    const lastKnown = (() => { try { return localStorage.getItem('tanso_last_observe_mode') === 'true' } catch { return false } })()
    return { intakeCompleted: false, modeSelected: false, isObserveMode: lastKnown }
  }
}

export function clearOnboardingCache() {
  onboardingCache = null
}
