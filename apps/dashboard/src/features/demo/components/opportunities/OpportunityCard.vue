<template>
  <Card class="overflow-hidden">
    <CardHeader class="pb-3">
      <!-- Header: Icon + Title -->
      <div class="flex items-center gap-2">
        <div :class="iconBgClass" class="rounded-lg p-1.5 shrink-0">
          <component :is="severityIcon" :class="iconClass" class="h-4 w-4" />
        </div>
        <CardTitle class="text-base font-semibold truncate">{{
          opportunity.featureName
        }}</CardTitle>
      </div>
      <CardDescription class="mt-1">{{ opportunityTypeLabel }}</CardDescription>
    </CardHeader>

    <CardContent class="space-y-3">
      <!-- Hero: Monthly Loss -->
      <div v-if="opportunity.monthlyLoss" class="text-xl font-semibold text-red-600">
        -${{ formatNumber(opportunity.monthlyLoss) }}/mo
      </div>

      <!-- Price Comparison Box -->
      <div class="flex items-center gap-3 p-2 rounded-md bg-muted/50">
        <div class="flex-1">
          <div class="text-xs text-muted-foreground">Current</div>
          <div class="font-medium">
            {{
              opportunity.currentPrice === 0 ? 'Free' : '$' + opportunity.currentPrice.toFixed(4)
            }}
          </div>
        </div>
        <ArrowRight class="h-4 w-4 text-muted-foreground shrink-0" />
        <div class="flex-1">
          <div class="text-xs text-muted-foreground">Suggested</div>
          <div class="font-medium text-emerald-600">
            ${{ opportunity.suggestedPrice.toFixed(4) }}
          </div>
        </div>
      </div>

      <!-- Context: Volume & Customers -->
      <div class="text-xs text-muted-foreground">
        {{ formatNumber(opportunity.volume) }} units/mo · ${{ opportunity.cost.toFixed(4) }} cost ·
        {{ opportunity.affectedCustomers }} customers
      </div>

      <!-- Action Button -->
      <Button class="w-full mt-3" @click="$emit('model-change', opportunity)">
        Model This Fix
        <ArrowRight class="h-4 w-4 ml-2" />
      </Button>
    </CardContent>
  </Card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { AlertTriangle, TrendingDown, Layers, ArrowRight } from 'lucide-vue-next'
import type { PricingOpportunity } from '../../types'

const props = defineProps<{
  opportunity: PricingOpportunity
}>()

defineEmits<{
  'model-change': [opportunity: PricingOpportunity]
}>()

const opportunityTypeLabel = computed(() => {
  switch (props.opportunity.type) {
    case 'negative_margin':
      return 'Losing money on each unit'
    case 'underpriced':
      return 'Priced below market rate'
    case 'tiering_opportunity':
      return 'Could benefit from tiered pricing'
    default:
      return 'Pricing issue detected'
  }
})

const severityIcon = computed(() => {
  switch (props.opportunity.type) {
    case 'negative_margin':
      return AlertTriangle
    case 'underpriced':
      return TrendingDown
    case 'tiering_opportunity':
      return Layers
    default:
      return AlertTriangle
  }
})

const iconBgClass = computed(() => {
  switch (props.opportunity.severity) {
    case 'high':
      return 'bg-red-100'
    case 'medium':
      return 'bg-amber-100'
    case 'low':
      return 'bg-blue-100'
    default:
      return 'bg-slate-100'
  }
})

const iconClass = computed(() => {
  switch (props.opportunity.severity) {
    case 'high':
      return 'text-red-600'
    case 'medium':
      return 'text-amber-600'
    case 'low':
      return 'text-blue-600'
    default:
      return 'text-slate-600'
  }
})

function formatNumber(value: number): string {
  if (value >= 1000000) return (value / 1000000).toFixed(1) + 'M'
  if (value >= 1000) return (value / 1000).toFixed(1) + 'K'
  return value.toLocaleString()
}
</script>
