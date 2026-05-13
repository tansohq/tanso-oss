<template>
  <div class="p-6 pb-16">
    <!-- Breadcrumb -->
    <div class="flex items-center gap-2 text-sm text-muted-foreground mb-6">
      <router-link :to="isDemo ? '/demo/plans' : '/plans'" class="hover:text-foreground"
        >Plans</router-link
      >
      <ChevronRight class="h-4 w-4" />
      <span class="text-foreground">{{ plan?.name || 'Plan' }}</span>
    </div>

    <div v-if="plan">
      <!-- Header -->
      <div class="flex items-center justify-between mb-6">
        <div>
          <h1 class="text-2xl font-semibold text-foreground">{{ plan.name }}</h1>
          <p class="text-muted-foreground mt-1 font-mono text-sm">{{ plan.externalId }}</p>
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button>
              Add Version
              <ChevronDown class="h-4 w-4 ml-2" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent>
            <DropdownMenuItem @click="handleCreateVersion">Create new version</DropdownMenuItem>
            <DropdownMenuItem @click="handleDuplicateVersion"
              >Duplicate current version</DropdownMenuItem
            >
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      <!-- Tabs -->
      <div class="flex gap-2 mb-6 border-b">
        <button
          :class="[
            'px-4 py-2 text-sm font-medium border-b-2 -mb-px',
            activeTab === 'overview'
              ? 'border-primary text-foreground'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          ]"
          @click="activeTab = 'overview'"
        >
          Overview
        </button>
        <button
          :class="[
            'px-4 py-2 text-sm font-medium border-b-2 -mb-px',
            activeTab === 'subscriptions'
              ? 'border-primary text-foreground'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          ]"
          @click="activeTab = 'subscriptions'"
        >
          Subscriptions
        </button>
        <button
          :class="[
            'px-4 py-2 text-sm font-medium border-b-2 -mb-px',
            activeTab === 'migrations'
              ? 'border-primary text-foreground'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          ]"
          @click="activeTab = 'migrations'"
        >
          Migrations
        </button>
        <button
          :class="[
            'px-4 py-2 text-sm font-medium border-b-2 -mb-px',
            activeTab === 'alerts'
              ? 'border-primary text-foreground'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          ]"
          @click="activeTab = 'alerts'"
        >
          Alerts
        </button>
      </div>

      <!-- Overview Tab -->
      <div v-if="activeTab === 'overview'" class="space-y-6 max-w-4xl">
        <!-- Plan Info -->
        <Card class="p-6">
          <div class="grid grid-cols-3 gap-6 text-sm">
            <div>
              <div class="text-muted-foreground mb-1">Status</div>
              <Badge
                :class="
                  plan.status === 'active'
                    ? 'bg-green-100 text-green-700 border-0'
                    : 'bg-gray-100 text-gray-700 border-0'
                "
              >
                {{ plan.status }}
              </Badge>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Billing cycle</div>
              <div class="font-medium capitalize">{{ plan.billingCycle }}</div>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Currency</div>
              <div class="font-medium">{{ plan.currency }}</div>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">External ID</div>
              <div class="font-mono">{{ plan.externalId }}</div>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Net terms</div>
              <div class="font-medium">{{ plan.netTerms }}</div>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Subscriptions</div>
              <div class="font-medium">{{ plan.subscriptionCount }}</div>
            </div>
          </div>
        </Card>

        <!-- Version Selector -->
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-3">
            <span class="text-sm text-muted-foreground">Version:</span>
            <Select v-model="selectedVersionNum">
              <SelectTrigger class="w-48">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem v-for="v in plan.versions" :key="v.version" :value="String(v.version)">
                  <div class="flex items-center gap-2">
                    <span>v{{ v.version }}</span>
                    <Badge
                      v-if="v.isDefault"
                      class="bg-blue-100 text-blue-700 border-0 text-[10px] px-1.5"
                    >
                      Default
                    </Badge>
                    <Badge :class="getVersionStatusClass(v.status)" class="text-[10px] px-1.5">
                      {{ v.status }}
                    </Badge>
                  </div>
                </SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <!-- Version Status Alert -->
        <div
          v-if="selectedVersion?.status === 'draft'"
          class="flex items-center gap-2 p-3 rounded-lg border border-amber-200 bg-amber-50"
        >
          <AlertTriangle class="h-4 w-4 text-amber-600" />
          <span class="text-sm text-amber-700"
            >This version is a draft. Publish it to make it available for new subscriptions.</span
          >
        </div>

        <!-- Features -->
        <Card class="p-6">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-sm font-medium">Features</h3>
            <Button
              variant="outline"
              size="sm"
              v-if="selectedVersion?.status === 'draft'"
              @click="openAddUsageFeeModal"
            >
              <Plus class="h-4 w-4 mr-1" />
              Add Feature
            </Button>
          </div>
          <Table v-if="selectedVersion?.usageBasedFees.length">
            <TableHeader>
              <TableRow>
                <TableHead>Feature</TableHead>
                <TableHead>Cycle</TableHead>
                <TableHead>Price Model</TableHead>
                <TableHead v-if="selectedVersion?.status === 'draft'" class="w-12"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <template v-for="fee in selectedVersion?.usageBasedFees" :key="fee.id">
                <TableRow class="cursor-pointer" @click="toggleFeeExpansion(fee.id)">
                  <TableCell class="font-medium">{{ fee.featureName }}</TableCell>
                  <TableCell>{{ fee.billingCycle === 'monthly' ? 'Monthly' : 'Annual' }}</TableCell>
                  <TableCell>
                    <div class="flex items-center gap-2">
                      <Badge variant="outline">{{ formatPriceModel(fee) }}</Badge>
                      <ChevronDown
                        v-if="fee.matrixDimensions || fee.tiers || fee.packageTiers"
                        class="h-4 w-4 text-muted-foreground transition-transform"
                        :class="expandedFees.includes(fee.id) && 'rotate-180'"
                      />
                    </div>
                  </TableCell>
                  <TableCell v-if="selectedVersion?.status === 'draft'" @click.stop>
                    <Button
                      variant="ghost"
                      size="sm"
                      class="h-8 w-8 p-0"
                      @click="openEditUsageFeeModal(fee)"
                    >
                      <Pencil class="h-3.5 w-3.5 text-muted-foreground" />
                    </Button>
                  </TableCell>
                </TableRow>
                <!-- Matrix Pricing Expansion -->
                <TableRow v-if="expandedFees.includes(fee.id) && fee.matrixDimensions">
                  <TableCell
                    :colspan="selectedVersion?.status === 'draft' ? 4 : 3"
                    class="bg-muted/50 p-4"
                  >
                    <div class="text-sm font-medium mb-3">
                      {{ fee.featureName }} - Matrix Pricing
                    </div>
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead class="uppercase text-xs">{{
                            fee.matrixDimensions[0].key
                          }}</TableHead>
                          <TableHead class="uppercase text-xs">Unit Price</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        <TableRow v-for="val in fee.matrixDimensions[0].values" :key="val.value">
                          <TableCell class="font-mono">{{ val.value }}</TableCell>
                          <TableCell class="tabular-nums"
                            >${{
                              val.unitPrice.toFixed(fee.featureId.includes('token') ? 4 : 2)
                            }}</TableCell
                          >
                        </TableRow>
                      </TableBody>
                    </Table>
                    <div class="text-xs text-muted-foreground mt-2">
                      First dimension: {{ fee.matrixDimensions[0].key }} | Default: ${{
                        fee.matrixDimensions[0].defaultPrice.toFixed(2)
                      }}
                    </div>
                  </TableCell>
                </TableRow>
                <!-- Graduated/Volume Pricing Expansion -->
                <TableRow
                  v-if="
                    expandedFees.includes(fee.id) &&
                    fee.tiers &&
                    (fee.priceModel === 'graduated' || fee.priceModel === 'volume')
                  "
                >
                  <TableCell
                    :colspan="selectedVersion?.status === 'draft' ? 4 : 3"
                    class="bg-muted/50 p-4"
                  >
                    <div class="text-sm font-medium mb-3">
                      {{ fee.featureName }} -
                      {{ fee.priceModel === 'graduated' ? 'Graduated' : 'Volume' }} Pricing
                    </div>
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead class="uppercase text-xs">From</TableHead>
                          <TableHead class="uppercase text-xs">To</TableHead>
                          <TableHead class="uppercase text-xs">Unit Price</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        <TableRow v-for="(tier, i) in fee.tiers" :key="i">
                          <TableCell>{{ tier.firstUnit.toLocaleString() }}</TableCell>
                          <TableCell>{{
                            tier.lastUnit === null ? 'Unlimited' : tier.lastUnit.toLocaleString()
                          }}</TableCell>
                          <TableCell class="tabular-nums">{{
                            tier.unitPrice === 0 ? 'Included' : `$${tier.unitPrice.toFixed(4)}`
                          }}</TableCell>
                        </TableRow>
                      </TableBody>
                    </Table>
                    <div class="mt-3 p-3 rounded-lg bg-muted text-xs">
                      <div class="font-medium mb-1">Example calculation (150 units):</div>
                      <div v-if="fee.priceModel === 'graduated'" class="text-muted-foreground">
                        {{ calculateGraduatedExample(fee.tiers, 150) }}
                      </div>
                      <div v-else class="text-muted-foreground">
                        {{ calculateVolumeExample(fee.tiers, 150) }}
                      </div>
                    </div>
                  </TableCell>
                </TableRow>
                <!-- Package Pricing Expansion -->
                <TableRow v-if="expandedFees.includes(fee.id) && fee.packageTiers">
                  <TableCell
                    :colspan="selectedVersion?.status === 'draft' ? 4 : 3"
                    class="bg-muted/50 p-4"
                  >
                    <div class="text-sm font-medium mb-3">
                      {{ fee.featureName }} - Package Pricing
                    </div>
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead class="uppercase text-xs">Up To</TableHead>
                          <TableHead class="uppercase text-xs">Unit Price</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        <TableRow v-for="(tier, i) in fee.packageTiers" :key="i">
                          <TableCell>{{
                            tier.upTo === 'unlimited' ? 'Unlimited' : tier.upTo.toLocaleString()
                          }}</TableCell>
                          <TableCell class="tabular-nums">{{
                            tier.unitPrice === 0 ? 'Included' : `$${tier.unitPrice.toFixed(3)}`
                          }}</TableCell>
                        </TableRow>
                      </TableBody>
                    </Table>
                  </TableCell>
                </TableRow>
              </template>
            </TableBody>
          </Table>
          <div v-else class="text-sm text-muted-foreground text-center py-4">
            No features configured for this version
          </div>
        </Card>

        <!-- Activity -->
        <Card class="p-6">
          <h3 class="text-sm font-medium mb-4">Activity</h3>
          <div class="space-y-3">
            <div
              v-for="(activity, i) in plan.activity.slice(0, 10)"
              :key="i"
              class="flex items-center gap-3 text-sm"
            >
              <div class="w-2 h-2 rounded-full bg-muted-foreground"></div>
              <span>{{ activity.action }}</span>
              <span class="text-muted-foreground">- {{ formatDate(activity.date) }}</span>
            </div>
          </div>
        </Card>
      </div>

      <!-- Subscriptions Tab -->
      <div v-else-if="activeTab === 'subscriptions'">
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Customer</TableHead>
                <TableHead>Start</TableHead>
                <TableHead>End</TableHead>
                <TableHead>Version</TableHead>
                <TableHead>Override</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="sub in planSubscriptions" :key="sub.id">
                <TableCell class="font-medium">{{ sub.customerName }}</TableCell>
                <TableCell>{{ formatDate(sub.startDate) }}</TableCell>
                <TableCell>{{ sub.endDate ? formatDate(sub.endDate) : '-' }}</TableCell>
                <TableCell>v{{ sub.version }}</TableCell>
                <TableCell>{{ sub.hasOverrides ? 'Yes' : '-' }}</TableCell>
                <TableCell>
                  <Badge
                    :class="
                      sub.status === 'active'
                        ? 'bg-green-100 text-green-700 border-0'
                        : 'bg-gray-100 text-gray-700 border-0'
                    "
                  >
                    {{ sub.status }}
                  </Badge>
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </Card>
      </div>

      <!-- Migrations Tab -->
      <div v-else-if="activeTab === 'migrations'" class="text-center py-12">
        <div class="inline-flex items-center gap-8 mb-6 p-6 bg-muted/50 rounded-lg">
          <div class="text-center">
            <div
              class="w-16 h-16 rounded-lg bg-muted flex items-center justify-center mb-2 mx-auto"
            >
              <span class="text-2xl font-semibold">v{{ prevVersion }}</span>
            </div>
            <div class="text-sm text-muted-foreground">
              {{ subscriptionsOnPrevVersion }} subscriptions
            </div>
          </div>
          <ArrowRight class="h-8 w-8 text-muted-foreground" />
          <div class="text-center">
            <div
              class="w-16 h-16 rounded-lg bg-primary/10 flex items-center justify-center mb-2 mx-auto"
            >
              <span class="text-2xl font-semibold text-primary">v{{ currentVersion }}</span>
            </div>
            <div class="text-sm text-muted-foreground">Current default</div>
          </div>
        </div>
        <p class="text-muted-foreground mb-4">Migrate subscriptions between plan versions</p>
        <Button @click="handleCreateVersion">Create new plan version</Button>
        <div class="mt-4">
          <a href="#" class="text-sm text-primary hover:underline">Learn more</a>
        </div>
      </div>

      <!-- Alerts Tab -->
      <div v-else-if="activeTab === 'alerts'" class="text-center py-12">
        <Bell class="h-12 w-12 text-muted-foreground mx-auto mb-4" />
        <h3 class="text-lg font-medium mb-2">No alerts configured</h3>
        <p class="text-muted-foreground mb-4">Set up alerts to monitor plan usage and billing</p>
        <Button>
          <Plus class="h-4 w-4 mr-2" />
          Add Alert
        </Button>
      </div>
    </div>

    <!-- Not Found -->
    <Card v-else class="p-8 text-center">
      <AlertCircle class="h-8 w-8 text-muted-foreground mx-auto mb-4" />
      <h3 class="text-lg font-medium mb-2">Plan Not Found</h3>
      <p class="text-muted-foreground mb-4">The plan you're looking for doesn't exist.</p>
      <Button variant="outline" @click="router.push(isDemo ? '/demo/plans' : '/plans')">
        Back to Plans
      </Button>
    </Card>

    <!-- Usage-Based Fee Modal -->
    <DemoAddUsageBasedFeeModal
      v-if="plan && selectedVersion"
      v-model:visible="showAddUsageFeeModal"
      :plan-id="plan.id"
      :version="selectedVersion.version"
      :editing-fee="editingUsageFee"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ChevronRight,
  ChevronDown,
  Plus,
  AlertCircle,
  ArrowRight,
  Bell,
  AlertTriangle,
  Pencil
} from 'lucide-vue-next'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu'
import { planSubscriptions as allSubscriptions } from '../data/mockData'
import type { UsageBasedFee, PricingTier, PlanVersionStatus } from '../types'
import DemoAddUsageBasedFeeModal from '../components/plans/DemoAddUsageBasedFeeModal.vue'
import { useDemoState } from '../composables/useDemoState'
import { toast } from '@/components/ui/toast/use-toast'

