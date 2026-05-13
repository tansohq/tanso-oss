<template>
  <Card
    class="h-full flex flex-col cursor-pointer hover:bg-muted/50 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
    @click="$emit('click')"
  >
    <CardHeader class="pb-2">
      <div class="flex items-center justify-between">
        <CardTitle class="text-base font-semibold tracking-tight">{{ simulation.name }}</CardTitle>
        <Badge :class="statusClasses" class="border-0">
          {{ statusLabel }}
        </Badge>
      </div>
      <CardDescription class="mt-1">
        {{ simulation.segmentName }} · {{ simulation.scenarios.length }}
        {{ simulation.scenarios.length === 1 ? 'scenario' : 'scenarios' }}
      </CardDescription>
    </CardHeader>
    <CardContent class="flex-1 flex flex-col">
      <!-- Margin Impact Summary (only for completed simulations) -->
      <div
        v-if="
          simulation.marginImpact &&
          (simulation.status === 'completed' || simulation.status === 'rolled_out')
        "
        class="space-y-2"
      >
        <div class="flex items-center gap-2">
          <TrendingUp
            v-if="simulation.marginImpact.marginDelta > 0"
            class="h-4 w-4 text-emerald-600"
          />
          <TrendingDown v-else class="h-4 w-4 text-red-600" />
          <span
            class="font-semibold"
            :class="simulation.marginImpact.marginDelta > 0 ? 'text-emerald-600' : 'text-red-600'"
          >
            {{ simulation.marginImpact.marginDelta > 0 ? '+' : '' }}${{
              formatCurrency(simulation.marginImpact.marginDelta)
            }}/mo
          </span>
        </div>
        <div class="text-sm text-muted-foreground">
          Margin {{ simulation.marginImpact.baselineMarginPct }}% →
          {{ simulation.marginImpact.scenarioMarginPct }}%
        </div>
      </div>

      <!-- Draft/Running state hint -->
      <div v-else-if="simulation.status === 'draft'" class="text-sm text-muted-foreground">
        Ready to run
      </div>
      <div v-else-if="simulation.status === 'running'" class="text-sm text-muted-foreground">
        Calculating impact...
      </div>

      <!-- Spacer to ensure minimum gap above footer -->
      <div class="flex-1 min-h-3"></div>

      <!-- Footer -->
      <div class="flex items-center justify-between text-sm text-muted-foreground pt-3 border-t">
        <span>{{ formatDate(simulation.createdAt) }}</span>
        <span v-if="simulation.segmentPreview" class="text-xs">
          {{ simulation.segmentPreview.customerCount }} customers
        </span>
      </div>
    </CardContent>
  </Card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { TrendingUp, TrendingDown } from 'lucide-vue-next'
import type { Simulation } from '../../types'
import { getSimulationStatusColor, getSimulationStatusLabel } from '../../types'

const props = defineProps<{
  simulation: Simulation
}>()

defineEmits<{
  click: []
}>()

const statusClasses = computed(() => getSimulationStatusColor(props.simulation.status))
const statusLabel = computed(() => getSimulationStatusLabel(props.simulation.status))

function formatCurrency(value: number): string {
  if (value >= 1000000) return (value / 1000000).toFixed(1) + 'M'
  if (value >= 1000) return (value / 1000).toFixed(1) + 'K'
  return value.toLocaleString()
}

function formatDate(dateString: string): string {
  const date = new Date(dateString)
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}
</script>
