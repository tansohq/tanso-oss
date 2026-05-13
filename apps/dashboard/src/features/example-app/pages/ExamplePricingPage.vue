<template>
  <div class="min-h-screen py-16 md:py-24 px-6">
    <div class="max-w-7xl mx-auto">
      <div class="text-center mb-16 max-w-3xl mx-auto">
        <p class="text-sm font-semibold text-indigo-600 mb-3 tracking-wide uppercase">Pricing</p>
        <h1 class="text-5xl md:text-6xl font-bold tracking-tight mb-4">
          Choose Your Plan
        </h1>
        <p class="text-lg md:text-xl text-muted-foreground">
          Start with a plan that fits your needs. Upgrade or downgrade anytime.
        </p>
      </div>

      <div v-if="isLoading" class="flex items-center justify-center py-24">
        <Loader2 class="w-8 h-8 animate-spin text-muted-foreground" />
      </div>

      <!-- Empty state when no plans are configured -->
      <div v-else-if="enrichedPlans.length === 0" class="flex flex-col items-center justify-center py-24 max-w-md mx-auto text-center">
        <div class="flex h-12 w-12 items-center justify-center rounded-full bg-muted mb-4">
          <Inbox class="w-6 h-6 text-muted-foreground" />
        </div>
        <h2 class="text-xl font-semibold mb-2">No plans available yet</h2>
        <p class="text-muted-foreground mb-6">
          Plans are configured in the Tanso dashboard. Create at least one plan with active status, then come back to see it here.
        </p>
        <a
          href="/plans"
          class="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 transition-colors"
        >
          Go to Dashboard
          <ExternalLink class="w-4 h-4" />
        </a>
      </div>

      <div
        v-else
        :class="[
          'grid grid-cols-1 gap-8 max-w-5xl mx-auto',
          enrichedPlans.length > 1 ? 'md:grid-cols-2' : '',
          enrichedPlans.length > 2 ? 'lg:grid-cols-3' : ''
        ]"
      >
        <ExamplePricingCard
          v-for="plan in enrichedPlans"
          :key="plan.id"
          :plan="plan"
          :is-current="plan.id === currentPlanId"
          :change-type="getChangeType(plan)"
          :is-pending="pendingPlanId === plan.id"
          @subscribe="handleSubscribe(plan)"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Loader2, Inbox, ExternalLink } from 'lucide-vue-next'
import { toast } from '@/components/ui/toast/use-toast'
import { usePlansQuery } from '@/features/plans/queries'
import { getPlanFeatures } from '@/features/plans/api'
import { getPlanFeatureRule } from '@/features/plan-features/api'
import { createSubscription, cancelSubscription as cancelSubscriptionApi, undoScheduledCancellation } from '@/features/subscriptions/api'
import ExamplePricingCard from '../components/ExamplePricingCard.vue'
import { useExampleAppState } from '../composables/useExampleAppState'
import type { EnrichedPlan, EnrichedFeature } from '../types'

const router = useRouter()
const {
  currentPlanId,
  isSubscribed,
  subscribedPlanPrice,
  subscriptionStatus,
  customerUuid,
  subscriptionId,
  setSubscription,
  subscribe,
  changePlan,
} = useExampleAppState()

const { data: plansData, isLoading: plansLoading } = usePlansQuery()
const enrichedPlans = ref<EnrichedPlan[]>([])
const isEnriching = ref(false)
const pendingPlanId = ref<string | null>(null)

const isLoading = computed(() => plansLoading.value || isEnriching.value)

const activePlans = computed(() =>
  (plansData.value?.data || []).filter((p) => (p.status ?? '').toLowerCase() === 'active')
)

