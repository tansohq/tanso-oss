import { useMutation, useQueryClient } from '@tanstack/vue-query'
import {
  syncPlanFeatures,
  createPlanFeatureRule,
  updatePlanFeatureRule,
  deletePlanFeatureRule
} from './api'
import type { PlanFeatureLinkedDiffRequest } from './types'

export function useSyncPlanFeaturesMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: { planUuid: string } & PlanFeatureLinkedDiffRequest) =>
      syncPlanFeatures(data.planUuid, { features: data.features }),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['plan-features', variables.planUuid] })
      queryClient.invalidateQueries({ queryKey: ['plans'] })
    },
    onError: (error) => {
      console.error('Failed to sync plan features:', error)
    }
  })
}

export function useCreatePlanFeatureRuleMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: {
      planId: string
      featureId: string
      type: string
      value: Record<string, any>
      isEnabled: boolean
      creditModelId?: string
    }) => createPlanFeatureRule(data),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['plan-features', variables.planId] })
    }
  })
}

export function useUpdatePlanFeatureRuleMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: {
      planId: string
      featureId: string
      type: string
      value: Record<string, any>
      isEnabled: boolean
      creditModelId?: string
    }) => updatePlanFeatureRule(data),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['plan-features', variables.planId] })
    }
  })
}

export function useDeletePlanFeatureRuleMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: { planUuid: string; featureUuid: string }) =>
      deletePlanFeatureRule(data.planUuid, data.featureUuid),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['plan-features', variables.planUuid] })
    }
  })
}
