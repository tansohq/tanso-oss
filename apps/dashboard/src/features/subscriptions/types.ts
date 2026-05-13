import { z } from 'zod'
import {
  subscriptionSchema,
  subscriptionsResponseSchema,
  subscriptionDetailResponseSchema,
  createSubscriptionSchema
} from './schemas'

export type Subscription = z.infer<typeof subscriptionSchema>
export type SubscriptionsResponse = z.infer<typeof subscriptionsResponseSchema>
export type SubscriptionDetailResponse = z.infer<typeof subscriptionDetailResponseSchema>
export type CreateSubscription = z.infer<typeof createSubscriptionSchema>

export interface FetchSubscriptionsParams {
  page?: number
  size?: number
}
