import { fetchSubscriptionStatus } from '@/features/account/api'

// Cache subscription status to avoid repeated API calls during navigation
let subscriptionStatusCache: { hasActiveSubscription: boolean; timestamp: number } | null = null
const CACHE_TTL_MS = 60000 // 1 minute cache

export async function checkSubscriptionStatus(): Promise<boolean> {
  // Return cached value if still valid
  if (subscriptionStatusCache && Date.now() - subscriptionStatusCache.timestamp < CACHE_TTL_MS) {
    return subscriptionStatusCache.hasActiveSubscription
  }

  try {
    const response = await fetchSubscriptionStatus()
    const hasActive = response.data?.hasActiveSubscription ?? false
    subscriptionStatusCache = { hasActiveSubscription: hasActive, timestamp: Date.now() }
    return hasActive
  } catch (error) {
    console.error('Failed to check subscription status:', error)
    // On error, allow access to avoid blocking users
    return true
  }
}

export function clearSubscriptionCache() {
  subscriptionStatusCache = null
}

export function invalidateSubscriptionCache() {
  subscriptionStatusCache = null
}
