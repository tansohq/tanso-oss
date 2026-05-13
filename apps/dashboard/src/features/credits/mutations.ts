import { useMutation, useQueryClient } from '@tanstack/vue-query'
import {
  createCreditModel,
  deleteCreditModel,
  createPlanCreditAllocation,
  deletePlanCreditAllocation,
  grantCredits
} from './api'
import type { CreateCreditModel } from './types'
import { useToast } from '@/components/ui/toast/use-toast'

export function useCreateCreditModelMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: CreateCreditModel) => createCreditModel(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['credit-models'] })
    },
    onError: (error) => {
      console.error('Failed to create credit model:', error)
      toast({
        title: 'Something went wrong',
        description: 'Failed to create credit model. Please try again.',
        variant: 'destructive',
      })
    }
  })
}

export function useDeleteCreditModelMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (id: string) => deleteCreditModel(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['credit-models'] })
    },
    onError: (error) => {
      console.error('Failed to delete credit model:', error)
      toast({
        title: 'Something went wrong',
        description: 'Failed to delete credit model. Please try again.',
        variant: 'destructive',
      })
    }
  })
}

export function useCreatePlanCreditAllocationMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (params: {
      creditModelId: string
      planId: string
      creditAmount: number
      grantExpiresMonths?: number
      hardLimit?: boolean
    }) =>
      createPlanCreditAllocation(params.creditModelId, params.planId, {
        creditAmount: params.creditAmount,
        grantExpiresMonths: params.grantExpiresMonths,
        hardLimit: params.hardLimit
      }),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: ['plan-credit-allocations', variables.planId]
      })
    },
    onError: (error) => {
      console.error('Failed to create plan credit allocation:', error)
      toast({
        title: 'Something went wrong',
        description: 'Failed to create credit allocation. Please try again.',
        variant: 'destructive',
      })
    }
  })
}

export function useDeletePlanCreditAllocationMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (params: { creditModelId: string; planId: string }) =>
      deletePlanCreditAllocation(params.creditModelId, params.planId),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: ['plan-credit-allocations', variables.planId]
      })
    },
    onError: (error) => {
      console.error('Failed to delete plan credit allocation:', error)
      toast({
        title: 'Something went wrong',
        description: 'Failed to delete credit allocation. Please try again.',
        variant: 'destructive',
      })
    }
  })
}

export function useGrantCreditsMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (params: { poolId: string; amount: number; reason?: string }) =>
      grantCredits(params.poolId, { amount: params.amount, reason: params.reason }),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['pool-grants', variables.poolId] })
      queryClient.invalidateQueries({ queryKey: ['customer-credit-pools'] })
    },
    onError: (error) => {
      console.error('Failed to grant credits:', error)
      toast({
        title: 'Something went wrong',
        description: 'Failed to grant credits. Please try again.',
        variant: 'destructive',
      })
    }
  })
}
