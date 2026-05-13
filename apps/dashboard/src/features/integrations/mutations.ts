import { useMutation, useQueryClient } from '@tanstack/vue-query'
import {
  updateAccountSettings,
  registerStripeApiKey,
  deleteStripeKeys,
  registerStripeWebhook,
  discoverStripeObjects,
  startStripeImport,
  startAutoCreateStripeImport,
  mapStripeProduct
} from './api'
import type { UpdateAccountSetting } from './schemas'
import type { StripeImportStartRequest } from './api'

export function useUpdateAccountSettingsMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: UpdateAccountSetting) => updateAccountSettings(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['account-settings'] })
    }
  })
}

export function useRegisterStripeApiKeyMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (clientStripeApiKey: string) => registerStripeApiKey(clientStripeApiKey),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stripe-keys'] })
    }
  })
}

export function useDeleteStripeKeysMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => deleteStripeKeys(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stripe-keys'] })
    }
  })
}

export function useRegisterStripeWebhookMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => registerStripeWebhook(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stripe-keys'] })
    }
  })
}

export function useDiscoverStripeMutation() {
  return useMutation({
    mutationFn: (request: {
      includeProducts: boolean
      includeCustomers: boolean
      includeSubscriptions: boolean
    }) => discoverStripeObjects(request)
  })
}

export function useStartStripeImportMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: StripeImportStartRequest) => startStripeImport(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customers'] })
      queryClient.invalidateQueries({ queryKey: ['subscriptions'] })
    }
  })
}

export function useStartAutoCreateStripeImportMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => startAutoCreateStripeImport(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customers'] })
      queryClient.invalidateQueries({ queryKey: ['subscriptions'] })
      queryClient.invalidateQueries({ queryKey: ['plans'] })
      queryClient.invalidateQueries({ queryKey: ['features'] })
    }
  })
}

export function useMapStripeProductMutation() {
  return useMutation({
    mutationFn: (request: { stripeProductId: string; tansoPlanId: string }) =>
      mapStripeProduct(request)
  })
}
