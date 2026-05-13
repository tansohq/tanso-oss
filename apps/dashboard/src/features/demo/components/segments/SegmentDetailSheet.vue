<template>
  <Sheet :open="open" @update:open="$emit('update:open', $event)">
    <SheetContent side="right" class="w-full sm:max-w-[600px] overflow-y-auto">
      <SheetHeader>
        <div class="flex items-center justify-between pr-12">
          <div>
            <SheetTitle>{{ segment?.name }}</SheetTitle>
            <SheetDescription>
              {{ segment?.customerCount.toLocaleString() }} customers · ${{
                formatCurrency(segment?.mrr || 0)
              }}
              MRR ·
              <span :class="marginClass"
                >{{ segment?.grossMarginPct !== null ? `${segment?.grossMarginPct}%` : '—' }} Gross
                Margin</span
              >
            </SheetDescription>
          </div>
          <div class="flex items-center gap-2">
            <Button variant="outline" size="sm" @click="$emit('edit', segment?.id)">
              <Pencil class="h-4 w-4 mr-1" />
              Edit
            </Button>
          </div>
        </div>
      </SheetHeader>

      <div v-if="segment" class="mt-6 space-y-6">
        <!-- Summary Stats -->
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <Card class="p-3">
            <div class="text-xs text-muted-foreground">Customers</div>
            <div class="text-lg font-semibold">{{ segment.customerCount }}</div>
            <TrendIndicator
              v-if="segment.customerTrend !== 'stable'"
              :direction="segment.customerTrend"
              :value="segment.customerTrendValue"
              class="text-xs"
            />
          </Card>
          <Card class="p-3">
            <div class="text-xs text-muted-foreground">Total MRR</div>
            <div class="text-lg font-semibold">${{ formatCurrency(segment.mrr) }}</div>
          </Card>
          <Card class="p-3">
            <div class="text-xs text-muted-foreground">Avg Margin</div>
            <div class="text-lg font-semibold" :class="marginClass">
              {{ segment.grossMarginPct !== null ? `${segment.grossMarginPct}%` : '—' }}
            </div>
            <TrendIndicator
              v-if="segment.marginTrend !== 'stable' && segment.marginTrendValue"
              :direction="segment.marginTrend"
              :value="segment.marginTrendValue"
              suffix="pp"
              class="text-xs"
            />
          </Card>
          <Card class="p-3">
            <div class="text-xs text-muted-foreground">Unprofitable</div>
            <div class="text-lg font-semibold">
              {{ unprofitableCount }}
            </div>
          </Card>
        </div>

        <!-- Rules -->
        <div>
          <h4 class="text-sm font-medium mb-2">Conditions (AND)</h4>
          <div class="space-y-2">
            <div
              v-for="rule in segment.rules"
              :key="rule.id"
              class="flex items-center gap-2 text-sm p-2 bg-muted/50 rounded"
            >
              <span class="text-muted-foreground">{{ rule.fieldLabel }}</span>
              <span>{{ rule.operatorLabel }}</span>
              <span class="font-medium">{{ rule.valueLabel }}</span>
            </div>
          </div>
        </div>

        <!-- AI Optimization Suggestions (only for segments with < 40 accounts) -->
        <SegmentOptimizationSuggestionsComponent
          v-if="segment.customerCount < 40 && suggestions"
          :suggestions="suggestions"
        />

        <!-- Connected Automations -->
        <div v-if="segment.linkedAutomationNames.length > 0">
          <h4 class="text-sm font-medium mb-2">Connected Automations</h4>
          <div class="space-y-2">
            <div
              v-for="name in segment.linkedAutomationNames"
              :key="name"
              class="flex items-center gap-2 text-sm p-2 bg-muted/50 rounded"
            >
              <Workflow class="h-4 w-4 text-muted-foreground" />
              <span>{{ name }}</span>
            </div>
          </div>
        </div>

        <!-- Connected Experiments -->
        <div v-if="segment.linkedExperimentNames.length > 0">
          <h4 class="text-sm font-medium mb-2">Connected Experiments</h4>
          <div class="space-y-2">
            <div
              v-for="name in segment.linkedExperimentNames"
              :key="name"
              class="flex items-center gap-2 text-sm p-2 bg-muted/50 rounded"
            >
              <FlaskConical class="h-4 w-4 text-muted-foreground" />
              <span>{{ name }}</span>
            </div>
          </div>
        </div>

        <!-- Customer Sample Table -->
        <div>
          <h4 class="text-sm font-medium mb-2">Customer Sample</h4>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Customer</TableHead>
                <TableHead class="text-right">MRR</TableHead>
                <TableHead class="text-right">Margin</TableHead>
                <TableHead class="text-right">Trend</TableHead>
                <TableHead class="text-right">Cost/Seat</TableHead>
                <TableHead>In Segment</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow
                v-for="customer in segment.customers.slice(0, 5)"
                :key="customer.id"
                class="cursor-pointer hover:bg-muted/50"
                @click="$emit('select-customer', customer.id)"
              >
                <TableCell class="font-medium">{{ customer.name }}</TableCell>
                <TableCell class="text-right">${{ customer.mrr.toLocaleString() }}</TableCell>
                <TableCell class="text-right">
                  <MarginBadge :margin="customer.grossMarginPct" />
                </TableCell>
                <TableCell class="text-right">
                  <TrendIndicator
                    v-if="customer.marginTrend !== null && customer.marginTrend !== 0"
                    :direction="customer.marginTrend < 0 ? 'down' : 'up'"
                    :value="Math.abs(customer.marginTrend)"
                    suffix="pp"
                  />
                  <span v-else class="text-muted-foreground">—</span>
                </TableCell>
                <TableCell class="text-right">
                  <span v-if="customer.costPerSeat">${{ customer.costPerSeat.toFixed(2) }}</span>
                  <span v-else class="text-muted-foreground">—</span>
                </TableCell>
                <TableCell>{{ customer.inSegmentSince }}</TableCell>
              </TableRow>
            </TableBody>
          </Table>
          <Button v-if="segment.customers.length > 5" variant="link" class="mt-2">
            View all {{ segment.customerCount }} customers
          </Button>
        </div>

        <!-- Margin Distribution -->
        <div v-if="segment.marginDistribution.length > 0">
          <h4 class="text-sm font-medium mb-2">Margin Distribution</h4>
          <div class="space-y-2">
            <div
              v-for="bucket in segment.marginDistribution"
              :key="bucket.range"
              class="flex items-center gap-3"
            >
              <span class="text-sm text-muted-foreground w-24">{{ bucket.range }}</span>
              <div class="flex-1 h-4 bg-muted rounded overflow-hidden">
                <div
                  class="h-full transition-all"
                  :class="getDistributionBarColor(bucket.range)"
                  :style="{ width: `${(bucket.count / maxDistributionCount) * 100}%` }"
                />
              </div>
              <span class="text-sm font-medium w-8 text-right">{{ bucket.count }}</span>
            </div>
          </div>
        </div>
      </div>
    </SheetContent>
  </Sheet>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription
} from '@/components/ui/sheet'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import { Pencil, Workflow, FlaskConical } from 'lucide-vue-next'
import type { Segment, SegmentOptimizationSuggestions } from '../../types'
import MarginBadge from '../shared/MarginBadge.vue'
import TrendIndicator from '../shared/TrendIndicator.vue'
import SegmentOptimizationSuggestionsComponent from './SegmentOptimizationSuggestions.vue'

