<template>
  <div class="p-6 pb-16">
    <!-- Header -->
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-semibold text-foreground">Plans</h1>
        <p class="text-muted-foreground mt-1">Manage pricing plans and versions</p>
      </div>
      <Button @click="showCreateDialog = true">
        <Plus class="h-4 w-4 mr-2" />
        Create Plan
      </Button>
    </div>

    <!-- Create Plan Dialog -->
    <Dialog v-model:open="showCreateDialog" @update:open="handleDialogChange">
      <DialogContent class="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>Create New Plan</DialogTitle>
          <DialogDescription> Enter a name for your new pricing plan </DialogDescription>
        </DialogHeader>
        <div class="flex flex-col gap-4 py-4">
          <div class="flex flex-col gap-2">
            <Label for="planName">Plan Name *</Label>
            <Input
              ref="planNameInputRef"
              id="planName"
              v-model="newPlanName"
              placeholder="e.g., Professional"
            />
          </div>
          <div class="flex flex-col gap-1.5">
            <Label>Billing Cycle *</Label>
            <div class="flex gap-2">
              <Button
                type="button"
                :variant="newPlanBillingCycle === 'monthly' ? 'default' : 'outline'"
                class="flex-1"
                size="sm"
                @click="newPlanBillingCycle = 'monthly'"
              >
                Monthly
              </Button>
              <Button
                type="button"
                :variant="newPlanBillingCycle === 'quarterly' ? 'default' : 'outline'"
                class="flex-1"
                size="sm"
                @click="newPlanBillingCycle = 'quarterly'"
              >
                Quarterly
              </Button>
              <Button
                type="button"
                :variant="newPlanBillingCycle === 'annual' ? 'default' : 'outline'"
                class="flex-1"
                size="sm"
                @click="newPlanBillingCycle = 'annual'"
              >
                Annual
              </Button>
            </div>
          </div>

          <!-- Advanced Options Toggle -->
          <button
            type="button"
            class="flex items-center justify-between w-full py-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
            @click="showAdvancedOptions = !showAdvancedOptions"
          >
            <span>Advanced options</span>
            <ChevronDown
              class="h-4 w-4 transition-transform"
              :class="showAdvancedOptions && 'rotate-180'"
            />
          </button>

          <!-- Advanced Options Content -->
          <div v-if="showAdvancedOptions" class="space-y-4 pt-1">
            <div class="flex flex-col gap-1.5">
              <Label for="planDescription">Description</Label>
              <Textarea
                id="planDescription"
                v-model="newPlanDescription"
                placeholder="Brief description of this plan..."
                rows="2"
                class="resize-none"
              />
            </div>
            <div class="grid grid-cols-2 gap-4">
              <div class="flex flex-col gap-1.5">
                <Label for="planExternalId">External ID</Label>
                <Input
                  id="planExternalId"
                  v-model="newPlanExternalId"
                  :placeholder="generatedExternalId || 'plan_pro'"
                  class="font-mono text-sm"
                />
                <p class="text-xs text-muted-foreground">
                  <span v-if="!newPlanExternalId && generatedExternalId" class="font-mono">
                    {{ generatedExternalId }}
                  </span>
                  <span v-else>Auto-generated from name</span>
                </p>
              </div>
              <div class="flex flex-col gap-1.5">
                <Label>Currency</Label>
                <Select v-model="newPlanCurrency">
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="USD">USD ($)</SelectItem>
                    <SelectItem value="EUR">EUR (&euro;)</SelectItem>
                    <SelectItem value="GBP">GBP (&pound;)</SelectItem>
                    <SelectItem value="CAD">CAD ($)</SelectItem>
                    <SelectItem value="AUD">AUD ($)</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="handleCancelCreate">Cancel</Button>
          <Button @click="handleCreatePlan" :disabled="!newPlanName.trim()"> Create Plan </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Revenue Chart -->
    <Card class="p-6 mb-6">
      <div class="flex items-center justify-between mb-4">
        <div>
          <div class="text-sm font-medium text-muted-foreground">Plan Revenue</div>
          <div class="text-4xl font-semibold tabular-nums">${{ totalRevenue.toLocaleString('en-US', { minimumFractionDigits: 2 }) }}</div>
          <div class="text-sm text-muted-foreground mt-1">{{ periodLabel }}</div>
        </div>
        <div class="flex gap-1">
          <Button
            v-for="p in periodOptions"
            :key="p"
            :variant="selectedPeriod === p ? 'default' : 'outline'"
            size="sm"
            class="h-7 px-2.5 text-xs"
            @click="selectedPeriod = p"
          >
            {{ periodButtonLabels[p] }}
          </Button>
        </div>
      </div>
      <div class="h-32 flex items-end gap-1">
        <div
          v-for="(_, i) in visibleLabels"
          :key="i"
          class="flex-1 flex flex-col gap-px"
        >
          <div
            class="bg-gray-700 rounded-t"
            :style="{ height: `${(visibleData.enterprise[i] / maxRevenue) * 120}px` }"
          />
          <div
            class="bg-gray-500"
            :style="{ height: `${(visibleData.growth[i] / maxRevenue) * 120}px` }"
          />
          <div
            class="bg-gray-300 rounded-b"
            :style="{ height: `${(visibleData.starter[i] / maxRevenue) * 120}px` }"
          />
        </div>
      </div>
      <div class="flex justify-between text-[10px] text-muted-foreground mt-1.5">
        <span v-for="label in visibleLabels" :key="label">{{ label }}</span>
      </div>
      <div class="flex items-center gap-4 mt-4 text-sm">
        <span class="flex items-center gap-2">
          <span class="w-3 h-3 rounded bg-gray-300"></span>
          Starter
        </span>
        <span class="flex items-center gap-2">
          <span class="w-3 h-3 rounded bg-gray-500"></span>
          Growth
        </span>
        <span class="flex items-center gap-2">
          <span class="w-3 h-3 rounded bg-gray-700"></span>
          Enterprise
        </span>
      </div>
    </Card>

    <!-- Status Filter Tabs -->
    <Tabs v-model="activeStatusTab" class="mb-4">
      <TabsList>
        <TabsTrigger value="active">Active ({{ statusCounts.active }})</TabsTrigger>
        <TabsTrigger value="draft">Draft ({{ statusCounts.draft }})</TabsTrigger>
        <TabsTrigger value="archived">Archived ({{ statusCounts.archived }})</TabsTrigger>
      </TabsList>
    </Tabs>

    <!-- Search -->
    <div class="relative max-w-sm mb-6">
      <Search class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
      <Input v-model="searchQuery" placeholder="Search plans..." class="pl-9" />
    </div>

    <!-- Plans Grid -->
    <div
      v-if="filteredPlans.length > 0"
      class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"
    >
      <DemoPlanCard
        v-for="plan in filteredPlans"
        :key="plan.id"
        :plan="plan"
        @click="goToPlanDetail(plan.id)"
      />
    </div>

    <!-- Empty State -->
    <div v-else class="flex flex-col items-center justify-center py-12 text-muted-foreground">
      <Inbox class="w-12 h-12 mb-4" />
      <p class="text-lg font-medium text-foreground mb-2">
        {{ activeStatusTab === 'active' ? 'No active plans' : activeStatusTab === 'draft' ? 'No draft plans' : 'No archived plans' }}
      </p>
      <p class="text-sm mb-4">
        {{ activeStatusTab === 'archived' ? 'Plans you archive will appear here' : 'Create a new plan to get started' }}
      </p>
      <div v-if="activeStatusTab !== 'archived'" class="flex gap-2">
        <Button @click="showCreateDialog = true">
          <Plus class="h-4 w-4 mr-2" />
          Create Plan
        </Button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Plus, Search, Inbox, ChevronDown } from 'lucide-vue-next'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import { planRevenueByMonth } from '../data/mockData'
