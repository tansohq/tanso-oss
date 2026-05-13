import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { saveIntakeData, completeOnboardingStep } from './api'
import { updateAccountSettings } from '@/features/integrations/api'
import { clearOnboardingCache } from '@/lib/onboardingCache'
import { useToast } from '@/components/ui/toast/use-toast'
import type { IntakeData } from './types'
import type { PlatformMode } from '@/features/integrations/schemas'

export function useSaveIntakeMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: IntakeData) => saveIntakeData(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['onboarding', 'intake'] })
      clearOnboardingCache()
    },
    onError: () => {
      toast({
        title: 'Something went wrong',
        description: 'Failed to save your responses. Please try again.',
        variant: 'destructive'
      })
    }
  })
}

export function useUpdatePlatformModeMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (mode: PlatformMode) => updateAccountSettings({ platformMode: mode }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['account-settings'] })
      clearOnboardingCache()
    },
    onError: () => {
      toast({
        title: 'Something went wrong',
        description: 'Failed to save your mode selection. Please try again.',
        variant: 'destructive'
      })
    }
  })
}

export function useCompleteStepMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (stepKey: string) => completeOnboardingStep(stepKey),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['onboarding', 'progress'] })
      clearOnboardingCache()
    },
    onError: () => {
      toast({
        title: 'Something went wrong',
        description: 'Please try again.',
        variant: 'destructive'
      })
    }
  })
}
