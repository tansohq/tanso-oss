<template>
  <div class="p-6 pb-16">
    <!-- Breadcrumb -->
    <div class="flex items-center gap-2 text-sm text-muted-foreground mb-6">
      <router-link :to="isDemo ? '/demo/simulations' : '/simulations'" class="hover:text-foreground"
        >Scenarios</router-link
      >
      <ChevronRight class="h-4 w-4" />
      <span class="text-foreground">{{ simulation?.name || 'Scenario' }}</span>
    </div>

    <div v-if="simulation">
      <!-- Header -->
      <div class="flex items-center justify-between mb-6">
        <div>
          <div class="flex items-center gap-3">
            <h1 class="text-2xl font-semibold text-foreground">{{ simulation.name }}</h1>
            <Badge :class="statusClasses" class="border-0">
              {{ statusLabel }}
            </Badge>
          </div>
          <div class="flex items-center gap-4 mt-2 text-sm text-muted-foreground">
            <span>{{ simulation.segmentName }}</span>
            <span>{{ formatDateRange(simulation.timeRange) }}</span>
            <span>{{ simulation.segmentPreview.customerCount }} customers</span>
          </div>
        </div>
        <div
          v-if="simulation.status === 'completed' || simulation.status === 'rolled_out'"
          class="flex gap-2"
        >
          <Button variant="outline" @click="exportCsv">
            <Download class="h-4 w-4 mr-2" />
            Export CSV
          </Button>
          <Button
            v-if="simulation.status === 'completed' && simulation.winningScenarioId"
            @click="showRolloutDialog = true"
          >
            Roll Out
          </Button>
        </div>
      </div>

      <!-- Running State -->
      <Card v-if="simulation.status === 'running'" class="p-8 text-center mb-6">
        <Loader2 class="h-8 w-8 animate-spin text-primary mx-auto mb-4" />
        <h3 class="text-lg font-medium mb-2">Calculating Impact</h3>
        <p class="text-muted-foreground">
          Analyzing {{ simulation.segmentPreview.customerCount }} customers against 90 days of usage
          data...
        </p>
      </Card>

      <!-- Draft State -->
      <Card v-else-if="simulation.status === 'draft'" class="p-8 text-center mb-6">
        <FileText class="h-8 w-8 text-muted-foreground mx-auto mb-4" />
        <h3 class="text-lg font-medium mb-2">Ready to Run</h3>
        <p class="text-muted-foreground mb-4">
          See how this pricing change would affect revenue and margin.
        </p>
        <Button @click="handleRunSimulation"> Calculate Impact </Button>
      </Card>

      <!-- Results (Completed or Rolled Out) -->
      <template v-else-if="simulation.marginImpact">
        <!-- Hero Section - Lead with Insight -->
        <Card class="mb-6 overflow-hidden">
          <div
            class="bg-gradient-to-r from-emerald-50 to-emerald-100/50 dark:from-emerald-950/20 dark:to-emerald-900/10 p-6"
          >
            <!-- Key Insight at TOP -->
            <div v-if="simulation.keyInsight" class="mb-4">
              <div class="flex items-start gap-3">
                <div class="rounded-full bg-amber-100 p-2 shrink-0">
                  <Lightbulb class="h-5 w-5 text-amber-600" />
                </div>
                <div>
                  <div class="text-sm font-medium text-muted-foreground mb-1">Key Insight</div>
                  <div class="text-base font-medium text-foreground">
                    {{ simulation.keyInsight }}
                  </div>
                </div>
              </div>

              <!-- Impact Summary Bullets -->
              <div class="mt-4 space-y-1.5 pl-12">
                <div class="flex items-center gap-2 text-sm">
                  <CheckCircle class="h-4 w-4 text-emerald-500" />
                  <span
                    >Improve margin by
                    <strong class="text-emerald-600"
                      >+${{ formatCurrency(simulation.marginImpact.marginDelta) }}/month</strong
                    ></span
                  >
                </div>
                <div class="flex items-center gap-2 text-sm">
                  <CheckCircle class="h-4 w-4 text-emerald-500" />
                  <span
                    >{{ simulation.segmentPreview.customerCount }} customers affected ({{
                      lowRiskCount
                    }}
                    low-risk)</span
                  >
                </div>
                <div v-if="highRiskCount > 0" class="flex items-center gap-2 text-sm">
                  <AlertTriangle class="h-4 w-4 text-amber-500" />
                  <span
                    >{{ highRiskCount }} high-risk customer{{ highRiskCount > 1 ? 's' : '' }} need{{
                      highRiskCount === 1 ? 's' : ''
                    }}
                    attention</span
                  >
                </div>
              </div>
            </div>

            <!-- Numbers below insight -->
            <div class="flex items-center justify-between pt-4 border-t border-emerald-200/50">
              <div>
                <div class="flex items-baseline gap-2">
                  <span class="text-2xl font-bold text-emerald-600">
                    +${{ formatCurrency(simulation.marginImpact.marginDelta) }}
                  </span>
                  <span class="text-muted-foreground">
                    ({{ simulation.marginImpact.baselineMarginPct }}% →
                    {{ simulation.marginImpact.scenarioMarginPct }}%)
                  </span>
                </div>
                <!-- Confidence Score -->
                <div v-if="simulation.confidenceScore" class="flex items-center gap-2 mt-1">
                  <div class="flex gap-0.5">
                    <Star
                      v-for="i in 5"
                      :key="i"
                      class="h-3.5 w-3.5"
                      :class="
                        i <= Math.round(simulation.confidenceScore / 20)
                          ? 'fill-amber-400 text-amber-400'
                          : 'text-muted-foreground/30'
                      "
                    />
                  </div>
                  <span class="text-xs text-muted-foreground"
                    >{{ simulation.confidenceScore }}/100 confidence</span
                  >
                </div>
              </div>
              <Button
                variant="ghost"
                size="sm"
                class="text-muted-foreground"
                @click="activeTab = 'confidence'"
              >
                See detailed breakdown
                <ChevronRight class="h-4 w-4 ml-1" />
              </Button>
            </div>
          </div>
        </Card>

        <!-- Tabs -->
        <Tabs v-model="activeTab" class="space-y-4">
          <TabsList class="grid w-full grid-cols-3 lg:w-[400px]">
            <TabsTrigger value="features">By Feature</TabsTrigger>
            <TabsTrigger value="customers">By Customer</TabsTrigger>
            <TabsTrigger value="confidence">Confidence</TabsTrigger>
          </TabsList>

          <!-- Tab 1: By Feature -->
          <TabsContent value="features">
            <Card>
              <div class="p-4 border-b flex items-center justify-between">
                <div>
                  <h3 class="font-semibold">Impact by Feature</h3>
                  <p class="text-sm text-muted-foreground">How each feature's margin changes</p>
                </div>
                <button
                  class="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
                  @click="showFeatureDetails = !showFeatureDetails"
                >
                  <span>{{ showFeatureDetails ? 'Hide' : 'Show' }} details</span>
                  <ChevronRight
                    class="h-4 w-4 transition-transform"
                    :class="{ 'rotate-90': showFeatureDetails }"
                  />
                </button>
              </div>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Feature</TableHead>
                    <TableHead v-if="showFeatureDetails" class="text-right"
                      >Current Price</TableHead
                    >
                    <TableHead v-if="showFeatureDetails" class="text-right">New Price</TableHead>
                    <TableHead v-if="showFeatureDetails" class="text-right">Volume</TableHead>
                    <TableHead v-if="showFeatureDetails" class="text-right">Cost</TableHead>
                    <TableHead class="text-right">Current Margin</TableHead>
                    <TableHead class="text-right">New Margin</TableHead>
                    <TableHead class="text-right">Impact</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  <TableRow v-for="feature in simulation.featureAnalysis" :key="feature.featureKey">
                    <TableCell>
                      <div class="flex items-center gap-2">
                        <div
                          :class="[
                            'w-2 h-2 rounded-full',
                            feature.status === 'negative'
                              ? 'bg-red-500'
                              : feature.status === 'low'
                                ? 'bg-amber-500'
                                : feature.status === 'profitable'
                                  ? 'bg-emerald-500'
                                  : 'bg-emerald-600'
                          ]"
                        />
                        <span class="font-medium">{{ feature.featureName }}</span>
                      </div>
                    </TableCell>
                    <TableCell v-if="showFeatureDetails" class="text-right font-mono">
                      {{
                        feature.currentPrice === 0 ? 'FREE' : `$${feature.currentPrice.toFixed(4)}`
                      }}
                    </TableCell>
                    <TableCell v-if="showFeatureDetails" class="text-right font-mono">
                      ${{ feature.newPrice.toFixed(4) }}
                    </TableCell>
                    <TableCell v-if="showFeatureDetails" class="text-right tabular-nums">{{
                      feature.volume.toLocaleString()
                    }}</TableCell>
                    <TableCell v-if="showFeatureDetails" class="text-right font-mono"
                      >${{ feature.cost.toFixed(4) }}</TableCell
                    >
                    <TableCell class="text-right">
                      <span :class="feature.currentMargin < 0 ? 'text-red-600 font-medium' : ''">
                        ${{ feature.currentMargin.toFixed(2) }}
                      </span>
                    </TableCell>
                    <TableCell class="text-right">
                      <span class="text-emerald-600 font-medium"
                        >${{ feature.newMargin.toFixed(2) }}</span
                      >
                    </TableCell>
                    <TableCell class="text-right">
                      <span class="text-emerald-600 font-medium"
                        >+${{ feature.marginDelta.toFixed(2) }}</span
                      >
                    </TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </Card>
          </TabsContent>

          <!-- Tab 2: Customer Impact -->
          <TabsContent value="customers">
            <Card v-if="simulation.customerImpacts && simulation.customerImpacts.length > 0">
              <div class="p-4 border-b">
                <h3 class="font-semibold">Impact by Customer</h3>
                <p class="text-sm text-muted-foreground">Sorted by churn risk</p>
              </div>

              <!-- Distribution Summary -->
              <div class="p-4 border-b bg-muted/30">
                <div class="grid grid-cols-3 gap-4 text-center">
                  <div>
                    <div class="text-2xl font-bold text-emerald-600">{{ lowRiskCount }}</div>
                    <div class="text-sm text-muted-foreground">Low Risk</div>
                  </div>
                  <div>
                    <div class="text-2xl font-bold text-amber-600">{{ mediumRiskCount }}</div>
                    <div class="text-sm text-muted-foreground">Medium Risk</div>
                  </div>
                  <div>
                    <div class="text-2xl font-bold text-red-600">{{ highRiskCount }}</div>
                    <div class="text-sm text-muted-foreground">High Risk</div>
                  </div>
                </div>
              </div>

              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Customer</TableHead>
                    <TableHead class="text-right">Current MRR</TableHead>
                    <TableHead class="text-right">New MRR</TableHead>
                    <TableHead>Risk</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  <TableRow
                    v-for="customer in paginatedCustomers"
                    :key="customer.customerId"
                    class="cursor-pointer hover:bg-muted/50"
                    @click="goToCustomer(customer.customerId)"
                  >
                    <TableCell class="font-medium text-foreground">
                      {{ customer.customerName }}
                    </TableCell>
                    <TableCell class="text-right tabular-nums"
                      >${{ formatCurrency(customer.currentMrr) }}</TableCell
                    >
                    <TableCell class="text-right tabular-nums">
                      <span>${{ formatCurrency(getNewMrr(customer)) }}</span>
                      <span
                        class="text-xs ml-1"
                        :class="customer.changePercent > 0 ? 'text-emerald-600' : 'text-red-600'"
                      >
                        ({{ customer.changePercent > 0 ? '+' : '' }}{{ customer.changePercent }}%)
                      </span>
                    </TableCell>
                    <TableCell>
                      <Badge :class="getChurnRiskClasses(customer.churnRisk)" class="border-0">
                        {{ customer.churnRisk }}
                      </Badge>
                    </TableCell>
                  </TableRow>
                </TableBody>
              </Table>
              <div
                v-if="showPagination"
                class="flex items-center justify-between px-4 py-3 border-t"
              >
                <span class="text-sm text-muted-foreground">
                  Showing {{ (currentPage - 1) * pageSize + 1 }}-{{
                    Math.min(currentPage * pageSize, sortedCustomerImpacts.length)
                  }}
                  of {{ sortedCustomerImpacts.length }}
                </span>
                <div class="flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    :disabled="currentPage === 1"
                    @click="currentPage--"
                  >
                    Previous
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    :disabled="currentPage >= totalPages"
                    @click="currentPage++"
                  >
                    Next
                  </Button>
                </div>
              </div>
            </Card>
          </TabsContent>

          <!-- Tab 3: Confidence Analysis -->
          <TabsContent value="confidence">
            <div class="grid gap-4 md:grid-cols-2">
              <!-- Data Quality -->
              <Card class="p-4">
                <h4 class="font-semibold mb-3">Data Quality</h4>
                <div class="space-y-3">
                  <div class="flex items-center justify-between">
                    <span class="text-sm text-muted-foreground">Historical data coverage</span>
                    <Badge
                      variant="outline"
                      class="bg-emerald-50 text-emerald-700 border-emerald-200"
                      >94%</Badge
                    >
                  </div>
                  <div class="flex items-center justify-between">
                    <span class="text-sm text-muted-foreground">Event attribution rate</span>
                    <Badge
                      variant="outline"
                      class="bg-emerald-50 text-emerald-700 border-emerald-200"
                      >98%</Badge
                    >
                  </div>
                  <div class="flex items-center justify-between">
                    <span class="text-sm text-muted-foreground">Cost data completeness</span>
                    <Badge
                      variant="outline"
                      class="bg-emerald-50 text-emerald-700 border-emerald-200"
                      >100%</Badge
                    >
                  </div>
                </div>
              </Card>

              <!-- Sample Size -->
              <Card class="p-4">
                <h4 class="font-semibold mb-3">Sample Size</h4>
                <div class="space-y-3">
                  <div class="flex items-center justify-between">
                    <span class="text-sm text-muted-foreground">Customers analyzed</span>
                    <span class="font-medium">{{ simulation.segmentPreview.customerCount }}</span>
                  </div>
                  <div class="flex items-center justify-between">
                    <span class="text-sm text-muted-foreground">Events processed</span>
                    <span class="font-medium">2.4M</span>
                  </div>
                  <div class="flex items-center justify-between">
                    <span class="text-sm text-muted-foreground">Time period</span>
                    <span class="font-medium">90 days</span>
                  </div>
                </div>
              </Card>

              <!-- Rollout Recommendation -->
              <Card class="p-4 md:col-span-2">
                <h4 class="font-semibold mb-3">Rollout Recommendation</h4>
                <div
                  class="p-3 bg-emerald-50 dark:bg-emerald-950/30 rounded-lg border border-emerald-200 dark:border-emerald-800"
                >
                  <div class="flex items-start gap-3">
                    <CheckCircle class="h-5 w-5 text-emerald-600 shrink-0 mt-0.5" />
                    <div>
                      <div class="font-medium text-emerald-700 dark:text-emerald-400">
                        Ready for rollout
                      </div>
                      <div class="text-sm text-muted-foreground mt-1">
                        Based on the confidence score and data quality, this simulation has
                        sufficient evidence to proceed with a gradual rollout. We recommend starting
                        with 10% of customers and monitoring for 2 weeks before full deployment.
                      </div>
                    </div>
                  </div>
                </div>
              </Card>

              <!-- Assumptions & Limitations -->
              <Card class="p-4 md:col-span-2">
                <h4 class="font-semibold mb-3">Assumptions & Limitations</h4>
                <div class="space-y-2 text-sm text-muted-foreground">
                  <div class="flex items-start gap-2">
                    <AlertTriangle class="h-4 w-4 text-amber-500 shrink-0 mt-0.5" />
                    <span
                      >This simulation assumes usage patterns remain constant. Actual results may
                      vary with customer behavior changes.</span
                    >
                  </div>
                  <div class="flex items-start gap-2">
                    <AlertTriangle class="h-4 w-4 text-amber-500 shrink-0 mt-0.5" />
                    <span
                      >Churn risk estimates are based on historical patterns and may not account for
                      competitive dynamics.</span
                    >
                  </div>
                  <div class="flex items-start gap-2">
                    <AlertTriangle class="h-4 w-4 text-amber-500 shrink-0 mt-0.5" />
                    <span
                      >Infrastructure costs are based on current provider rates and may
                      change.</span
                    >
                  </div>
                </div>
              </Card>
            </div>
          </TabsContent>
        </Tabs>
      </template>

      <!-- Fallback to old summary table if no margin data -->
      <template v-else-if="simulation.summaryTable">
        <Card class="mb-6">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Scenario</TableHead>
                <TableHead class="text-right">Revenue</TableHead>
                <TableHead class="text-right">Margin</TableHead>
                <TableHead class="text-right">Churn Risk</TableHead>
                <TableHead></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="row in simulation.summaryTable" :key="row.scenarioId">
                <TableCell class="font-medium">{{ row.scenarioName }}</TableCell>
                <TableCell class="text-right">
                  <div>${{ formatCurrency(row.revenue) }}</div>
                  <div
                    v-if="row.revenuePctChange !== 0"
                    class="text-xs"
                    :class="row.revenuePctChange > 0 ? 'text-emerald-600' : 'text-red-600'"
                  >
                    {{ row.revenuePctChange > 0 ? '+' : '' }}{{ row.revenuePctChange.toFixed(1) }}%
                  </div>
                </TableCell>
                <TableCell class="text-right">
                  <div>{{ row.margin }}%</div>
                  <div
                    v-if="row.marginChange !== 0"
                    class="text-xs"
                    :class="row.marginChange > 0 ? 'text-emerald-600' : 'text-red-600'"
                  >
                    {{ row.marginChange > 0 ? '+' : '' }}{{ row.marginChange }}pp
                  </div>
                </TableCell>
                <TableCell class="text-right">
                  <span class="text-sm">{{ row.churnRiskCount }} at risk</span>
                </TableCell>
                <TableCell class="text-right">
                  <div class="flex gap-1 justify-end">
                    <Badge
                      v-for="badge in row.badges"
                      :key="badge"
                      variant="outline"
                      class="text-xs"
                    >
                      {{ formatBadge(badge) }}
                    </Badge>
                  </div>
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </Card>
      </template>
    </div>

    <!-- Not Found -->
    <Card v-else class="p-8 text-center">
      <AlertCircle class="h-8 w-8 text-muted-foreground mx-auto mb-4" />
      <h3 class="text-lg font-medium mb-2">Scenario Not Found</h3>
      <p class="text-muted-foreground mb-4">
        This scenario may have been deleted or doesn't exist.
      </p>
      <Button variant="outline" @click="router.push(isDemo ? '/demo/simulations' : '/simulations')">
        Back to Scenarios
      </Button>
    </Card>

    <!-- Rollout Confirmation Dialog -->
    <Dialog v-model:open="showRolloutDialog">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Apply This Pricing Change?</DialogTitle>
          <DialogDescription>
            This will update pricing for {{ simulation?.segmentName }}.
          </DialogDescription>
        </DialogHeader>
        <div class="py-4">
          <p class="text-sm text-muted-foreground mb-4">
            <strong>{{ simulation?.segmentPreview.customerCount }}</strong> customers will be
            affected by this change.
          </p>
          <div class="p-3 bg-muted rounded-lg text-sm">
            Applying: <strong>{{ winningScenarioName }}</strong>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="showRolloutDialog = false">Cancel</Button>
          <Button @click="handleRollout">Apply Changes</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ChevronRight,
  Loader2,
  FileText,
  AlertCircle,
  Download,
  Star,
  Lightbulb,
  CheckCircle,
  AlertTriangle
} from 'lucide-vue-next'
import { useDemoState } from '../composables/useDemoState'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { getSimulationStatusColor, getSimulationStatusLabel, getChurnRiskColor } from '../types'
import type { ChurnRisk, SimulationCustomerImpact } from '../types'

