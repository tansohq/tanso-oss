<template>
  <Card class="p-6 mb-6">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-xs font-medium text-muted-foreground uppercase tracking-wide">Plan Usage Summary</h3>
      <span class="text-xs text-muted-foreground tabular-nums">
        {{ formatPeriodDate(periodStart) }} – {{ formatPeriodDate(periodEnd) }}
      </span>
    </div>

    <!-- Loading -->
    <div v-if="isLoading" class="flex items-center gap-2 py-4">
      <Loader2 class="h-4 w-4 animate-spin text-muted-foreground" />
      <span class="text-sm text-muted-foreground">Loading usage...</span>
    </div>

    <!-- Error -->
    <div v-else-if="isError" class="text-sm text-destructive">
      Unable to load usage data.
    </div>

    <!-- Empty -->
    <p v-else-if="aggregatedFeatures.length === 0" class="text-sm text-muted-foreground">
      No usage recorded this period.
    </p>

    <!-- Usage table -->
    <template v-else>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Feature</TableHead>
            <TableHead class="text-right">Units</TableHead>
            <TableHead class="text-right">Revenue</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="f in aggregatedFeatures" :key="f.featureId">
            <TableCell class="font-medium">
              <span class="inline-flex items-center gap-1.5">
                {{ f.featureName }}
                <TooltipProvider v-if="f.resetMode === 'accumulate'">
                  <Tooltip>
                    <TooltipTrigger as-child>
                      <Badge variant="secondary" class="text-[10px] px-1.5 py-0">Cumulative</Badge>
                    </TooltipTrigger>
                    <TooltipContent>
                      <p>Pricing tiers are based on cumulative usage total</p>
                    </TooltipContent>
                  </Tooltip>
                </TooltipProvider>
              </span>
            </TableCell>
            <TableCell class="text-right tabular-nums">{{ formatUnits(f.units) }}</TableCell>
            <TableCell class="text-right tabular-nums">{{ formatAmount(f.revenue) }}</TableCell>
          </TableRow>
          <!-- Total row -->
          <TableRow class="border-t-2">
            <TableCell class="font-semibold">Total</TableCell>
            <TableCell class="text-right tabular-nums font-semibold">{{ formatUnits(revenueData?.aggregateTotalUnits ?? 0) }}</TableCell>
            <TableCell class="text-right tabular-nums font-semibold">{{ formatAmount(revenueData?.aggregateTotalRevenue ?? 0) }}</TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </template>

    <!-- Footer -->
    <p v-if="!isLoading && !isError && subscriptionCount > 0" class="text-xs text-muted-foreground mt-4">
      Across {{ subscriptionCount }} subscription{{ subscriptionCount === 1 ? '' : 's' }}.
    </p>
  </Card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger
} from '@/components/ui/tooltip'
import { Loader2 } from 'lucide-vue-next'
import { formatAmount, formatUsageNumber } from '@/lib/formatters'
import { usePlanRevenueQuery } from '../queries'

const props = defineProps<{
  planId: string
}>()

// Default period: first of current month → first of next month (UTC)
const now = new Date()
const periodStart = new Date(Date.UTC(now.getFullYear(), now.getMonth(), 1)).toISOString()
const periodEnd = new Date(Date.UTC(now.getFullYear(), now.getMonth() + 1, 1)).toISOString()

const planIdRef = computed(() => props.planId)

const { data, isLoading, isError } = usePlanRevenueQuery(planIdRef, periodStart, periodEnd)

const revenueData = computed(() => data.value?.data ?? null)

const subscriptionCount = computed(() => revenueData.value?.subscriptions?.totalElements ?? 0)

// Aggregate features across all subscriptions
const aggregatedFeatures = computed(() => {
  const subs = revenueData.value?.subscriptions?.items
  if (!subs || subs.length === 0) return []

  const map = new Map<string, { featureId: string; featureName: string; units: number; revenue: number; resetMode: string | null }>()

  for (const sub of subs) {
    for (const f of sub.features) {
      const existing = map.get(f.featureId)
      if (existing) {
        existing.units += f.units
        existing.revenue += f.revenue
      } else {
        map.set(f.featureId, {
          featureId: f.featureId,
          featureName: f.featureName,
          units: f.units,
          revenue: f.revenue,
          resetMode: f.resetMode ?? null
        })
      }
    }
  }

  return Array.from(map.values())
})

function formatUnits(units: number): string {
  return formatUsageNumber(units)
}


function formatPeriodDate(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}
</script>
