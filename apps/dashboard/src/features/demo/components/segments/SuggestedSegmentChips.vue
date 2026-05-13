<template>
  <div v-if="suggestions.length > 0" class="flex items-center gap-2">
    <span class="text-xs text-muted-foreground">Suggested:</span>
    <TooltipProvider>
      <Tooltip v-for="suggestion in suggestions" :key="suggestion.id">
        <TooltipTrigger asChild>
          <Badge
            variant="outline"
            class="cursor-pointer hover:bg-muted/50 transition-colors"
            @click="handleCreateSegment(suggestion)"
          >
            {{ suggestion.name }} ({{ suggestion.customerCount }})
          </Badge>
        </TooltipTrigger>
        <TooltipContent side="bottom" class="max-w-xs">
          <div class="space-y-1">
            <p class="font-medium">{{ suggestion.name }}</p>
            <p class="text-xs text-muted-foreground">
              {{ suggestion.customerCount }} customers · ${{
                formatCurrency(suggestion.totalMrr)
              }}
              MRR
            </p>
            <p class="text-xs text-muted-foreground">
              {{ suggestion.avgMargin.toFixed(0) }}% avg margin
            </p>
            <p class="text-xs">{{ suggestion.reason }}</p>
            <p class="text-xs text-muted-foreground mt-2">Click to create segment</p>
          </div>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Badge } from '@/components/ui/badge'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { useDemoState } from '../../composables/useDemoState'
import { formatCurrency } from '@/lib/utils'
import type { Customer, Segment } from '../../types'

interface SegmentSuggestion {
  id: string
  name: string
  customerCount: number
  totalMrr: number
  avgMargin: number
  reason: string
  filterFn: (c: Customer) => boolean
}

const router = useRouter()
const { customers, createSegment } = useDemoState()

const suggestions = computed<SegmentSuggestion[]>(() => {
  const result: SegmentSuggestion[] = []
  const customerList = customers as Customer[]

  // Underwater accounts (margin < 40%)
  const underwaterFilter = (c: Customer) => (c.margin ?? 0.5) < 0.4
  const underwater = customerList.filter(underwaterFilter)
  if (underwater.length >= 3) {
    result.push({
      id: 'underwater',
      name: 'Underwater Accounts',
      customerCount: underwater.length,
      totalMrr: underwater.reduce((sum, c) => sum + c.mrr, 0),
      avgMargin:
        (underwater.reduce((sum, c) => sum + (c.margin ?? 0.5), 0) / underwater.length) * 100,
      reason: 'Accounts with margins below 40%',
      filterFn: underwaterFilter
    })
  }

  // High-value at risk (high MRR, lower margin)
  const highValueFilter = (c: Customer) => c.mrr > 5000 && (c.margin ?? 0.5) < 0.6
  const highValueRisk = customerList.filter(highValueFilter)
  if (highValueRisk.length >= 2) {
    result.push({
      id: 'high-value-risk',
      name: 'High-Value at Risk',
      customerCount: highValueRisk.length,
      totalMrr: highValueRisk.reduce((sum, c) => sum + c.mrr, 0),
      avgMargin:
        (highValueRisk.reduce((sum, c) => sum + (c.margin ?? 0.5), 0) / highValueRisk.length) * 100,
      reason: 'Large accounts with margin concerns',
      filterFn: highValueFilter
    })
  }

  return result.slice(0, 3)
})

function handleCreateSegment(suggestion: SegmentSuggestion) {
  const customerList = customers as Customer[]
  const matchingCustomers = customerList.filter(suggestion.filterFn)

  // Create segment data structure
  const newSegment: Omit<Segment, 'id'> = {
    name: suggestion.name,
    icon: suggestion.id === 'underwater' ? 'AlertTriangle' : 'TrendingDown',
    color: suggestion.id === 'underwater' ? 'red' : 'amber',
    customerCount: suggestion.customerCount,
    customerTrend: 'stable',
    mrr: suggestion.totalMrr,
    carr: Math.round(suggestion.totalMrr * 0.75),
    uarr: Math.round(suggestion.totalMrr * 0.25),
    grossMarginPct: Math.round(suggestion.avgMargin),
    marginTrend: 'stable',
    rules:
      suggestion.id === 'underwater'
        ? [
            {
              id: 'r1',
              field: 'gross_margin_pct',
              fieldLabel: 'Gross Margin',
              operator: 'less_than' as const,
              operatorLabel: 'is less than',
              value: 40,
              valueLabel: '40%'
            }
          ]
        : [
            {
              id: 'r1',
              field: 'mrr',
              fieldLabel: 'MRR',
              operator: 'greater_than' as const,
              operatorLabel: 'is greater than',
              value: 5000,
              valueLabel: '$5,000'
            },
            {
              id: 'r2',
              field: 'gross_margin_pct',
              fieldLabel: 'Gross Margin',
              operator: 'less_than' as const,
              operatorLabel: 'is less than',
              value: 60,
              valueLabel: '60%'
            }
          ],
    automationCount: 0,
    experimentCount: 0,
    linkedAutomationNames: [],
    linkedExperimentNames: [],
    lastUpdated: 'Just now',
    status: 'active',
    customers: matchingCustomers.map((c) => ({
      id: c.id,
      name: c.name,
      email: c.email,
      mrr: c.mrr,
      carr: Math.round(c.mrr * 0.75 * 12),
      uarr: Math.round(c.mrr * 0.25 * 12),
      grossMarginPct: Math.round((c.margin ?? 0.5) * 100),
      marginTrend: 0,
      costPerSeat: null,
      apiCost30d: c.costs?.total ?? null,
      plan: c.plan,
      seats: c.seats,
      customerSince: c.customerSince,
      inSegmentSince: 'Just now',
      segments: [],
      activeExperiments: [],
      automationHistory: [],
      marginTrendData: []
    })),
    marginDistribution: []
  }

  const created = createSegment(newSegment)
  router.push(`/demo/segments/${created.id}`)
}
</script>
