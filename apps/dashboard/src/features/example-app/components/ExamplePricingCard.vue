<template>
  <Card :class="['p-8 md:p-10 relative transition-shadow hover:shadow-lg', isCurrent ? 'border-indigo-500 border-2 shadow-xl ring-1 ring-indigo-500/20' : 'hover:border-slate-300']">
    <div class="mb-8">
      <h3 class="text-2xl font-bold mb-2">{{ plan.name }}</h3>
      <div class="flex items-baseline gap-1 mb-4">
        <span class="text-5xl font-bold">{{ formattedPrice }}</span>
        <span class="text-xl text-muted-foreground">/{{ billingInterval }}</span>
      </div>
      <p v-if="hasUsageCharges" class="text-sm text-muted-foreground mb-2">
        + usage-based charges
      </p>
      <p v-if="plan.description" class="text-muted-foreground">{{ plan.description }}</p>
    </div>

    <ul class="space-y-4 mb-8">
      <ExampleFeatureItem
        v-for="feature in sortedFeatures"
        :key="feature.id"
        :name="feature.name"
        :pricing-type="feature.pricingType"
        :unit-price="feature.unitPrice"
        :unit-label="feature.unitLabel"
        :max-usage="feature.maxUsage"
        :tiers="feature.tiers"
        :currency="plan.currency"
      />
    </ul>

    <Button
      :variant="isCurrent ? 'secondary' : 'default'"
      :class="['w-full', !isCurrent && !isPending ? 'bg-indigo-600 hover:bg-indigo-700 text-white' : '']"
      :disabled="isPending || isCurrent"
      @click="emit('subscribe')"
    >
      {{ buttonLabel }}
    </Button>
  </Card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { formatPrice, formatIntervalShort } from '@/lib/formatters'
import { isSimplePrepaidGraduated } from '../lib/examplePricing'
import ExampleFeatureItem from './ExampleFeatureItem.vue'
import type { EnrichedPlan, EnrichedFeature } from '../types'

const props = defineProps<{
  plan: EnrichedPlan
  isCurrent: boolean
  changeType: 'upgrade' | 'downgrade' | null
  isPending: boolean
}>()

const emit = defineEmits<{
  subscribe: []
}>()

const formattedPrice = computed(() => formatPrice(props.plan.priceAmount))
const billingInterval = computed(() => formatIntervalShort(props.plan.intervalMonths))

const hasUsageCharges = computed(() =>
  props.plan.features.some((f) => {
    if (f.pricingType === 'usage_based') {
      return f.unitPrice !== 0
    }
    if (f.pricingType === 'graduated' && f.tiers) {
      const prepaid = isSimplePrepaidGraduated(f.tiers)
      return !prepaid || prepaid.overagePrice !== 0
    }
    return false
  })
)

const sortedFeatures = computed(() =>
  [...props.plan.features].sort((a, b) => {
    const order = (f: EnrichedFeature) =>
      f.pricingType === 'included' ? 1 : 0
    return order(a) - order(b)
  })
)

const buttonLabel = computed(() => {
  if (props.isCurrent) return 'Current Plan'
  if (props.isPending) return 'Processing...'
  if (props.changeType === 'upgrade') return 'Upgrade'
  if (props.changeType === 'downgrade') return 'Downgrade'
  return 'Subscribe'
})
</script>
