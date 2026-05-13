<template>
  <main class="max-w-7xl mx-auto px-6 py-8 md:py-12">
    <div class="mb-8">
      <h2 class="text-3xl font-bold tracking-tight mb-2">Welcome back!</h2>
      <p class="text-muted-foreground">
        Here's what's happening with your account today.
      </p>
    </div>

    <!-- No subscription state -->
    <template v-if="!isSubscribed">
      <Card class="p-12">
        <div class="text-center space-y-4">
          <CreditCard class="w-16 h-16 mx-auto text-muted-foreground" />
          <div>
            <p class="font-semibold text-lg mb-2">No active subscription</p>
            <p class="text-muted-foreground mb-6">
              Subscribe to a plan to get started.
            </p>
            <Button @click="router.push({ name: 'example-pricing' })">
              View Plans
            </Button>
          </div>
        </div>
      </Card>
    </template>

    <!-- Active subscription -->
    <template v-else>
      <!-- Scheduled cancellation banner -->
      <Card v-if="subscriptionStatus === 'pending_cancellation'" class="p-4 mb-4 border-amber-500 bg-amber-50">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-3">
            <AlertCircle class="w-5 h-5 text-amber-600" />
            <div>
              <p class="font-medium text-amber-800">Cancellation scheduled</p>
              <p class="text-sm text-amber-700">
                Your subscription will end on
                {{ cancelEffectiveAt ? new Date(cancelEffectiveAt).toLocaleDateString(undefined, { month: 'long', day: 'numeric', year: 'numeric' }) : 'the end of the current billing period' }}.
                You can still use all features until then.
              </p>
            </div>
          </div>
          <Button
            variant="outline"
            size="sm"
            class="border-amber-600 text-amber-700 hover:bg-amber-100"
            @click="router.push({ name: 'example-pricing' })"
          >
            Change Plan
          </Button>
        </div>
      </Card>

      <!-- Cancelled banner -->
      <Card v-if="subscriptionStatus === 'cancelled'" class="p-4 mb-4 border-amber-500 bg-amber-50">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-3">
            <AlertCircle class="w-5 h-5 text-amber-600" />
            <div>
              <p class="font-medium text-amber-800">Subscription cancelled</p>
              <p class="text-sm text-amber-700">
                Your access has been revoked. Resubscribe to regain access.
              </p>
            </div>
          </div>
          <Button
            variant="outline"
            size="sm"
            class="border-amber-600 text-amber-700 hover:bg-amber-100"
            @click="router.push({ name: 'example-pricing' })"
          >
            Resubscribe
          </Button>
        </div>
      </Card>

      <!-- Subscription info card -->
      <Card class="p-6 mb-8">
        <div class="flex items-start justify-between">
          <div class="flex items-center gap-4">
            <div class="p-3 bg-primary/10 rounded-lg">
              <CreditCard class="w-6 h-6 text-primary" />
            </div>
            <div>
              <h3 class="text-lg font-semibold">Current Subscription</h3>
              <p class="text-2xl font-bold text-primary mt-1">{{ currentPlanName }}</p>
              <p class="text-sm text-muted-foreground mt-1">
                Status:
                <span v-if="subscriptionStatus === 'cancelled'" class="text-destructive font-semibold">
                  Cancelled
                </span>
                <span v-else-if="subscriptionStatus === 'pending_cancellation'" class="text-amber-600 font-semibold">
                  Cancels {{ cancelEffectiveAt ? new Date(cancelEffectiveAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' }) : 'at end of period' }}
                </span>
                <span v-else class="text-green-600 font-semibold">Active</span>
              </p>

              <!-- Plan features -->
              <div v-if="currentPlanFeatures.length > 0" class="mt-4 pt-4 border-t">
                <p class="text-sm font-semibold mb-2">Plan features</p>
                <div class="space-y-3">
                  <ExamplePlanFeatureRow
                    v-for="feature in currentPlanFeatures"
                    :key="feature.id"
                    :feature="feature"
                  />
                </div>
              </div>
            </div>
          </div>

          <div class="flex gap-2">
            <Button
              v-if="subscriptionStatus === 'active' || subscriptionStatus === 'pending_cancellation'"
              variant="outline"
              size="sm"
              class="min-w-[160px]"
              @click="router.push({ name: 'example-pricing' })"
            >
              <ArrowUpRight class="w-4 h-4 mr-2" />
              Change Plan
            </Button>
            <Button
              v-if="subscriptionStatus === 'active'"
              variant="destructive"
              size="sm"
              class="min-w-[160px]"
              :disabled="isCancelling"
              @click="handleCancel"
            >
              <Loader2 v-if="isCancelling" class="w-4 h-4 mr-2 animate-spin" />
              <X v-else class="w-4 h-4 mr-2" />
              Cancel Plan
            </Button>
          </div>
        </div>
      </Card>

      <!-- Feature access hint -->
      <p class="text-sm text-muted-foreground mb-4">
        Click "Access Feature" to use a feature. This checks your entitlement and tracks a real usage event.
      </p>

      <!-- Feature access cards -->
      <div v-if="isEnriching" class="flex items-center justify-center py-12">
        <Loader2 class="w-8 h-8 animate-spin text-muted-foreground" />
      </div>
      <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-6">
        <ExampleFeatureAccessCard
          v-for="feature in allEnrichedFeatures"
          :key="feature.id"
          :feature="feature"
          :is-locked="subscriptionStatus === 'cancelled'"
          @upgrade="router.push({ name: 'example-pricing' })"
        />
        <div v-if="allEnrichedFeatures.length === 0" class="col-span-2 text-center py-12 text-muted-foreground">
          No features available
        </div>
      </div>
    </template>
  </main>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { CreditCard, ArrowUpRight, X, AlertCircle, Loader2 } from 'lucide-vue-next'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { toast } from '@/components/ui/toast/use-toast'
import { useFeaturesQuery } from '@/features/features/queries'
import { getPlanFeatureRule } from '@/features/plan-features/api'
import { cancelSubscription as cancelSubscriptionApi, getSubscription } from '@/features/subscriptions/api'
import ExamplePlanFeatureRow from '../components/ExamplePlanFeatureRow.vue'
import ExampleFeatureAccessCard from '../components/ExampleFeatureAccessCard.vue'
import { useExampleAppState } from '../composables/useExampleAppState'
import type { EnrichedFeature } from '../types'

const router = useRouter()
const {
  isSubscribed,
  currentPlanId,
  currentPlanName,
  subscriptionStatus,
  subscriptionId,
  cancelEffectiveAt,
  cancelSubscription,
} = useExampleAppState()

const currentPlanFeatures = ref<EnrichedFeature[]>([])
const allEnrichedFeatures = ref<EnrichedFeature[]>([])
const isEnriching = ref(false)

const { data: featuresData } = useFeaturesQuery({ size: 100 })
const isCancelling = ref(false)

let fetchId = 0

// Fetch all features and enrich plan features with pricing rules
watch(
  [() => featuresData.value, currentPlanId],
  async ([features, planId]) => {
    const rawFeatures = features?.data || []
    if (rawFeatures.length === 0) {
      allEnrichedFeatures.value = []
      currentPlanFeatures.value = []
      return
    }

    isEnriching.value = true
    const currentFetchId = ++fetchId

    try {
      const results = await Promise.all(
        rawFeatures.map(async (feature) => {
          if (!planId) {
            return {
              enriched: { id: feature.id, name: feature.name, key: feature.key, pricingType: 'included' as const },
              inPlan: false,
            }
          }
          try {
            const rule = await getPlanFeatureRule(planId, feature.id)
            const value = rule?.data?.value || {}
            const model = (value as Record<string, unknown>).model as string | undefined
            let pricingType: EnrichedFeature['pricingType'] = 'included'
            if (model === 'usage') pricingType = 'usage_based'
            else if (model === 'graduated') pricingType = 'graduated'

            return {
              enriched: {
                id: feature.id,
                name: feature.name,
                key: feature.key,
                pricingType,
                unitPrice: (value as Record<string, unknown>).price_per_unit as number | undefined,
                unitLabel: (value as Record<string, unknown>).usage_unit_type as string | undefined,
                tiers: (value as Record<string, unknown>).tiers as EnrichedFeature['tiers'],
              },
              inPlan: true,
            }
          } catch {
            return {
              enriched: { id: feature.id, name: feature.name, key: feature.key, pricingType: 'included' as const },
              inPlan: false,
            }
          }
        })
      )

      if (currentFetchId === fetchId) {
        allEnrichedFeatures.value = results.map((r) => r.enriched)
        currentPlanFeatures.value = results.filter((r) => r.inPlan).map((r) => r.enriched)
      }
    } catch {
      if (currentFetchId === fetchId) {
        allEnrichedFeatures.value = []
        currentPlanFeatures.value = []
      }
    } finally {
      if (currentFetchId === fetchId) {
        isEnriching.value = false
      }
    }
  },
  { immediate: true }
)

async function handleCancel() {
  if (!subscriptionId.value) {
    cancelSubscription()
    return
  }

  isCancelling.value = true
  try {
    await cancelSubscriptionApi(subscriptionId.value, 'END_OF_PERIOD')

    // Fetch the subscription to get the cancellation effective date
    const sub = await getSubscription(subscriptionId.value)
    const effectiveAt = sub.data?.cancelEffectiveAt ?? sub.data?.currentPeriodEnd ?? null

    cancelSubscription(effectiveAt ?? undefined)

    const dateStr = effectiveAt
      ? new Date(effectiveAt).toLocaleDateString(undefined, { month: 'long', day: 'numeric', year: 'numeric' })
      : 'end of the current billing period'
    toast({
      title: 'Cancellation scheduled',
      description: `Your subscription will end on ${dateStr}.`,
    })
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Cancellation failed'
    toast({ title: 'Error', description: message, variant: 'destructive' })
  } finally {
    isCancelling.value = false
  }
}
</script>