const props = defineProps<{
  open: boolean
  segment: Segment | null
  suggestions?: SegmentOptimizationSuggestions | null
}>()

defineEmits<{
  'update:open': [value: boolean]
  'select-customer': [id: string]
  edit: [id: string | undefined]
}>()

const marginClass = computed(() => {
  if (!props.segment || props.segment.grossMarginPct === null) return ''
  if (props.segment.grossMarginPct < 0) return 'text-red-600'
  if (props.segment.grossMarginPct < 50) return 'text-amber-600'
  return 'text-green-600'
})

const unprofitableCount = computed(() => {
  if (!props.segment) return 0
  return props.segment.customers.filter((c) => c.grossMarginPct !== null && c.grossMarginPct < 0)
    .length
})

const maxDistributionCount = computed(() => {
  if (!props.segment) return 1
  return Math.max(...props.segment.marginDistribution.map((d) => d.count), 1)
})

function formatCurrency(value: number): string {
  if (value >= 1000000) return (value / 1000000).toFixed(1) + 'M'
  if (value >= 1000) return (value / 1000).toFixed(1) + 'K'
  return value.toLocaleString()
}

function getDistributionBarColor(range: string): string {
  if (range.includes('< 0') || range.includes('Loss')) return 'bg-red-500'
  if (range.includes('0-30') || range.includes('0%')) return 'bg-red-400'
  if (range.includes('30-50')) return 'bg-amber-500'
  if (range.includes('50-70')) return 'bg-green-400'
  if (range.includes('> 70') || range.includes('70+')) return 'bg-green-500'
  return 'bg-blue-500'
}
</script>
