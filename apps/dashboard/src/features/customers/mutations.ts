import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { createCustomer, updateCustomer } from './api'
import type { CreateCustomer, UpdateCustomer } from './types'

export function useCreateCustomerMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: CreateCustomer) => createCustomer(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customers'] })
    },
    onError: (error) => {
      console.error('Failed to create customer:', error)
    }
  })
}

export function useUpdateCustomerMutation(customerId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: UpdateCustomer) => updateCustomer(customerId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customers'] })
      queryClient.invalidateQueries({ queryKey: ['customer', customerId] })
    },
    onError: (error) => {
      console.error('Failed to update customer:', error)
    }
  })
}
