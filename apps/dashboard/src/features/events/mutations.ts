import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { createEvent } from './api'
import type { CreateEventRequest } from './types'

export function useCreateEventMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: CreateEventRequest) => createEvent(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['events'] })
    },
    onError: (error) => {
      console.error('Failed to create event:', error)
    }
  })
}