const route = useRoute()
const router = useRouter()

const { pricingPlansData, createPlanVersion, duplicatePlanVersion } = useDemoState()

const activeTab = ref<'overview' | 'subscriptions' | 'migrations' | 'alerts'>('overview')
const selectedVersionNum = ref<string>('')
const expandedFees = ref<string[]>([])

// Usage-based fee modal state
const showAddUsageFeeModal = ref(false)
const editingUsageFee = ref<UsageBasedFee | undefined>(undefined)

// Check if we're in demo mode
const isDemo = computed(() => route.path.startsWith('/demo'))

const plan = computed(() => {
  const id = route.params.id as string
  return pricingPlansData.value.find((p) => p.id === id)
})

const selectedVersion = computed(() => {
  if (!plan.value) return null
  const vNum = parseInt(selectedVersionNum.value)
  return (
    plan.value.versions.find((v) => v.version === vNum) ||
    plan.value.versions.find((v) => v.isDefault)
  )
})

const planSubscriptions = computed(() => {
  if (!plan.value) return []
  return allSubscriptions.filter((s) => plan.value?.versions.some((v) => v.version === s.version))
})

const currentVersion = computed(() => {
  const defaultV = plan.value?.versions.find((v) => v.isDefault)
  return defaultV?.version || 1
})

