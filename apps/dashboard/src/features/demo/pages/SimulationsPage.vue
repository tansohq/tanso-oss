<template>
  <div class="p-6 pb-16 overflow-x-hidden">
    <!-- Header with updated copy -->
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-semibold text-foreground">Model Pricing Changes</h1>
        <p class="text-muted-foreground mt-1">See the impact before you commit</p>
      </div>
      <Button @click="navigateToNew">
        <Plus class="h-4 w-4 mr-2" />
        New Scenario
      </Button>
    </div>

    <!-- Pricing Opportunities Banner -->
    <button
      v-if="opportunities.length > 0"
      class="w-full flex items-center justify-between p-3 mb-6 rounded-lg bg-red-50 border border-red-200 hover:bg-red-100 transition-colors text-left"
      @click="showOpportunities = true"
    >
      <div class="flex items-center gap-3">
        <div class="rounded-full bg-red-100 p-1.5">
          <AlertTriangle class="h-4 w-4 text-red-600" />
        </div>
        <div>
          <span class="font-medium text-red-900"
            >{{ opportunities.length }} pricing issues detected</span
          >
          <span class="text-red-700 ml-2"
            >-${{ formatCurrency(totalMonthlyLoss) }}/mo potential loss</span
          >
        </div>
      </div>
      <ChevronRight class="h-5 w-5 text-red-600" />
    </button>

    <!-- Opportunities Sheet -->
    <Sheet v-model:open="showOpportunities">
      <SheetContent class="w-full sm:max-w-md overflow-y-auto">
        <SheetHeader>
          <SheetTitle>Pricing Issues</SheetTitle>
          <SheetDescription>
            {{ opportunities.length }} issues · -${{ formatCurrency(totalMonthlyLoss) }}/mo
            potential loss
          </SheetDescription>
        </SheetHeader>
        <div class="mt-6 space-y-4">
          <OpportunityCard
            v-for="opp in opportunities"
            :key="opp.id"
            :opportunity="opp"
            @model-change="handleModelChange"
          />
        </div>
      </SheetContent>
    </Sheet>

    <!-- Simulations Section -->
    <div class="space-y-4">
      <div class="flex items-center justify-between">
        <h2 class="text-lg font-semibold text-foreground">Your Scenarios</h2>
        <!-- Status Filters -->
        <div v-if="simulationsData.length > 0" class="flex items-center gap-1">
          <Button
            v-for="filter in statusFilters"
            :key="filter.value"
            :variant="statusFilter === filter.value ? 'secondary' : 'ghost'"
            size="sm"
            @click="statusFilter = filter.value"
          >
            {{ filter.label }}
          </Button>
        </div>
      </div>

      <!-- Simulations Grid -->
      <div
        v-if="filteredSimulations.length > 0"
        class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"
      >
        <SimulationCard
          v-for="simulation in filteredSimulations"
          :key="simulation.id"
          :simulation="simulation"
          @click="navigateToDetail(simulation.id)"
        />
      </div>

      <!-- Empty State - with guidance -->
      <Card v-else-if="simulationsData.length === 0" class="p-12 text-center">
        <div class="inline-flex items-center justify-center w-16 h-16 rounded-full bg-muted mb-4">
          <FlaskConical class="h-8 w-8 text-muted-foreground" />
        </div>
        <h3 class="text-lg font-semibold mb-2">No scenarios yet</h3>
        <p class="text-muted-foreground mb-6 max-w-sm mx-auto">
          Model a price change to see how it affects revenue and margin before rolling it out.
        </p>
        <div class="flex flex-col sm:flex-row gap-3 justify-center">
          <Button v-if="opportunities.length > 0" variant="outline" @click="scrollToOpportunities">
            <AlertTriangle class="h-4 w-4 mr-2" />
            Start from an Issue
          </Button>
          <Button @click="navigateToNew">
            <Plus class="h-4 w-4 mr-2" />
            Create from Scratch
          </Button>
        </div>
      </Card>

      <!-- Filtered Empty State -->
      <div v-else class="text-center py-12 text-muted-foreground">
        <p>No {{ statusFilter }} scenarios found</p>
        <Button variant="link" class="mt-2" @click="statusFilter = 'all'">
          View all scenarios
        </Button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Plus, FlaskConical, AlertTriangle, ChevronRight } from 'lucide-vue-next'
import { useDemoState } from '../composables/useDemoState'
import { pricingOpportunities } from '../data/mockData'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle
} from '@/components/ui/sheet'
import SimulationCard from '../components/simulations/SimulationCard.vue'
import OpportunityCard from '../components/opportunities/OpportunityCard.vue'
import type { PricingOpportunity, SimulationStatus } from '../types'

const router = useRouter()
const route = useRoute()
const { simulationsData } = useDemoState()

const isDemo = computed(() => route.path.startsWith('/demo'))
const showOpportunities = ref(false)

// Status filter
const statusFilter = ref<'all' | SimulationStatus>('all')

// Status counts
const statusCounts = computed(() => ({
  all: simulationsData.value.length,
  draft: simulationsData.value.filter((s) => s.status === 'draft').length,
  running: simulationsData.value.filter((s) => s.status === 'running').length,
  completed: simulationsData.value.filter((s) => s.status === 'completed').length,
  rolled_out: simulationsData.value.filter((s) => s.status === 'rolled_out').length
}))

// Status filter options
const statusFilters = computed(() => {
  const filters: Array<{ label: string; value: 'all' | SimulationStatus }> = [
    { label: `All (${statusCounts.value.all})`, value: 'all' }
  ]
  if (statusCounts.value.draft > 0) {
    filters.push({ label: `Draft (${statusCounts.value.draft})`, value: 'draft' })
  }
  if (statusCounts.value.running > 0) {
    filters.push({ label: `Running (${statusCounts.value.running})`, value: 'running' })
  }
  if (statusCounts.value.completed > 0) {
    filters.push({ label: `Completed (${statusCounts.value.completed})`, value: 'completed' })
  }
  if (statusCounts.value.rolled_out > 0) {
    filters.push({ label: `Rolled Out (${statusCounts.value.rolled_out})`, value: 'rolled_out' })
  }
  return filters
})

// Filtered simulations
const filteredSimulations = computed(() => {
  if (statusFilter.value === 'all') return simulationsData.value
  return simulationsData.value.filter((s) => s.status === statusFilter.value)
})

const opportunities = computed(() => pricingOpportunities)

const totalMonthlyLoss = computed(() =>
  opportunities.value.reduce((sum, o) => sum + (o.monthlyLoss || 0), 0)
)

function formatCurrency(value: number): string {
  if (value >= 1000000) return (value / 1000000).toFixed(1) + 'M'
  if (value >= 1000) return (value / 1000).toFixed(1) + 'K'
  return value.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 0 })
}

function navigateToNew() {
  router.push({ name: isDemo.value ? 'demo-simulation-new' : 'simulation-new' })
}

function navigateToDetail(id: string) {
  router.push({
    name: isDemo.value ? 'demo-simulation-detail' : 'simulation-detail',
    params: { id }
  })
}

function handleModelChange(opportunity: PricingOpportunity) {
  router.push({
    name: isDemo.value ? 'demo-simulation-new' : 'simulation-new',
    query: {
      featureKey: opportunity.featureKey,
      suggestedPrice: opportunity.suggestedPrice.toString()
    }
  })
}

function scrollToOpportunities() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>
