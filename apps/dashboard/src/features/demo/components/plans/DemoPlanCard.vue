<template>
  <Card
    class="flex flex-col overflow-hidden transition-all cursor-pointer hover:bg-muted/50"
    @click="emit('click')"
  >
    <CardHeader class="pb-2">
      <div>
        <div class="flex items-center gap-2">
          <CardTitle class="text-lg truncate">{{ plan.name }}</CardTitle>
          <Badge
            v-if="planStatus === 'draft'"
            class="bg-amber-50 text-amber-700 border border-amber-200/50 shadow-none text-xs shrink-0"
          >
            Draft
          </Badge>
          <Badge
            v-else-if="planStatus === 'archived'"
            class="bg-gray-50 text-gray-600 border border-gray-200/50 shadow-none text-xs shrink-0"
          >
            Archived
          </Badge>
        </div>
        <div class="mt-1 flex items-baseline gap-2">
          <span class="text-2xl font-bold">{{ formatPrice(basePrice) }}</span>
          <span class="text-muted-foreground">/ {{ formatIntervalShort(plan.billingCycle) }}</span>
        </div>
        <div v-if="hasUsagePricing" class="text-xs text-muted-foreground/60 italic mt-0.5">+ usage-based</div>
      </div>
    </CardHeader>
    <CardContent class="mt-auto">
      <div class="pt-3 border-t">
        <TooltipProvider :delay-duration="0">
          <Tooltip v-if="featureNames.length > 0">
            <TooltipTrigger as-child>
              <Badge class="text-xs bg-gray-100/80 text-gray-900 border-0 shadow-none cursor-default">
                {{ featureNames.length }} {{ featureNames.length === 1 ? 'feature' : 'features' }}
              </Badge>
            </TooltipTrigger>
            <TooltipContent side="bottom" class="max-w-xs">
              <p v-for="name in featureNames" :key="name" class="text-xs">{{ name }}</p>
            </TooltipContent>
          </Tooltip>
          <span v-else class="text-sm text-amber-600 flex items-center gap-1">
            <AlertTriangle class="h-3.5 w-3.5" />
            No features
          </span>
        </TooltipProvider>
      </div>
    </CardContent>
  </Card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { AlertTriangle } from 'lucide-vue-next'
import type { Plan } from '../../types'
import { useDemoState } from '../../composables/useDemoState'

const props = defineProps<{
  plan: Plan
}>()

const emit = defineEmits<{
  click: []
}>()

const { planFeatureMap, featuresData } = useDemoState()

const planStatus = computed(() => props.plan.status ?? 'active')

const defaultVersion = computed(() => {
  return (
    props.plan.versions.find((v) => v.isDefault) ||
    props.plan.versions[props.plan.versions.length - 1]
  )
})

const basePrice = computed(() => {
  const version = defaultVersion.value
  if (!version?.fixedFees?.length) return 0
  return version.fixedFees.reduce((sum, fee) => sum + fee.amount * fee.quantity, 0)
})

const hasUsagePricing = computed(() => {
  const version = defaultVersion.value
  return (version?.usageBasedFees?.length ?? 0) > 0
})

const featureNames = computed(() => {
  const featureIds = planFeatureMap.value[props.plan.id] ?? []
  return featureIds
    .map((id) => featuresData.value.find((f) => f.id === id)?.name)
    .filter((name): name is string => !!name)
})

function formatPrice(amount: number | null | undefined): string {
  if (amount === null || amount === undefined) return '$0'
  const hasCents = amount % 1 !== 0
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: hasCents ? 2 : 0,
    maximumFractionDigits: hasCents ? 2 : 0
  }).format(amount)
}

function formatIntervalShort(cycle: string): string {
  switch (cycle) {
    case 'monthly': return 'mo'
    case 'quarterly': return 'qtr'
    case 'annual': return 'yr'
    default: return cycle
  }
}
</script>
