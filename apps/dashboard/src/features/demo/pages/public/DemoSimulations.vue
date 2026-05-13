<template>
  <div class="p-6 pb-20">
    <!-- Header -->
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-semibold text-foreground">Pricing Simulations</h1>
        <p class="text-muted-foreground mt-1">Test pricing changes before going live</p>
      </div>
    </div>

    <div class="grid grid-cols-2 gap-6">
      <!-- Simulation Controls -->
      <Card class="p-6">
        <h3 class="text-lg font-semibold mb-6">Configure Simulation</h3>

        <div class="space-y-6">
          <!-- Plan Selector -->
          <div>
            <label class="block text-sm font-medium mb-2">Select Plan</label>
            <div class="flex gap-2">
              <Button
                v-for="plan in plans"
                :key="plan.name"
                :variant="selectedPlan === plan.name ? 'default' : 'outline'"
                size="sm"
                @click="selectPlan(plan.name)"
              >
                {{ plan.name }}
              </Button>
            </div>
          </div>

          <!-- Price Change Slider -->
          <div>
            <label class="block text-sm font-medium mb-2">
              Price Change:
              <span :class="priceChange >= 0 ? 'text-emerald-500' : 'text-red-500'">
                {{ priceChange >= 0 ? '+' : '' }}{{ priceChange }}%
              </span>
            </label>
            <input
              type="range"
              v-model.number="priceChange"
              min="-30"
              max="50"
              class="w-full h-2 bg-muted rounded-lg appearance-none cursor-pointer accent-primary"
            />
            <div class="flex justify-between text-xs text-muted-foreground mt-1">
              <span>-30%</span>
              <span>0%</span>
              <span>+50%</span>
            </div>
          </div>

          <!-- Current vs New Price -->
          <div class="pt-4 border-t">
            <div class="flex items-center justify-between mb-3">
              <span class="text-sm text-muted-foreground">Current Price</span>
              <span class="font-semibold">${{ currentPlanData?.price || 0 }}/mo</span>
            </div>
            <div class="flex items-center justify-between mb-3">
              <span class="text-sm text-muted-foreground">New Price</span>
              <span class="font-semibold text-primary">${{ newPrice }}/mo</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-sm text-muted-foreground">Affected Customers</span>
              <span class="font-semibold">{{ affectedCustomers }}</span>
            </div>
          </div>

          <Button class="w-full" @click="runSimulation">
            <Play class="w-4 h-4 mr-2" />
            Run Simulation
          </Button>
        </div>
      </Card>

      <!-- Simulation Results -->
      <Card class="p-6">
        <h3 class="text-lg font-semibold mb-6">Projected Impact</h3>

        <div
          v-if="!showResults"
          class="flex flex-col items-center justify-center h-64 text-muted-foreground"
        >
          <FlaskConical class="w-12 h-12 mb-4 opacity-50" />
          <p>Configure and run a simulation to see results</p>
        </div>

        <div v-else class="space-y-6">
          <!-- Impact Cards -->
          <div class="grid grid-cols-2 gap-4">
            <div class="bg-muted/50 rounded-lg p-4">
              <div class="text-sm text-muted-foreground mb-1">Revenue Impact</div>
              <div
                class="text-2xl font-bold flex items-center gap-1"
                :class="revenueImpact >= 0 ? 'text-emerald-500' : 'text-red-500'"
              >
                <ArrowUpRight v-if="revenueImpact >= 0" class="w-5 h-5" />
                <ArrowDownRight v-else class="w-5 h-5" />
                ${{ Math.abs(revenueImpact).toLocaleString() }}/mo
              </div>
            </div>
            <div class="bg-muted/50 rounded-lg p-4">
              <div class="text-sm text-muted-foreground mb-1">Margin Impact</div>
              <div
                class="text-2xl font-bold flex items-center gap-1"
                :class="marginImpact >= 0 ? 'text-emerald-500' : 'text-red-500'"
              >
                <ArrowUpRight v-if="marginImpact >= 0" class="w-5 h-5" />
                <ArrowDownRight v-else class="w-5 h-5" />
                {{ Math.abs(marginImpact).toFixed(1) }}%
              </div>
            </div>
          </div>

          <!-- Details -->
          <div class="space-y-3">
            <div class="flex justify-between items-center">
              <span class="text-sm text-muted-foreground">Current MRR</span>
              <span class="font-medium">${{ currentMrr.toLocaleString() }}</span>
            </div>
            <div class="flex justify-between items-center">
              <span class="text-sm text-muted-foreground">Projected MRR</span>
              <span class="font-medium text-primary">${{ projectedMrr.toLocaleString() }}</span>
            </div>
            <div class="h-px bg-border my-2" />
            <div class="flex justify-between items-center">
              <span class="text-sm text-muted-foreground">Current Margin</span>
              <span class="font-medium" :class="getMarginClass(currentMargin)">
                {{ currentMargin.toFixed(1) }}%
              </span>
            </div>
            <div class="flex justify-between items-center">
              <span class="text-sm text-muted-foreground">Projected Margin</span>
              <span class="font-medium" :class="getMarginClass(projectedMargin)">
                {{ projectedMargin.toFixed(1) }}%
              </span>
            </div>
          </div>

          <!-- Warnings -->
          <div
            v-if="projectedMargin < 40"
            class="flex items-center gap-2 p-3 bg-amber-500/10 border border-amber-500/30 rounded-lg text-amber-600 text-sm"
          >
            <AlertTriangle class="w-4 h-4 shrink-0" />
            <span>Warning: Projected margin below 40% threshold</span>
          </div>

          <div
            v-if="marginImpact > 5"
            class="flex items-center gap-2 p-3 bg-emerald-500/10 border border-emerald-500/30 rounded-lg text-emerald-600 text-sm"
          >
            <Check class="w-4 h-4 shrink-0" />
            <span>This change would significantly improve margins</span>
          </div>
        </div>
      </Card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  Play,
  FlaskConical,
  ArrowUpRight,
  ArrowDownRight,
  AlertTriangle,
  Check
} from 'lucide-vue-next'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { useDemoState } from '../../composables/useDemoState'

