import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useToast } from '@/components/ui/toast/use-toast'
import { createPlan, updatePlan, activatePlan, archivePlan, restorePlan, deletePlan, duplicatePlan, type DuplicatePlanResult } from './api'
import type { CreatePlan, UpdatePlan, PlanFeatureLinkedResponse } from './types'

export function useCreatePlanMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: CreatePlan) => createPlan(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plans'] })
    },
    onError: (error) => {
      console.error('Failed to create plan:', error)
      toast({
        title: 'Something went wrong',
        description: 'Failed to create plan. Please try again.',
        variant: 'destructive',
      })
    }
  })
}

export function useUpdatePlanMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: ({ uuid, data }: { uuid: string; data: UpdatePlan }) => updatePlan(uuid, data),
    onSuccess: (_result, { uuid }) => {
      queryClient.invalidateQueries({ queryKey: ['plans'] })
      queryClient.invalidateQueries({ queryKey: ['plan', uuid] })
    },
    onError: (error) => {
      console.error('Failed to update plan:', error)
      toast({
        title: 'Something went wrong',
        description: 'Failed to update plan. Please try again.',
        variant: 'destructive',
      })
    }
  })
}

export function useUpdateBasePriceMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: ({ uuid, data }: { uuid: string; data: UpdatePlan }) => updatePlan(uuid, data),
    onSuccess: async (_result, { uuid }) => {
      queryClient.invalidateQueries({ queryKey: ['plans'] })
      queryClient.invalidateQueries({ queryKey: ['plan', uuid] })
      try {
        await queryClient.refetchQueries({ queryKey: ['plan-features', uuid] })
      } catch (e) {
        console.error('Failed to refresh plan features after price update:', e)
      }
    },
    onError: (error) => {
      console.error('Failed to update base price:', error)
      toast({
        title: 'Something went wrong',
        description: 'Failed to update base price. Please try again.',
        variant: 'destructive',
      })
    }
  })
}

export function useActivatePlanMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (uuid: string) => activatePlan(uuid),
    onSuccess: (_data, uuid) => {
      queryClient.setQueryData<PlanFeatureLinkedResponse>(['plan-features', uuid], (old) => {
        if (old?.data?.plan) {
          return { ...old, data: { ...old.data, plan: { ...old.data.plan, status: 'active' } } }
        }
        return old
      })
      queryClient.invalidateQueries({ queryKey: ['plans'] })
      queryClient.invalidateQueries({ queryKey: ['plan', uuid] })
      queryClient.invalidateQueries({ queryKey: ['plan-features', uuid] })
    },
    onError: (error) => {
      console.error('Failed to activate plan:', error)
      toast({
        title: 'Something went wrong',
        description: 'Failed to activate plan. Please try again.',
        variant: 'destructive',
      })
    }
  })
}

export function useArchivePlanMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (uuid: string) => archivePlan(uuid),
    onSuccess: (_data, uuid) => {
      queryClient.setQueryData<PlanFeatureLinkedResponse>(['plan-features', uuid], (old) => {
        if (old?.data?.plan) {
          return { ...old, data: { ...old.data, plan: { ...old.data.plan, status: 'archived' } } }
        }
        return old
      })
      queryClient.invalidateQueries({ queryKey: ['plans'] })
      queryClient.invalidateQueries({ queryKey: ['plan', uuid] })
      queryClient.invalidateQueries({ queryKey: ['plan-features', uuid] })
    },
    onError: (error) => {
      console.error('Failed to archive plan:', error)
      toast({
        title: 'Something went wrong',
        description: 'Failed to archive plan. Please try again.',
        variant: 'destructive',
      })
    }
  })
}

export function useRestorePlanMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (uuid: string) => restorePlan(uuid),
    onSuccess: (_data, uuid) => {
      queryClient.setQueryData<PlanFeatureLinkedResponse>(['plan-features', uuid], (old) => {
        if (old?.data?.plan) {
          return { ...old, data: { ...old.data, plan: { ...old.data.plan, status: 'active' } } }
        }
        return old
      })
      queryClient.invalidateQueries({ queryKey: ['plans'] })
      queryClient.invalidateQueries({ queryKey: ['plan', uuid] })
      queryClient.invalidateQueries({ queryKey: ['plan-features', uuid] })
    },
    onError: (error) => {
      console.error('Failed to restore plan:', error)
      toast({
        title: 'Something went wrong',
        description: 'Failed to restore plan. Please try again.',
        variant: 'destructive',
      })
    }
  })
}

export function useDeletePlanMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (uuid: string) => deletePlan(uuid),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plans'] })
    },
    onError: (error) => {
      console.error('Failed to delete plan:', error)
      toast({
        title: 'Something went wrong',
        description: 'Failed to delete plan. Please try again.',
        variant: 'destructive',
      })
    }
  })
}

export function useDuplicatePlanMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation<DuplicatePlanResult, Error, string>({
    mutationFn: (sourcePlanId: string) => duplicatePlan(sourcePlanId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plans'] })
    },
    onError: (error) => {
      console.error('Failed to duplicate plan:', error)
      toast({
        title: 'Something went wrong',
        description: 'Failed to duplicate plan. Please try again.',
        variant: 'destructive',
      })
    }
  })
}