const route = useRoute()
const router = useRouter()
const { simulationsData, runSimulation, rolloutSimulation } = useDemoState()

const showRolloutDialog = ref(false)
const currentPage = ref(1)
const pageSize = 10
const activeTab = ref('features')
const showFeatureDetails = ref(false)

// Check if we're in demo mode
const isDemo = computed(() => route.path.startsWith('/demo'))

const simulation = computed(() => {
  const id = route.params.id as string
  return simulationsData.value.find((s) => s.id === id)
})

function goToCustomer(customerId: string) {
  const basePath = isDemo.value ? '/demo/customer' : '/customer'
  router.push({
    path: `${basePath}/${customerId}`,
    query: {
      from: 'simulation',
      simulationId: simulation.value?.id,
      simulationName: simulation.value?.name
    }
  })
}

const statusClasses = computed(() =>
  simulation.value ? getSimulationStatusColor(simulation.value.status) : ''
)
const statusLabel = computed(() =>
  simulation.value ? getSimulationStatusLabel(simulation.value.status) : ''
)

const nonBaselineScenarios = computed(() => {
  if (!simulation.value) return []
  return simulation.value.scenarios.filter((s) => !s.isBaseline)
})

const sortedCustomerImpacts = computed(() => {
  if (!simulation.value?.customerImpacts) return []
  return [...simulation.value.customerImpacts].sort((a, b) => b.changePercent - a.changePercent)
})

