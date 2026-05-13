<template>
  <div class="p-6 pb-16">
    <!-- Header with date indicator -->
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-semibold text-foreground">Dashboard</h1>
        <p class="text-muted-foreground mt-1">Overview of revenue and margins</p>
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

    <!-- Summary Card -->
    <Card class="p-6 mb-6">
      <div class="grid grid-cols-2 lg:grid-cols-4 gap-6">
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
              <span class="w-3 h-3 rounded-full bg-primary"></span>
              {{ portfolioSummary.customersByStatus.healthy }}
            </span>
            <span class="flex items-center gap-1">
              <span class="w-3 h-3 rounded-full bg-primary/50"></span>
              {{ portfolioSummary.customersByStatus.atRisk }}
            </span>
            <span class="flex items-center gap-1">
              <span class="w-3 h-3 rounded-full bg-primary/20"></span>
              {{ portfolioSummary.customersByStatus.underwater }}
            </span>
          </div>
        </div>
      </div>
    </Card>

    <!-- MRR Distribution Bar with thresholds -->
    <Card class="p-4 mb-6">
      <div class="text-sm font-medium mb-3">MRR by margin health</div>
      <div class="flex h-4 rounded-full overflow-hidden">
        <div class="bg-primary" :style="{ width: healthyPct + '%' }" />
        <div class="bg-primary/50" :style="{ width: atRiskPct + '%' }" />
        <div class="bg-primary/20" :style="{ width: underwaterPct + '%' }" />
      </div>
      <div class="flex flex-wrap gap-2 text-xs text-muted-foreground mt-2">
        <span class="flex items-center gap-1">
          <span class="w-2 h-2 rounded-full bg-primary"></span>
          Healthy (≥70%): ${{ portfolioSummary.mrrByStatus.healthy.toLocaleString() }}
        </span>
        <span class="flex items-center gap-1">
          <span class="w-2 h-2 rounded-full bg-primary/50"></span>
          At Risk (40-70%): ${{ portfolioSummary.mrrByStatus.atRisk.toLocaleString() }}
        </span>
        <span class="flex items-center gap-1">
          <span class="w-2 h-2 rounded-full bg-primary/20"></span>
          Underwater (&lt;40%): ${{ portfolioSummary.mrrByStatus.underwater.toLocaleString() }}
        </span>
      </div>
    </Card>

    <!-- Customers Section -->
    <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 mb-4">
      <h2 class="text-lg font-semibold">Customers</h2>
      <Select v-model="sortBy">
        <SelectTrigger class="w-48">
          <SelectValue placeholder="Sort by" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="margin_asc">Margin (low to high)</SelectItem>
          <SelectItem value="margin_desc">Margin (high to low)</SelectItem>
          <SelectItem value="mrr_desc">MRR (high to low)</SelectItem>
          <SelectItem value="cost_desc">Cost (high to low)</SelectItem>
        </SelectContent>
      </Select>
    </div>

    <div class="space-y-3">
      <CustomerCard
        v-for="customer in sortedCustomers"
        :key="customer.id"
        :customer="customer"
        @click="goToCustomer(customer.id)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { TrendingUp, TrendingDown } from 'lucide-vue-next'
import { Card } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import { useDemoState } from '../composables/useDemoState'
import CustomerCard from '../components/analytics/CustomerCard.vue'

const router = useRouter()
const route = useRoute()
const { customers, portfolioSummary } = useDemoState()

const sortBy = ref('margin_asc')
const dateRange = ref('30d')

// Check if we're in demo mode
const isDemo = computed(() => route.path.startsWith('/demo'))

function goToCustomer(id: string) {
  if (isDemo.value) {
    router.push(`/demo/customers/${id}`)
  } else {
    router.push(`/customers/${id}`)
  }
}

const sortedCustomers = computed(() => {
  const sorted = [...customers]
  switch (sortBy.value) {
    case 'margin_asc':
      return sorted.sort((a, b) => a.margin - b.margin)
    case 'margin_desc':
      return sorted.sort((a, b) => b.margin - a.margin)
    case 'mrr_desc':
      return sorted.sort((a, b) => b.mrr - a.mrr)
    case 'cost_desc':
      return sorted.sort((a, b) => b.costs.total - a.costs.total)
    default:
      return sorted
  }
})

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
  return 'text-foreground'
})

const marginTrendClass = computed(() => {
  return 'text-muted-foreground'
})
</script>
