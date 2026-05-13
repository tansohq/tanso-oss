<template>
  <div class="p-6 pb-20">
    <!-- Header -->
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-semibold text-foreground">Portfolio Analytics</h1>
        <p class="text-muted-foreground mt-1">
          Real-time margin visibility across your customer base
        </p>
      </div>
      <Select v-model="dateRange">
        <SelectTrigger class="w-36">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="7d">Last 7 days</SelectItem>
          <SelectItem value="30d">Last 30 days</SelectItem>
          <SelectItem value="90d">Last 90 days</SelectItem>
        </SelectContent>
      </Select>
    </div>

    <!-- Portfolio Health Card -->
    <Card class="p-6 mb-6">
      <div class="text-sm font-medium text-muted-foreground mb-4">Portfolio Health</div>
      <div class="grid grid-cols-4 gap-6">
        <div>
          <div class="text-sm text-muted-foreground">Total MRR</div>
          <div class="text-2xl font-semibold">
            ${{ portfolioSummary.totalMrr.toLocaleString() }}
          </div>
        </div>
        <div>
          <div class="text-sm text-muted-foreground">Total Costs (30d)</div>
          <div class="text-2xl font-semibold">
            ${{ portfolioSummary.totalCosts.toLocaleString() }}
          </div>
        </div>
        <div>
          <div class="text-sm text-muted-foreground">Avg Margin</div>
          <div class="text-2xl font-semibold" :class="avgMarginColorClass">
            {{ (portfolioSummary.avgMargin * 100).toFixed(0) }}%
          </div>
          <div class="flex items-center gap-1 text-sm mt-1" :class="marginTrendClass">
            <TrendingDown v-if="portfolioSummary.marginTrendPp < 0" class="h-3 w-3" />
            <TrendingUp v-else-if="portfolioSummary.marginTrendPp > 0" class="h-3 w-3" />
            <span>{{ Math.abs(portfolioSummary.marginTrendPp) }}pp from last month</span>
          </div>
        </div>
        <div>
          <div class="text-sm text-muted-foreground">Customers</div>
          <div class="flex items-center gap-3 mt-2">
            <span class="flex items-center gap-1">
              <span class="w-3 h-3 rounded-full bg-emerald-500"></span>
              {{ portfolioSummary.customersByStatus.healthy }}
            </span>
            <span class="flex items-center gap-1">
              <span class="w-3 h-3 rounded-full bg-amber-500"></span>
              {{ portfolioSummary.customersByStatus.atRisk }}
            </span>
            <span class="flex items-center gap-1">
              <span class="w-3 h-3 rounded-full bg-red-500"></span>
              {{ portfolioSummary.customersByStatus.underwater }}
            </span>
          </div>
        </div>
      </div>
    </Card>

    <!-- MRR Distribution Bar -->
    <Card class="p-4 mb-6">
      <div class="text-sm font-medium mb-3">MRR by margin health</div>
      <div class="flex h-4 rounded-full overflow-hidden">
        <div
          class="bg-emerald-500 transition-all duration-500"
          :style="{ width: healthyPct + '%' }"
        />
        <div class="bg-amber-500 transition-all duration-500" :style="{ width: atRiskPct + '%' }" />
        <div
          class="bg-red-500 transition-all duration-500"
          :style="{ width: underwaterPct + '%' }"
        />
      </div>
      <div class="flex justify-between text-xs text-muted-foreground mt-2">
        <span class="flex items-center gap-1">
          <span class="w-2 h-2 rounded-full bg-emerald-500"></span>
          Healthy (≥70%): ${{ portfolioSummary.mrrByStatus.healthy.toLocaleString() }}
        </span>
        <span class="flex items-center gap-1">
          <span class="w-2 h-2 rounded-full bg-amber-500"></span>
          At Risk (40-70%): ${{ portfolioSummary.mrrByStatus.atRisk.toLocaleString() }}
        </span>
        <span class="flex items-center gap-1">
          <span class="w-2 h-2 rounded-full bg-red-500"></span>
          Underwater (&lt;40%): ${{ portfolioSummary.mrrByStatus.underwater.toLocaleString() }}
        </span>
      </div>
    </Card>

    <!-- Top/Bottom Customers -->
    <div class="grid grid-cols-2 gap-6">
      <Card class="p-6">
        <h3 class="text-sm font-medium mb-4 flex items-center gap-2">
          <TrendingUp class="h-4 w-4 text-emerald-500" />
          Highest Margin Customers
        </h3>
        <div class="space-y-3">
          <div
            v-for="customer in topCustomers"
            :key="customer.id"
            class="flex items-center justify-between py-2 border-b border-border last:border-0"
          >
            <div>
              <p class="font-medium text-sm">{{ customer.name }}</p>
              <p class="text-xs text-muted-foreground">${{ customer.mrr }}/mo</p>
            </div>
            <span class="text-sm font-semibold text-emerald-500">
              {{ Math.round(customer.margin * 100) }}%
            </span>
          </div>
        </div>
      </Card>

      <Card class="p-6">
        <h3 class="text-sm font-medium mb-4 flex items-center gap-2">
          <TrendingDown class="h-4 w-4 text-red-500" />
          Lowest Margin Customers
        </h3>
        <div class="space-y-3">
          <div
            v-for="customer in bottomCustomers"
            :key="customer.id"
            class="flex items-center justify-between py-2 border-b border-border last:border-0"
          >
            <div>
              <p class="font-medium text-sm">{{ customer.name }}</p>
              <p class="text-xs text-muted-foreground">${{ customer.mrr }}/mo</p>
            </div>
            <span
              class="text-sm font-semibold"
              :class="customer.margin < 0.4 ? 'text-red-500' : 'text-amber-500'"
            >
              {{ Math.round(customer.margin * 100) }}%
            </span>
          </div>
        </div>
      </Card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { TrendingUp, TrendingDown } from 'lucide-vue-next'
import { Card } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import { useDemoState } from '../../composables/useDemoState'

const { customers, portfolioSummary } = useDemoState()

const dateRange = ref('30d')

const sortedByMargin = computed(() => {
  return [...customers].sort((a, b) => b.margin - a.margin)
})

const topCustomers = computed(() => sortedByMargin.value.slice(0, 5))
const bottomCustomers = computed(() => sortedByMargin.value.slice(-5).reverse())

const healthyPct = computed(() => {
  const total = portfolioSummary.totalMrr
  return (portfolioSummary.mrrByStatus.healthy / total) * 100
})

const atRiskPct = computed(() => {
  const total = portfolioSummary.totalMrr
  return (portfolioSummary.mrrByStatus.atRisk / total) * 100
})

const underwaterPct = computed(() => {
  const total = portfolioSummary.totalMrr
  return (portfolioSummary.mrrByStatus.underwater / total) * 100
})

const avgMarginColorClass = computed(() => {
  const margin = portfolioSummary.avgMargin
  if (margin < 0.4) return 'text-red-600'
  if (margin < 0.7) return 'text-amber-600'
  return 'text-emerald-600'
})

const marginTrendClass = computed(() => {
  if (portfolioSummary.marginTrendPp < 0) return 'text-red-600'
  if (portfolioSummary.marginTrendPp > 0) return 'text-emerald-600'
  return 'text-muted-foreground'
})
</script>
