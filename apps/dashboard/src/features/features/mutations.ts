import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useToast } from '@/components/ui/toast/use-toast'
import { createFeature, updateFeature, deleteFeature } from './api'
import type { CreateFeature, UpdateFeature } from './types'

export function useCreateFeatureMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: CreateFeature) => createFeature(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['features'] })
    },
    onError: (error) => {
      console.error('Failed to create feature:', error)
      toast({
        title: 'Something went wrong',
        description: 'Failed to create feature. Please try again.',
        variant: 'destructive',
      })
    }
  })
}

export function useUpdateFeatureMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: ({ uuid, data }: { uuid: string; data: UpdateFeature }) => updateFeature(uuid, data),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['features'] })
      queryClient.invalidateQueries({ queryKey: ['feature', variables.uuid] })
    },
    onError: (error) => {
      console.error('Failed to update feature:', error)
      toast({
        title: 'Something went wrong',
        description: 'Failed to update feature. Please try again.',
        variant: 'destructive',
      })
    }
  })
}

export function useDeleteFeatureMutation() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (uuid: string) => deleteFeature(uuid),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['features'] })
    },
    onError: (error) => {
      console.error('Failed to delete feature:', error)
      toast({
        title: 'Something went wrong',
        description: 'Failed to delete feature. Please try again.',
        variant: 'destructive',
      })
    }
  })
}
