<template>
  <div class="space-y-2">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <span class="font-medium">{{ label }}</span>
        <Badge variant="outline" class="text-xs">Metered</Badge>
      </div>
      <div class="text-sm font-mono tabular-nums">${{ cost.toLocaleString() }}</div>
    </div>

    <!-- Progress Bar with Threshold Markers -->
    <div class="relative">
      <!-- Background track -->
      <div class="h-3 rounded-full bg-muted overflow-hidden">
        <!-- Progress fill -->
        <div
          :class="['h-full rounded-full transition-all', progressColor]"
          :style="{ width: `${Math.min(percentage, 100)}%` }"
        />
      </div>

      <!-- Threshold markers -->
      <template v-if="limit">
        <div
          v-for="threshold in activeThresholds"
          :key="threshold"
          class="absolute top-0 h-3 w-0.5 bg-foreground/30"
          :style="{ left: `${threshold}%` }"
        >
          <div
            v-if="percentage >= threshold - 2 && percentage <= threshold + 2"
            class="absolute -top-5 left-1/2 -translate-x-1/2 text-[10px] text-muted-foreground whitespace-nowrap"
          >
            {{ threshold }}% alert
          </div>
        </div>
      </template>
    </div>

    <!-- Stats row -->
    <div class="flex items-center justify-between text-sm">
      <div class="text-muted-foreground">
        <span class="font-mono tabular-nums">{{ formatNumber(current) }}</span>
        <template v-if="limit">
          <span> / </span>
          <span class="font-mono tabular-nums">{{ formatNumber(limit) }}</span>
        </template>
      </div>
      <div class="flex items-center gap-2">
        <span v-if="limit" :class="percentageColor"> {{ percentage.toFixed(0) }}% of limit </span>
        <span v-else class="text-muted-foreground"> No limit </span>
        <!-- Alert indicator -->
        <div v-if="hasTriggeredAlert" class="flex items-center gap-1 text-amber-600">
          <Bell class="h-3 w-3" />
          <span class="text-xs">{{ triggeredAlertLevel }}% alert</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Bell } from 'lucide-vue-next'
import { Badge } from '@/components/ui/badge'

const props = withDefaults(
  defineProps<{
    label: string
    current: number
    limit?: number | null
    cost: number
    thresholds?: number[]
  }>(),
  {
    limit: null,
    thresholds: () => [50, 80, 95]
  }
)

const percentage = computed(() => {
  if (!props.limit) return 0
  return (props.current / props.limit) * 100
})

const activeThresholds = computed(() => {
  return props.thresholds.filter((t) => t < 100)
})

const hasTriggeredAlert = computed(() => {
  return props.thresholds.some((t) => percentage.value >= t)
})

const triggeredAlertLevel = computed(() => {
  const triggered = props.thresholds.filter((t) => percentage.value >= t)
  return triggered.length > 0 ? Math.max(...triggered) : 0
})

const progressColor = computed(() => {
  if (!props.limit) return 'bg-blue-500'
  if (percentage.value >= 95) return 'bg-red-500'
  if (percentage.value >= 80) return 'bg-amber-500'
  if (percentage.value >= 50) return 'bg-yellow-500'
  return 'bg-emerald-500'
})

const percentageColor = computed(() => {
  if (percentage.value >= 95) return 'text-red-600 font-medium'
  if (percentage.value >= 80) return 'text-amber-600 font-medium'
  if (percentage.value >= 50) return 'text-yellow-600'
  return 'text-muted-foreground'
})

function formatNumber(num: number): string {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toLocaleString()
}
</script>
