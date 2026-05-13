<template>
  <div class="p-6 pb-16">
    <!-- Breadcrumb -->
    <div class="flex items-center gap-2 text-sm text-muted-foreground mb-6">
      <router-link to="/demo/simulations" class="hover:text-foreground">Simulations</router-link>
      <ChevronRight class="h-4 w-4" />
      <span class="text-foreground">New Simulation</span>
    </div>

    <!-- Progress Steps -->
    <div class="flex items-center gap-4 mb-8">
      <div v-for="(step, index) in steps" :key="step.id" class="flex items-center gap-2">
        <div
          class="flex items-center justify-center w-8 h-8 rounded-full text-sm font-medium"
          :class="stepClasses(index)"
        >
          {{ index + 1 }}
        </div>
        <span
          class="text-sm"
          :class="currentStep >= index ? 'text-foreground' : 'text-muted-foreground'"
        >
          {{ step.label }}
        </span>
        <ChevronRight v-if="index < steps.length - 1" class="h-4 w-4 text-muted-foreground ml-2" />
      </div>
    </div>

    <!-- Step Content -->
    <Card class="p-6">
      <!-- Step 1: Define -->
      <div v-if="currentStep === 0">
        <h2 class="text-lg font-semibold mb-4">Define Simulation</h2>
        <div class="space-y-6">
          <div>
            <Label>Name</Label>
            <Input
              v-model="formData.name"
              placeholder="e.g., Q1 2025 Price Increase Analysis"
              class="mt-1.5"
            />
          </div>

          <div>
            <Label>Segment</Label>
            <Select v-model="formData.segmentId" class="mt-1.5">
              <SelectTrigger>
                <SelectValue placeholder="Select a segment..." />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Customers</SelectItem>
                <SelectItem v-for="segment in segmentsData" :key="segment.id" :value="segment.id">
                  {{ segment.name }}
                </SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div>
              <Label>Start Date</Label>
              <Input v-model="formData.startDate" type="date" class="mt-1.5" />
            </div>
            <div>
              <Label>End Date</Label>
              <Input v-model="formData.endDate" type="date" class="mt-1.5" />
            </div>
          </div>

          <!-- Preview -->
          <Card v-if="segmentPreview" class="p-4 bg-muted/50">
            <div class="text-sm font-medium text-muted-foreground mb-2">Preview</div>
            <div class="flex items-center gap-4 text-sm">
              <span>{{ segmentPreview.customerCount }} customers</span>
              <span class="text-muted-foreground">·</span>
              <span>${{ formatCurrency(segmentPreview.totalMrr) }} MRR</span>
              <span class="text-muted-foreground">·</span>
              <span>{{ segmentPreview.avgMargin }}% avg margin</span>
            </div>
          </Card>
        </div>
      </div>

      <!-- Step 2: Build Scenarios -->
      <div v-if="currentStep === 1">
        <h2 class="text-lg font-semibold mb-4">Build Scenarios</h2>
        <div class="grid grid-cols-2 gap-6">
          <!-- Baseline (Left) -->
          <Card class="p-4 bg-muted/30">
            <div class="flex items-center gap-2 mb-3">
              <div class="w-3 h-3 rounded-full bg-slate-400"></div>
              <span class="font-medium">Baseline (Current)</span>
            </div>
            <p class="text-sm text-muted-foreground">Current pricing structure for comparison</p>
          </Card>

          <!-- Scenarios (Right) -->
          <div class="space-y-4">
            <div
              v-for="(scenario, index) in formData.scenarios"
              :key="index"
              class="border rounded-lg p-4"
            >
              <div class="flex items-center justify-between mb-3">
                <Input v-model="scenario.name" placeholder="Scenario name" class="w-48" />
                <Button
                  variant="ghost"
                  size="icon"
                  @click="removeScenario(index)"
                  :disabled="formData.scenarios.length <= 1"
                >
                  <X class="h-4 w-4" />
                </Button>
              </div>

              <div class="grid grid-cols-2 gap-3">
                <div>
                  <Label class="text-xs">Change Type</Label>
                  <Select v-model="scenario.changeType">
                    <SelectTrigger class="mt-1">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="percentage_increase">Percentage Increase</SelectItem>
                      <SelectItem value="percentage_decrease">Percentage Decrease</SelectItem>
                      <SelectItem value="flat_monthly">Flat Monthly Price</SelectItem>
                      <SelectItem value="per_unit">Per-Unit Price</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div>
                  <Label class="text-xs">Value</Label>
                  <div class="flex items-center gap-1 mt-1">
                    <span
                      v-if="
                        scenario.changeType === 'flat_monthly' || scenario.changeType === 'per_unit'
                      "
                      class="text-muted-foreground"
                      >$</span
                    >
                    <Input v-model.number="scenario.value" type="number" placeholder="0" />
                    <span
                      v-if="
                        scenario.changeType === 'percentage_increase' ||
                        scenario.changeType === 'percentage_decrease'
                      "
                      class="text-muted-foreground"
                      >%</span
                    >
                  </div>
                </div>
              </div>
            </div>

            <Button
              variant="outline"
              class="w-full"
              @click="addScenario"
              :disabled="formData.scenarios.length >= 5"
            >
              <Plus class="h-4 w-4 mr-2" />
              Add Scenario
            </Button>
            <p
              v-if="formData.scenarios.length >= 5"
              class="text-xs text-muted-foreground text-center"
            >
              Maximum 5 scenarios reached
            </p>
          </div>
        </div>
      </div>

      <!-- Step 3: Review & Run -->
      <div v-if="currentStep === 2">
        <h2 class="text-lg font-semibold mb-4">Review & Run</h2>
        <div class="space-y-4">
          <Card class="p-4">
            <div class="grid grid-cols-2 gap-4 text-sm">
              <div>
                <span class="text-muted-foreground">Name</span>
                <p class="font-medium">{{ formData.name || 'Untitled' }}</p>
              </div>
              <div>
                <span class="text-muted-foreground">Segment</span>
                <p class="font-medium">{{ selectedSegmentName }}</p>
              </div>
              <div>
                <span class="text-muted-foreground">Time Range</span>
                <p class="font-medium">{{ formData.startDate }} to {{ formData.endDate }}</p>
              </div>
              <div>
                <span class="text-muted-foreground">Customers</span>
                <p class="font-medium">{{ segmentPreview?.customerCount || 0 }}</p>
              </div>
            </div>
          </Card>

          <Card class="p-4">
            <div class="text-sm text-muted-foreground mb-2">Scenarios</div>
            <div class="space-y-2">
              <div class="flex items-center gap-2 text-sm">
                <div class="w-2 h-2 rounded-full bg-slate-400"></div>
                <span>Baseline (Current pricing)</span>
              </div>
              <div
                v-for="(scenario, index) in formData.scenarios"
                :key="index"
                class="flex items-center gap-2 text-sm"
              >
                <div class="w-2 h-2 rounded-full bg-primary"></div>
                <span
                  >{{ scenario.name || `Scenario ${index + 1}` }}:
                  {{ formatScenarioChange(scenario) }}</span
                >
              </div>
            </div>
          </Card>
        </div>
      </div>
    </Card>

    <!-- Footer Actions -->
    <div class="flex items-center justify-between mt-6">
      <Button variant="outline" @click="goBack">
        {{ currentStep === 0 ? 'Cancel' : 'Back' }}
      </Button>
      <Button @click="goNext" :disabled="!canProceed">
        {{ currentStep === 2 ? 'Run Simulation' : 'Continue' }}
        <ChevronRight v-if="currentStep < 2" class="h-4 w-4 ml-1" />
      </Button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ChevronRight, Plus, X } from 'lucide-vue-next'
