<template>
  <Card class="p-6 mb-6">
    <div class="flex items-center justify-between mb-4">
      <div>
        <template v-if="isLoading">
          <Skeleton class="h-4 w-28 mb-2" />
          <Skeleton class="h-8 w-20 mb-2" />
          <Skeleton class="h-4 w-36" />
        </template>
        <template v-else>
          <div class="text-sm font-medium text-muted-foreground">Events Ingested</div>
          <div class="text-4xl font-semibold tabular-nums">
            {{ formatNumber(totalEvents) }}
          </div>
          <div class="text-sm text-muted-foreground mt-1">Updated just now</div>
        </template>
      </div>
      <div v-if="isLoading">
        <Skeleton class="h-4 w-16" />
      </div>
      <div v-else class="text-sm text-muted-foreground">{{ periodLabel ?? 'Last 7 days' }}</div>
    </div>
    <div class="h-16 flex items-end gap-[1px]" role="img" :aria-label="`Events ingested - ${periodLabel ?? 'Last 7 days'}`">
      <template v-if="isLoading">
        <div
          v-for="i in barCount"
          :key="i"
          class="flex-1 rounded-t animate-pulse bg-gray-200"
          :style="{ height: '50%' }"
        />
      </template>
      <template v-else-if="hasData">
        <Tooltip v-for="(bar, i) in computedBarHeights" :key="i">
          <TooltipTrigger as-child>
            <div
              class="flex-1 rounded-t bg-gray-300 transition-all cursor-default"
              :style="{ height: bar }"
            />
          </TooltipTrigger>
          <TooltipContent>
            <p class="text-xs tabular-nums">{{ displayLabels[i] ? `${displayLabels[i]}: ` : '' }}{{ (dailyCounts?.[i] ?? 0).toLocaleString() }} events</p>
          </TooltipContent>
        </Tooltip>
      </template>
      <template v-else>
        <div
          v-for="i in barCount"
          :key="i"
          class="flex-1 rounded bg-muted"
          style="height: 4px"
        />
      </template>
    </div>
    <div v-if="!isLoading" class="flex justify-between text-[10px] text-muted-foreground mt-1.5">
      <span v-for="(label, i) in displayLabels" :key="i" :class="{ 'invisible': !label }">{{ label || '.' }}</span>
    </div>
  </Card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Card } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'

const props = defineProps<{
  totalEvents: number
  isLoading: boolean
  dailyCounts?: number[]
  labels?: string[]
  periodLabel?: string
}>()

const barCount = computed(() => props.dailyCounts?.length ?? 7)

const displayLabels = computed(() => {
  if (props.labels && props.labels.length > 0) return props.labels
  const labels: string[] = []
  const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
  for (let i = 6; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    labels.push(days[d.getDay()])
  }
  return labels
})

const hasData = computed(() => {
  const counts = props.dailyCounts
  return counts != null && counts.length > 0 && counts.some((c) => c > 0)
})

const computedBarHeights = computed(() => {
  const counts = props.dailyCounts
  if (!counts || counts.length === 0) {
    return Array(7).fill('4px')
  }
  const max = Math.max(...counts, 1)
  return counts.map((c) => {
    const pct = (c / max) * 100
    return pct > 0 ? `${Math.max(pct, 6)}%` : '4px'
  })
})

function formatNumber(num: number): string {
  if (num >= 1000000000) return (num / 1000000000).toFixed(1) + 'B'
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toLocaleString()
}
</script>
