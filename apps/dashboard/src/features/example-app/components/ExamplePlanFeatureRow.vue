<template>
  <!-- Included feature -->
  <div v-if="isIncluded" class="flex items-center justify-between text-sm">
    <span class="flex items-center gap-1.5">
      <span class="text-green-600">&#10003;</span>
      <span>{{ feature.name }}</span>
    </span>
    <span class="text-xs text-muted-foreground">Included</span>
  </div>

  <!-- Usage-based / graduated feature -->
  <div v-else class="space-y-1.5">
    <div class="flex items-center justify-between text-sm">
      <span class="font-medium">{{ feature.name }}</span>
      <span class="tabular-nums">
        {{ currentUsage.toLocaleString() }} / {{ displayLimit != null ? displayLimit.toLocaleString() : '\u221E' }}{{ unit ? ` ${unit}` : '' }}
      </span>
    </div>
    <Progress v-if="displayLimit != null && usagePercent != null" :model-value="Math.min(usagePercent, 100)" class="h-2" />
    <p v-if="includedAmount != null" class="text-xs text-muted-foreground">
      {{ overageText }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Progress } from '@/components/ui/progress'
import { formatPrice } from '@/lib/formatters'
import { isSimplePrepaidGraduated } from '../lib/examplePricing'
import { useExampleAppState } from '../composables/useExampleAppState'
import type { EnrichedFeature } from '../types'

const props = defineProps<{
  feature: EnrichedFeature
}>()

const { getUsage } = useExampleAppState()

const isIncluded = computed(() =>
  props.feature.pricingType === 'included'
)

const unit = computed(() => props.feature.unitLabel || '')

const prepaid = computed(() => isSimplePrepaidGraduated(props.feature.tiers ?? null))

const includedAmount = computed(() => {
  if (prepaid.value) return prepaid.value.includedAmount
  return props.feature.maxUsage ?? null
})

const displayLimit = computed(() => includedAmount.value)

const currentUsage = computed(() => getUsage(props.feature.key))

const usagePercent = computed(() => {
  if (includedAmount.value == null) return null
  return (currentUsage.value / Number(includedAmount.value)) * 100
})

const overageTiers = computed(() =>
  (props.feature.tiers ?? []).filter(t => t.price_per_unit > 0)
)

const overageText = computed(() => {
  if (includedAmount.value == null) return ''
  const included = Number(includedAmount.value).toLocaleString()
  if (overageTiers.value.length === 1) {
    return `${included} included, then ${formatPrice(overageTiers.value[0].price_per_unit)} per ${unit.value || 'unit'}`
  }
  if (prepaid.value && prepaid.value.overagePrice > 0) {
    return `${included} included, then ${formatPrice(prepaid.value.overagePrice)} per ${unit.value || 'unit'}`
  }
  if (props.feature.unitPrice != null && props.feature.unitPrice > 0) {
    return `${included} included, then ${formatPrice(props.feature.unitPrice)} per ${unit.value || 'unit'}`
  }
  return `${included} included`
})
</script>