import { useDemoState } from '../composables/useDemoState'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import type { PricingChangeType, Simulation, SimulationScenario } from '../types'

const router = useRouter()
const route = useRoute()
const { segmentsData, createSimulation, runSimulation } = useDemoState()

const isDemo = computed(() => route.path.startsWith('/demo'))

const steps = [
  { id: 'define', label: 'Define' },
  { id: 'scenarios', label: 'Build Scenarios' },
  { id: 'review', label: 'Review & Run' }
]

const currentStep = ref(0)

interface ScenarioForm {
  name: string
  changeType: PricingChangeType
  value: number
}

const formData = ref({
  name: '',
  segmentId: '',
  startDate: new Date().toISOString().split('T')[0],
  endDate: new Date(Date.now() + 90 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
  scenarios: [
    { name: '', changeType: 'percentage_increase' as PricingChangeType, value: 10 }
  ] as ScenarioForm[]
})

const selectedSegment = computed(() => {
  if (formData.value.segmentId === 'all' || !formData.value.segmentId) return null
  return segmentsData.value.find((c) => c.id === formData.value.segmentId)
})

const selectedSegmentName = computed(() => {
  if (formData.value.segmentId === 'all') return 'All Customers'
  return selectedSegment.value?.name || 'Not selected'
})

const segmentPreview = computed(() => {
  if (formData.value.segmentId === 'all') {
    return { customerCount: 342, totalMrr: 485000, avgMargin: 65 }
  }
  if (selectedSegment.value) {
    return {
      customerCount: selectedSegment.value.customerCount,
      totalMrr: selectedSegment.value.mrr,
      avgMargin: selectedSegment.value.grossMarginPct || 0
    }
  }
  return null
})

const canProceed = computed(() => {
  if (currentStep.value === 0) {
    return (
      formData.value.name &&
      formData.value.segmentId &&
      formData.value.startDate &&
      formData.value.endDate
    )
  }
  if (currentStep.value === 1) {
    return formData.value.scenarios.length > 0 && formData.value.scenarios.every((s) => s.value > 0)
  }
  return true
})

function stepClasses(index: number) {
  if (index < currentStep.value) {
    return 'bg-primary text-primary-foreground'
  }
  if (index === currentStep.value) {
    return 'bg-primary text-primary-foreground'
  }
  return 'bg-muted text-muted-foreground'
}

function addScenario() {
  if (formData.value.scenarios.length < 5) {
    formData.value.scenarios.push({
      name: '',
      changeType: 'percentage_increase',
      value: 0
    })
  }
}

function removeScenario(index: number) {
  if (formData.value.scenarios.length > 1) {
    formData.value.scenarios.splice(index, 1)
  }
}

function formatScenarioChange(scenario: ScenarioForm): string {
  switch (scenario.changeType) {
    case 'percentage_increase':
      return `+${scenario.value}%`
    case 'percentage_decrease':
      return `-${scenario.value}%`
    case 'flat_monthly':
      return `$${scenario.value}/mo`
    case 'per_unit':
      return `$${scenario.value}/unit`
    default:
      return ''
  }
}

function formatCurrency(value: number): string {
  if (value >= 1000000) return (value / 1000000).toFixed(1) + 'M'
  if (value >= 1000) return (value / 1000).toFixed(0) + 'K'
  return value.toLocaleString()
}

function goBack() {
  if (currentStep.value === 0) {
    router.push({ name: isDemo.value ? 'demo-simulations' : 'simulations' })
  } else {
    currentStep.value--
  }
}

function goNext() {
  if (currentStep.value < 2) {
    currentStep.value++
  } else {
    submitSimulation()
  }
}

function submitSimulation() {
  const newId = `sim_${Date.now()}`

  const scenarios: SimulationScenario[] = [
    {
      id: 'scen_baseline',
      name: 'Baseline (Current)',
      isBaseline: true,
      pricingChange: null
    },
    ...formData.value.scenarios.map((s, i) => ({
      id: `scen_${i + 1}`,
      name: s.name || `Scenario ${i + 1}`,
      isBaseline: false,
      pricingChange: {
        type: s.changeType,
        value: s.value,
        label: formatScenarioChange(s)
      }
    }))
  ]

  const simulation: Simulation = {
    id: newId,
    name: formData.value.name,
    segmentId: formData.value.segmentId === 'all' ? null : formData.value.segmentId,
    segmentName: selectedSegmentName.value,
    status: 'draft',
    timeRange: {
      start: formData.value.startDate,
      end: formData.value.endDate
    },
    scenarios,
    segmentPreview: segmentPreview.value || { customerCount: 0, totalMrr: 0, avgMargin: 0 },
    createdAt: new Date().toISOString().split('T')[0]
  }

  createSimulation(simulation)
  runSimulation(newId)
  router.push({
    name: isDemo.value ? 'demo-simulation-detail' : 'simulation-detail',
    params: { id: newId }
  })
}
</script>
