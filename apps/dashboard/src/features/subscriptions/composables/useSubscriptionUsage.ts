import { computed, type Ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRoute } from 'vue-router'
import { fetchPlanRevenue } from '@/features/plans/api'

export interface UsageGroup {
  featureId: string
  featureKey: string
  featureName: string
  usageUnits: number
  revenue: number
  model: string | null
  resetMode: string | null
}

export interface SubscriptionUsageData {
  groups: UsageGroup[]
  totalUnits: number
  totalRevenue: number
  hasAnyEvents: boolean
}

const DEMO_USAGE: UsageGroup[] = [
  { featureId: 'f6a7b8c9-d0e1-4234-f012-345678901234', featureKey: 'api_access', featureName: 'API Access', usageUnits: 8500, revenue: 42.50, model: 'usage', resetMode: 'reset' },
  { featureId: 'a7b8c9d0-e1f2-4345-0123-456789012345', featureKey: 'campaign_analytics', featureName: 'Campaign Analytics', usageUnits: 1400, revenue: 28.00, model: 'usage', resetMode: 'reset' },
  { featureId: 'c9d0e1f2-a3b4-4567-2345-678901234567', featureKey: 'email_sequences', featureName: 'Email Sequences', usageUnits: 3200, revenue: 64.00, model: 'usage', resetMode: 'reset' },
  { featureId: 'd0e1f2a3-b4c5-4678-3456-789012345678', featureKey: 'contact_enrichment', featureName: 'Contact Enrichment', usageUnits: 2100, revenue: 31.50, model: 'usage', resetMode: 'reset' }
]

const DEMO_DATA: SubscriptionUsageData = {
  groups: DEMO_USAGE,
  totalUnits: DEMO_USAGE.reduce((sum, g) => sum + g.usageUnits, 0),
  totalRevenue: DEMO_USAGE.reduce((sum, g) => sum + g.revenue, 0),
  hasAnyEvents: true
}

export function useSubscriptionUsage(
  subscriptionId: Ref<string>,
  planId: Ref<string>,
  periodStart: Ref<string>,
  periodEnd: Ref<string>
) {
  const route = useRoute()

  const isDemo = computed(() => route.path.startsWith('/demo'))

  const query = useQuery({
    queryKey: computed(() => [
      'subscription-usage',
      subscriptionId.value,
      planId.value,
      periodStart.value,
      periodEnd.value
    ]),
    queryFn: async (): Promise<SubscriptionUsageData> => {
      if (isDemo.value) {
        return DEMO_DATA
      }

      const response = await fetchPlanRevenue(
        planId.value,
        periodStart.value,
        periodEnd.value,
        0,
        1,
        subscriptionId.value
      )

      const sub = response.data.subscriptions.items[0]

      if (!sub) {
        return { groups: [], totalUnits: 0, totalRevenue: 0, hasAnyEvents: false }
      }

      return {
        groups: sub.features.map((f) => ({
          featureId: f.featureId,
          featureKey: f.featureKey,
          featureName: f.featureName,
          usageUnits: f.units,
          revenue: f.revenue,
          model: f.model ?? null,
          resetMode: f.resetMode ?? null
        })),
        totalUnits: sub.totalUnits,
        totalRevenue: sub.totalRevenue,
        hasAnyEvents: sub.features.length > 0
      }
    },
    enabled: computed(() =>
      isDemo.value || (!!subscriptionId.value && !!planId.value && !!periodStart.value && !!periodEnd.value)
    )
  })

  const groups = computed(() => query.data.value?.groups ?? [])
  const totalUnits = computed(() => query.data.value?.totalUnits ?? 0)
  const totalRevenue = computed(() => query.data.value?.totalRevenue ?? 0)
  const hasAnyEvents = computed(() => query.data.value?.hasAnyEvents ?? false)
  const loading = computed(() => query.isLoading.value)
  const error = computed(() => query.isError.value)

  return {
    groups,
    totalUnits,
    totalRevenue,
    loading,
    error,
    hasAnyEvents,
    query
  }
}