// Risk counts
const lowRiskCount = computed(
  () => sortedCustomerImpacts.value.filter((c) => c.churnRisk === 'low').length
)
const mediumRiskCount = computed(
  () => sortedCustomerImpacts.value.filter((c) => c.churnRisk === 'medium').length
)
const highRiskCount = computed(
  () => sortedCustomerImpacts.value.filter((c) => c.churnRisk === 'high').length
)

const totalPages = computed(() => Math.ceil(sortedCustomerImpacts.value.length / pageSize))

const paginatedCustomers = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return sortedCustomerImpacts.value.slice(start, start + pageSize)
})

const showPagination = computed(() => sortedCustomerImpacts.value.length > pageSize)

const winningScenarioName = computed(() => {
  if (!simulation.value?.winningScenarioId) return ''
  const scenario = simulation.value.scenarios.find(
    (s) => s.id === simulation.value?.winningScenarioId
  )
  return scenario?.name || ''
})

function formatCurrency(value: number): string {
  if (value >= 1000000) return (value / 1000000).toFixed(1) + 'M'
  if (value >= 1000) return (value / 1000).toFixed(1) + 'K'
  return value.toLocaleString()
}

function formatDateRange(range: { start: string; end: string }): string {
  const start = new Date(range.start).toLocaleDateString('en-US', {
    month: 'short',
    year: 'numeric'
  })
  const end = new Date(range.end).toLocaleDateString('en-US', { month: 'short', year: 'numeric' })
  return `${start} – ${end}`
}

