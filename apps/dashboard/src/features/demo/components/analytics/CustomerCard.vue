<template>
  <Card class="cursor-pointer hover:bg-muted/50 transition-colors" @click="$emit('click')">
    <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between p-4 gap-3">
      <!-- Left: Name + Plan + Status -->
      <div class="flex items-center gap-4">
        <div>
          <div class="font-medium">{{ customer.name }}</div>
          <div class="text-sm text-muted-foreground">{{ customer.plan }}</div>
        </div>
        <Badge :class="statusBadgeClass">
          {{ statusLabel }}
        </Badge>
      </div>

      <!-- Right: Metrics + Sparkline + Chevron -->
      <div class="flex items-center gap-4 sm:gap-6 flex-wrap">
        <div class="text-right">
          <div class="font-semibold tabular-nums">${{ customer.mrr.toLocaleString() }}</div>
          <div class="text-xs text-muted-foreground">MRR</div>
        </div>
        <div class="text-right">
          <div class="font-semibold tabular-nums">${{ customer.costs.total.toLocaleString() }}</div>
          <div class="text-xs text-muted-foreground">Cost</div>
        </div>
        <div class="text-right">
          <div class="font-semibold tabular-nums" :class="marginColorClass">{{ marginPct }}%</div>
          <div class="text-xs text-muted-foreground">Margin</div>
        </div>
        <TrendingUp
          v-if="customer.marginTrend === 'improving'"
          class="h-4 w-4 text-muted-foreground"
        />
        <TrendingDown
          v-else-if="customer.marginTrend === 'declining'"
          class="h-4 w-4 text-muted-foreground"
        />
        <Minus v-else class="h-4 w-4 text-muted-foreground" />
        <ChevronRight class="h-4 w-4 text-muted-foreground" />
      </div>
    </div>
  </Card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ChevronRight, TrendingUp, TrendingDown, Minus } from 'lucide-vue-next'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import type { Customer } from '../../types'

const props = defineProps<{
  customer: Customer
}>()

defineEmits<{
  click: []
}>()

const marginPct = computed(() => (props.customer.margin * 100).toFixed(0))

const statusBadgeClass = computed(() => {
  switch (props.customer.marginStatus) {
    case 'healthy':
      return 'bg-primary/10 text-primary border-0 hover:bg-primary/10'
    case 'at_risk':
      return 'bg-muted text-muted-foreground border-0 hover:bg-muted'
    case 'underwater':
      return 'bg-muted text-muted-foreground border-0 hover:bg-muted'
    default:
      return ''
  }
})

const statusLabel = computed(() => {
  switch (props.customer.marginStatus) {
    case 'healthy':
      return 'Healthy'
    case 'at_risk':
      return 'At Risk'
    case 'underwater':
      return 'Underwater'
    default:
      return props.customer.marginStatus
  }
})

const marginColorClass = computed(() => {
  return 'text-foreground'
})
</script>