const prevVersion = computed(() => {
  return Math.max(1, currentVersion.value - 1)
})

const subscriptionsOnPrevVersion = computed(() => {
  return planSubscriptions.value.filter((s) => s.version === prevVersion.value).length
})

// Initialize selected version when plan loads
watch(
  plan,
  (newPlan) => {
    if (newPlan) {
      const defaultVersion = newPlan.versions.find((v) => v.isDefault)
      selectedVersionNum.value = String(defaultVersion?.version || newPlan.versions[0].version)
    }
  },
  { immediate: true }
)

function toggleFeeExpansion(feeId: string) {
  const index = expandedFees.value.indexOf(feeId)
  if (index === -1) {
    expandedFees.value.push(feeId)
  } else {
    expandedFees.value.splice(index, 1)
  }
}

function getVersionStatusClass(status: PlanVersionStatus): string {
  switch (status) {
    case 'draft':
      return 'bg-amber-100 text-amber-700 border-0'
    case 'published':
      return 'bg-green-100 text-green-700 border-0'
    case 'archived':
      return 'bg-gray-100 text-gray-500 border-0'
    default:
      return 'bg-gray-100 text-gray-700 border-0'
  }
}

function formatPriceModel(fee: UsageBasedFee): string {
  if (fee.priceModel === 'matrix') return 'Matrix'
  if (fee.priceModel === 'package') return 'Package'
  if (fee.priceModel === 'per_unit')
    return `Per Unit ($${fee.unitPrice?.toFixed(fee.featureId.includes('token') ? 4 : 3)})`
  if (fee.priceModel === 'graduated') return `Graduated (${fee.tiers?.length || 0} tiers)`
  if (fee.priceModel === 'volume') return `Volume (${fee.tiers?.length || 0} tiers)`
  return fee.priceModel
}