function formatBadge(badge: string): string {
  switch (badge) {
    case 'highest_revenue':
      return 'Highest Revenue'
    case 'best_margin':
      return 'Best Margin'
    case 'lowest_risk':
      return 'Lowest Risk'
    default:
      return badge
  }
}

function getChurnRiskClasses(risk: ChurnRisk): string {
  return getChurnRiskColor(risk)
}

function getNewMrr(customer: SimulationCustomerImpact): number {
  // Get the last non-baseline scenario's MRR
  const scenarios = nonBaselineScenarios.value
  if (scenarios.length === 0) return customer.currentMrr
  const lastScenario = scenarios[scenarios.length - 1]
  return customer.scenarioMrrs[lastScenario.id] || customer.currentMrr
}

function exportCsv() {
  if (!simulation.value?.customerImpacts) return

  const scenarios = nonBaselineScenarios.value
  const headers = [
    'Customer',
    'Current MRR',
    ...scenarios.map((s) => `${s.name} MRR`),
    'Change %',
    'Current Margin',
    'New Margin',
    'Churn Risk'
  ]

  const rows = sortedCustomerImpacts.value.map((c) => [
    `"${c.customerName}"`,
    c.currentMrr,
    ...scenarios.map((s) => c.scenarioMrrs[s.id] || 0),
    c.changePercent,
    c.currentMargin,
    c.newMargin,
    c.churnRisk
  ])

  const csv = [headers.join(','), ...rows.map((row) => row.join(','))].join('\n')

  const blob = new Blob([csv], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${simulation.value.name.toLowerCase().replace(/\s+/g, '-')}-results.csv`
  a.click()
  URL.revokeObjectURL(url)
}

function handleRunSimulation() {
  if (simulation.value) {
    runSimulation(simulation.value.id)
  }
}

function handleRollout() {
  if (simulation.value?.winningScenarioId) {
    rolloutSimulation(simulation.value.id, simulation.value.winningScenarioId)
    showRolloutDialog.value = false
  }
}
</script>