// Fetch features and pricing rules for each active plan
let fetchId = 0
watch(
  activePlans,
  async (plans) => {
    if (plans.length === 0) {
      enrichedPlans.value = []
      return
    }
    isEnriching.value = true
    const currentFetchId = ++fetchId

    const result = await Promise.all(
      plans.map(async (plan) => {
        let features: EnrichedFeature[] = []
        try {
          const featuresResponse = await getPlanFeatures(plan.id)
          const rawFeatures = featuresResponse?.data?.features || []

          features = await Promise.all(
            rawFeatures
              .map(async (feature) => {
                try {
                  const rule = await getPlanFeatureRule(plan.id, feature.id)
                  const value = rule?.data?.value || {}
                  const model = (value as Record<string, unknown>).model as string | undefined
                  let pricingType: EnrichedFeature['pricingType'] = 'included'
                  if (model === 'usage') pricingType = 'usage_based'
                  else if (model === 'graduated') pricingType = 'graduated'

                  return {
                    id: feature.id,
                    name: feature.name,
                    key: feature.key,
                    pricingType,
                    unitPrice: (value as Record<string, unknown>).price_per_unit as number | undefined,
                    unitLabel: (value as Record<string, unknown>).usage_unit_type as string | undefined,
                    tiers: (value as Record<string, unknown>).tiers as EnrichedFeature['tiers'],
                  }
                } catch {
                  return {
                    id: feature.id,
                    name: feature.name,
                    key: feature.key,
                    pricingType: 'included' as const,
                  }
                }
              })
          )
        } catch {
          // Plan features fetch failed — show plan with no features
        }

        return {
          id: plan.id,
          key: plan.key,
          name: plan.name,
          description: plan.description,
          priceAmount: plan.priceAmount ?? 0,
          intervalMonths: plan.intervalMonths,
          currency: 'USD',
          status: plan.status ?? null,
          features,
        }
      })
    )

    if (currentFetchId === fetchId) {
      enrichedPlans.value = result.sort((a, b) => a.priceAmount - b.priceAmount)
      isEnriching.value = false
    }
  },
  { immediate: true }
)

function getChangeType(plan: EnrichedPlan): 'upgrade' | 'downgrade' | null {
  if (!isSubscribed.value || plan.id === currentPlanId.value) return null
  if (plan.priceAmount > subscribedPlanPrice.value) return 'upgrade'
  if (plan.priceAmount < subscribedPlanPrice.value) return 'downgrade'
  return null
}

async function handleSubscribe(plan: EnrichedPlan) {
  if (!customerUuid.value) {
    // Fallback to local-only if no UUID (shouldn't happen in normal flow)
    if (isSubscribed.value) {
      changePlan(plan.id, plan.name, plan.priceAmount, plan.currency)
    } else {
      subscribe(plan.id, plan.name, plan.priceAmount, plan.currency)
    }
    router.push({ name: 'example-dashboard' })
    return
  }

  pendingPlanId.value = plan.id
  try {
    // If changing plan, cancel the old subscription first
    if (isSubscribed.value && subscriptionId.value) {
      // If already pending cancellation, undo it before cancelling immediately
      if (subscriptionStatus.value === 'pending_cancellation') {
        await undoScheduledCancellation(subscriptionId.value)
      }
      await cancelSubscriptionApi(subscriptionId.value, 'IMMEDIATE')
    }

    // Create the new subscription
    const response = await createSubscription({
      planId: plan.id,
      customerId: customerUuid.value,
    })

    const subId = response.data?.id
    if (subId) {
      setSubscription(subId, plan.id, plan.name, plan.priceAmount, plan.currency)
    } else {
      // Response didn't include subscription ID — update local state anyway
      subscribe(plan.id, plan.name, plan.priceAmount, plan.currency)
    }

    toast({
      title: isSubscribed.value ? 'Plan changed' : 'Subscribed',
      description: `You're now on the ${plan.name} plan.`,
    })
    router.push({ name: 'example-dashboard' })
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Subscription failed'
    toast({ title: 'Error', description: message, variant: 'destructive' })
  } finally {
    pendingPlanId.value = null
  }
}
</script>