function calculateGraduatedExample(tiers: PricingTier[], usage: number): string {
  let total = 0
  let remaining = usage
  const parts: string[] = []

  for (const tier of tiers) {
    if (remaining <= 0) break

    const tierMax = tier.lastUnit === null ? Infinity : tier.lastUnit
    const tierMin = tier.firstUnit
    const tierRange = tierMax - tierMin + 1
    const unitsInTier = Math.min(remaining, tierRange)

    const tierCost = unitsInTier * tier.unitPrice
    total += tierCost

    if (tier.unitPrice === 0) {
      parts.push(`${unitsInTier.toLocaleString()} units (free)`)
    } else {
      parts.push(
        `${unitsInTier.toLocaleString()} × $${tier.unitPrice.toFixed(4)} = $${tierCost.toFixed(2)}`
      )
    }

    remaining -= unitsInTier
  }

  return `${parts.join(' + ')} = $${total.toFixed(2)} total`
}

function calculateVolumeExample(tiers: PricingTier[], usage: number): string {
  let applicablePrice = 0

  for (const tier of tiers) {
    const tierMax = tier.lastUnit === null ? Infinity : tier.lastUnit
    if (usage <= tierMax) {
      applicablePrice = tier.unitPrice
      break
    }
    applicablePrice = tier.unitPrice
  }

  const total = usage * applicablePrice
  return `All ${usage.toLocaleString()} units × $${applicablePrice.toFixed(4)} = $${total.toFixed(2)} total`
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

// Usage-based fee modal actions
function openAddUsageFeeModal() {
  editingUsageFee.value = undefined
  showAddUsageFeeModal.value = true
}

function openEditUsageFeeModal(fee: UsageBasedFee) {
  editingUsageFee.value = fee
  showAddUsageFeeModal.value = true
}

// Version management actions
function handleCreateVersion() {
  if (!plan.value) return
  const newVersion = createPlanVersion(plan.value.id)
  if (newVersion) {
    selectedVersionNum.value = String(newVersion.version)
    toast({
      title: 'Version created',
      description: `Version ${newVersion.version} has been created as a draft.`
    })
  }
}

function handleDuplicateVersion() {
  if (!plan.value || !selectedVersion.value) return
  const newVersion = duplicatePlanVersion(plan.value.id, selectedVersion.value.version)
  if (newVersion) {
    selectedVersionNum.value = String(newVersion.version)
    toast({
      title: 'Version duplicated',
      description: `Version ${newVersion.version} has been created from v${selectedVersion.value.version}.`
    })
  }
}
</script>