import { useDemoState } from '../composables/useDemoState'
import DemoPlanCard from '../components/plans/DemoPlanCard.vue'

const router = useRouter()
const route = useRoute()

const { pricingPlansData, createPlan } = useDemoState()

const activeStatusTab = ref<'active' | 'draft' | 'archived'>('active')
const searchQuery = ref('')

// Create plan dialog state
const showCreateDialog = ref(false)
const showAdvancedOptions = ref(false)
const newPlanName = ref('')
const newPlanDescription = ref('')
const newPlanExternalId = ref('')
const newPlanCurrency = ref('USD')
const newPlanBillingCycle = ref<'monthly' | 'quarterly' | 'annual'>('monthly')
const planNameInputRef = ref<InstanceType<typeof Input> | null>(null)

// Auto-generated external ID preview
const generatedExternalId = computed(() => {
  if (!newPlanName.value.trim()) return ''
  return newPlanName.value.toLowerCase().replace(/\s+/g, '_')
})

// Status counts for tab labels
const statusCounts = computed(() => {
  const all = pricingPlansData.value
  return {
    active: all.filter((p) => p.status === 'active').length,
    draft: all.filter((p) => p.status === 'draft').length,
    archived: all.filter((p) => p.status === 'archived').length
  }
})

// Handle dialog open/close for auto-focus
function handleDialogChange(open: boolean) {
  if (open) {
    nextTick(() => {
      planNameInputRef.value?.$el?.focus()
    })
  } else {
    resetCreateForm()
  }
}

