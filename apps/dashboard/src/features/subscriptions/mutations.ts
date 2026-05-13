import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useToast } from '@/components/ui/toast/use-toast'
import { createSubscription, activateSubscription, cancelSubscription, undoScheduledCancellation } from './api'
import type { CreateSubscription } from './types'

export function useCreateSubscriptionMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: CreateSubscription) => createSubscription(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['subscriptions'] })
    },
    onError: (error) => {
      console.error('Failed to create subscription:', error)
      toast({
        title: 'Something went wrong',
        description: 'Failed to create subscription. Please try again.',
        variant: 'destructive',
      })
    }
  })
}

export function useActivateSubscriptionMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (id: string) => activateSubscription(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['subscriptions'] })
      queryClient.invalidateQueries({ queryKey: ['subscription'] })
    },
    onError: (error) => {
      console.error('Failed to activate subscription:', error)
      toast({
        title: 'Something went wrong',
        description: 'Failed to activate subscription. Please try again.',
        variant: 'destructive',
      })
    }
  })
}

export function useCancelSubscriptionMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: ({ id, cancelMode }: { id: string; cancelMode: 'IMMEDIATE' | 'END_OF_PERIOD' }) =>
      cancelSubscription(id, cancelMode),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['subscriptions'] })
      queryClient.invalidateQueries({ queryKey: ['subscription'] })
    },
    onError: (error) => {
      console.error('Failed to cancel subscription:', error)
      toast({
        title: 'Something went wrong',
        description: 'Failed to cancel subscription. Please try again.',
        variant: 'destructive',
      })
    }
  })
}

export function useUndoScheduledCancellationMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (id: string) => undoScheduledCancellation(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['subscriptions'] })
      queryClient.invalidateQueries({ queryKey: ['subscription'] })
    },
    onError: (error) => {
      console.error('Failed to undo scheduled cancellation:', error)
      toast({
        title: 'Something went wrong',
        description: 'Failed to undo scheduled cancellation. Please try again.',
        variant: 'destructive',
      })
    }
  })
}
