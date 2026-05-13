<template>
  <Alert v-if="suggestion && !dismissed" variant="warning" class="mb-6">
    <TrendingUp class="h-4 w-4" />
    <div class="flex-1 min-w-0">
      <AlertTitle class="flex items-center justify-between">
        <span>{{ suggestion.reason }}</span>
        <Button
          variant="ghost"
          size="icon"
          class="h-6 w-6 -mr-2 -mt-1"
          @click.stop="dismissed = true"
        >
          <X class="h-3 w-3" />
        </Button>
      </AlertTitle>
      <AlertDescription class="flex items-center justify-between mt-1">
        <span class="text-muted-foreground">
          +${{ formatCurrency(suggestion.projectedRevenueChange) }}/mo potential with
          {{ suggestion.changePercent }}% price increase
        </span>
        <Button size="sm" @click="handleSimulate"> Simulate </Button>
      </AlertDescription>
    </div>
  </Alert>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { TrendingUp, X } from 'lucide-vue-next'
import { Alert, AlertTitle, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { formatCurrency } from '@/lib/utils'
import type { Segment } from '../../types'

interface SimulationSuggestion {
  changePercent: number
  projectedRevenueChange: number
  reason: string
}

const props = defineProps<{
  segment: Segment
}>()

const router = useRouter()
const dismissed = ref(false)

const suggestion = computed<SimulationSuggestion | null>(() => {
  const avgMargin = props.segment.grossMarginPct ?? 50

  // Negative margin → aggressive increase
  if (avgMargin < 0) {
    return {
      changePercent: 25,
      projectedRevenueChange: props.segment.mrr * 0.25,
      reason: 'Negative margins detected'
    }
  }

  // Low margin (< 40%) → moderate increase
  if (avgMargin < 40) {
    const increase = avgMargin < 20 ? 20 : 15
    return {
      changePercent: increase,
      projectedRevenueChange: props.segment.mrr * (increase / 100),
      reason: 'Low margins detected'
    }
  }

  return null
})

function handleSimulate() {
  router.push({
    path: '/demo/simulations/new',
    query: {
      segmentId: props.segment.id,
      changePercent: suggestion.value?.changePercent.toString()
    }
  })
}
</script>