// Reset form state
function resetCreateForm() {
  newPlanName.value = ''
  newPlanDescription.value = ''
  newPlanExternalId.value = ''
  newPlanCurrency.value = 'USD'
  newPlanBillingCycle.value = 'monthly'
  showAdvancedOptions.value = false
}

// Handle cancel button
function handleCancelCreate() {
  showCreateDialog.value = false
  resetCreateForm()
}

// Check if we're in demo mode
const isDemo = computed(() => route.path.startsWith('/demo'))

const filteredPlans = computed(() => {
  let plans = pricingPlansData.value.filter((p) => p.status === activeStatusTab.value)
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    plans = plans.filter(
      (p) =>
        p.name.toLowerCase().includes(query) ||
        p.externalId?.toLowerCase().includes(query) ||
        p.description?.toLowerCase().includes(query)
    )
  }
  return plans
})

function handleCreatePlan() {
  if (!newPlanName.value.trim()) return

  const externalId = newPlanExternalId.value.trim() || generatedExternalId.value

  const newPlan = createPlan({
    name: newPlanName.value.trim(),
    description: newPlanDescription.value.trim() || undefined,
    externalId,
    status: 'active',
    currency: newPlanCurrency.value,
    billingCycle: newPlanBillingCycle.value,
    netTerms: 'Net 30',
    billingMode: 'arrears'
  })

  // Reset form and close dialog
  showCreateDialog.value = false
  resetCreateForm()

  // Navigate to the new plan
  goToPlanDetail(newPlan.id)
}

type RevenuePeriod = '3m' | '6m' | '12m'
const periodOptions: RevenuePeriod[] = ['3m', '6m', '12m']
const periodButtonLabels: Record<RevenuePeriod, string> = { '3m': '3M', '6m': '6M', '12m': '12M' }
const selectedPeriod = ref<RevenuePeriod>('3m')

const monthCount = computed(() => {
  switch (selectedPeriod.value) {
    case '3m': return 3
    case '6m': return 6
    case '12m': return 12
  }
})

const periodLabel = computed(() => `Last ${monthCount.value} months`)

const visibleLabels = computed(() =>
  planRevenueByMonth.labels.slice(-monthCount.value)
)

const visibleData = computed(() => ({
  starter: planRevenueByMonth.data.starter.slice(-monthCount.value),
  growth: planRevenueByMonth.data.growth.slice(-monthCount.value),
  enterprise: planRevenueByMonth.data.enterprise.slice(-monthCount.value)
}))

const totalRevenue = computed(() => {
  const d = visibleData.value
  let total = 0
  for (let i = 0; i < d.starter.length; i++) {
    total += d.starter[i] + d.growth[i] + d.enterprise[i]
  }
  return total / 100
})

const maxRevenue = computed(() => {
  const d = visibleData.value
  const totals = d.starter.map(
    (_, i) => d.starter[i] + d.growth[i] + d.enterprise[i]
  )
  return Math.max(...totals)
})

function goToPlanDetail(id: string) {
  if (isDemo.value) {
    router.push(`/demo/plans/${id}`)
  } else {
    router.push(`/plans/${id}`)
  }
}
</script>