const { portfolioSummary } = useDemoState()

const plans = [
  { name: 'Starter', price: 99 },
  { name: 'Growth', price: 299 },
  { name: 'Enterprise', price: 999 }
]

const selectedPlan = ref('Growth')
const priceChange = ref(0)
const showResults = ref(false)

const currentPlanData = computed(() => plans.find((p) => p.name === selectedPlan.value))

const newPrice = computed(() => {
  const current = currentPlanData.value?.price || 0
  return Math.round(current * (1 + priceChange.value / 100))
})

const affectedCustomers = computed(() => {
  // Count customers on the selected plan (simulated)
  const planCustomerCounts: Record<string, number> = {
    Starter: 8,
    Growth: 12,
    Enterprise: 5
  }
  return planCustomerCounts[selectedPlan.value] || 0
})

const currentMrr = computed(() => {
  const price = currentPlanData.value?.price || 0
  return price * affectedCustomers.value
})

const projectedMrr = computed(() => {
  return newPrice.value * affectedCustomers.value
})

const revenueImpact = computed(() => {
  return projectedMrr.value - currentMrr.value
})

const currentMargin = computed(() => {
  // Use portfolio average margin as baseline
  return portfolioSummary.avgMargin * 100
})

const projectedMargin = computed(() => {
  if (priceChange.value === 0) return currentMargin.value
  // Margin improves with price increases (costs stay same)
  const marginDelta = priceChange.value * 0.6 // Price change partially flows to margin
  return currentMargin.value + marginDelta
})

const marginImpact = computed(() => {
  return projectedMargin.value - currentMargin.value
})

function selectPlan(planName: string) {
  selectedPlan.value = planName
  showResults.value = false
}

function runSimulation() {
  showResults.value = true
}

function getMarginClass(margin: number) {
  if (margin >= 70) return 'text-emerald-500'
  if (margin >= 40) return 'text-amber-500'
  return 'text-red-500'
}
</script>
