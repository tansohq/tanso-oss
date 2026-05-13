<template>
  <div class="p-6">
    <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between mb-6">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight text-foreground">Plans</h1>
        <p class="text-muted-foreground mt-1">Manage pricing plans and versions</p>
      </div>
      <div class="flex items-center gap-2">
        <Button @click="showCreateModal = true">
          <Plus class="w-4 h-4 mr-2" />
          Create Plan
        </Button>
      </div>
    </div>

    <PlanRevenueCard v-if="!isError" :is-loading="isLoading || isLoadingInvoices" :invoices="invoices as any" />

    <!-- Status Filter Tabs + Search -->
    <Tabs v-model="activeStatusTab" class="mb-6">
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
        <div class="relative max-w-full sm:max-w-sm flex-1">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input v-model="searchQuery" placeholder="Search plans..." class="pl-9" />
        </div>
        <TabsList>
          <TabsTrigger value="active">Active ({{ statusCounts.active }})</TabsTrigger>
          <TabsTrigger value="draft">Draft ({{ statusCounts.draft }})</TabsTrigger>
          <TabsTrigger value="archived">Archived ({{ statusCounts.archived }})</TabsTrigger>
        </TabsList>
      </div>
    </Tabs>

    <!-- Loading State -->
    <CardGridSkeleton v-if="isLoading" :count="6" :columns="3" />

    <!-- Error State -->
    <div
      v-else-if="isError"
      class="flex flex-col items-center justify-center py-12 bg-card rounded-lg border"
    >
      <AlertCircle class="w-12 h-12 text-destructive mb-4" />
      <p class="text-lg font-medium text-foreground mb-2">Unable to load plans</p>
      <p class="text-sm text-muted-foreground mb-4">
        This is usually fixed by logging out and back in.
      </p>
      <div class="flex gap-2">
        <Button variant="outline" @click="refetch">
          <RefreshCw class="w-4 h-4 mr-2" />
          Try Again
        </Button>
        <Button variant="outline" @click="authStore.logout()">
          <LogOut class="w-4 h-4 mr-2" />
          Log out
        </Button>
      </div>
    </div>

    <!-- Plans Grid -->
    <div
      v-else-if="filteredPlans.length > 0"
      class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"
    >
      <PlanCard
        v-for="plan in filteredPlans"
        :key="plan.id"
        :plan="plan"
        @click="onCardClick(plan)"
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
        <Button @click="showCreateModal = true">
          <Plus class="w-4 h-4 mr-2" />
          Create Plan
        </Button>
        <Button variant="outline" @click="showStripeModal = true">
          Import from Stripe
        </Button>
      </div>
    </div>

    <CreatePlanModal v-model:visible="showCreateModal" @create:success="handlePlanCreated" />
    <StripeConnectionModal v-model:visible="showStripeModal" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Plus, AlertCircle, Search, Inbox, RefreshCw, LogOut } from 'lucide-vue-next'
import CardGridSkeleton from '@/shared/components/CardGridSkeleton.vue'
import { useQueryClient } from '@tanstack/vue-query'
import { usePlansQuery } from '../queries'
import { useInvoicesQuery } from '@/features/invoices/queries'
import { useAuthStore } from '@/stores/auth'
import PlanCard from '../components/PlanCard.vue'
import PlanRevenueCard from '../components/PlanRevenueCard.vue'
import CreatePlanModal from '../components/CreatePlanModal.vue'
import StripeConnectionModal from '@/features/integrations/components/StripeConnectionModal.vue'
import type { Plan } from '../types'
import { useTracking } from '@/lib/tracking'
import { useDemoPrefix } from '@/lib/useDemoPrefix'

const router = useRouter()
const queryClient = useQueryClient()
const { isDemo, demoPath } = useDemoPrefix()
const { track } = useTracking()

// In demo mode, skip API calls and use mock data
const { data, isLoading, isError, refetch } = isDemo.value
  ? { data: ref(null), isLoading: ref(false), isError: ref(false), refetch: () => {} }
  : usePlansQuery()
const { data: invoicesData, isLoading: isLoadingInvoices } = isDemo.value
  ? { data: ref(null), isLoading: ref(false) }
  : useInvoicesQuery()
const authStore = isDemo.value ? { logout: () => {} } : useAuthStore()

// IDs must match useDemoDataSeeder so plan-features cache is found by PlanCard
const DEMO_PLANS: Plan[] = [
  { id: 'b1a2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', key: 'starter', name: 'Starter', description: 'For early-stage sales teams getting started with outbound', priceAmount: 99, intervalMonths: '1', billingTiming: 'IN_ADVANCE', status: 'active', createdAt: '2025-12-14T12:00:00Z', modifiedAt: '2026-02-14T12:00:00Z' },
  { id: 'c2b3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e', key: 'growth', name: 'Growth', description: 'For scaling teams that need advanced analytics and integrations', priceAmount: 199, intervalMonths: '1', billingTiming: 'IN_ADVANCE', status: 'active', createdAt: '2025-12-14T12:00:00Z', modifiedAt: '2026-02-14T12:00:00Z' },
  { id: 'd3c4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f', key: 'enterprise', name: 'Enterprise', description: 'For large sales organizations with custom requirements', priceAmount: 699, intervalMonths: '1', billingTiming: 'IN_ADVANCE', status: 'active', createdAt: '2025-12-14T12:00:00Z', modifiedAt: '2026-02-14T12:00:00Z' },
]

const invoices = computed(() => {
  if (isDemo.value) {
    const cached = queryClient.getQueryData<{ data?: unknown[] }>(['invoices'])
    return cached?.data ?? []
  }
  return invoicesData.value?.data ?? []
})

const plans = computed(() => {
  if (isDemo.value) return DEMO_PLANS
  if (!data.value) return []
  if (data.value.data) {
    // Paginated format: { data: { items: [...] } }
    if ('items' in data.value.data && Array.isArray(data.value.data.items)) {
      return data.value.data.items
    }
    // Direct array format: { data: [...] }
    if (Array.isArray(data.value.data)) {
      return data.value.data
    }
  }
  return []
})

const statusCounts = computed(() => {
  const all = plans.value
  return {
    active: all.filter((p) => (p.status ?? 'draft').toLowerCase() === 'active').length,
    draft: all.filter((p) => (p.status ?? 'draft').toLowerCase() === 'draft').length,
    archived: all.filter((p) => (p.status ?? 'draft').toLowerCase() === 'archived').length
  }
})

const showCreateModal = ref(false)
const showStripeModal = ref(false)
const searchQuery = ref('')
const activeStatusTab = ref<'active' | 'draft' | 'archived'>('active')

const filteredPlans = computed(() => {
  let result = plans.value.filter((plan) => {
    const status = (plan.status ?? 'draft').toLowerCase()
    return status === activeStatusTab.value
  })

  // Filter by search query
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(
      (plan) =>
        plan.key?.toLowerCase().includes(query) ||
        plan.name?.toLowerCase().includes(query) ||
        plan.description?.toLowerCase().includes(query)
    )
  }

  return result
})

function onCardClick(plan: Plan) {
  track('plan_detail_opened', { planId: plan.id })
  router.push(demoPath(`/plans/${plan.id}`))
}

function handlePlanCreated(plan: { id: string; name: string }) {
  track('plan_created', { planId: plan.id, planName: plan.name })
  router.push(demoPath(`/plans/${plan.id}`))
}
</script>
