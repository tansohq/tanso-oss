<template>
  <div class="p-6">
    <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between mb-6">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight text-foreground">Subscriptions</h1>
        <p class="text-muted-foreground mt-1">View active customer subscriptions</p>
      </div>
      <Button @click="showCreateModal = true">
        <Plus class="w-4 h-4 mr-2" />
        Create Subscription
      </Button>
    </div>

    <CreateSubscriptionModal v-model:visible="showCreateModal" />
    <StripeConnectionModal v-model:visible="showStripeModal" />

    <div class="bg-card rounded-lg border shadow-sm">
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between sm:gap-4 p-4 border-b">
        <div class="relative max-w-sm flex-1">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input v-model="searchQuery" placeholder="Search subscriptions..." class="pl-9" />
        </div>
        <Tabs v-model="statusFilter">
          <TabsList>
            <TabsTrigger value="all"> All ({{ statusCounts.all }}) </TabsTrigger>
            <TabsTrigger value="active"> Active ({{ statusCounts.active }}) </TabsTrigger>
            <TabsTrigger value="draft"> Draft ({{ statusCounts.draft }}) </TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      <TableSkeleton
        v-if="isLoading"
        :columns="[
          'Customer',
          'Plan',
          'Base Price',
          'Status',
          'Renews',
          'Actions'
        ]"
        :rows="5"
        :column-widths="['w-24', 'w-24', 'w-16', 'w-16', 'w-24', 'w-[70px]']"
      />

      <div v-else-if="isError" class="flex flex-col items-center justify-center py-12">
        <AlertCircle class="w-12 h-12 text-destructive mb-4" />
        <p class="text-lg font-medium text-foreground mb-2">Unable to load subscriptions</p>
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

      <div v-else-if="subscriptions && subscriptions.length > 0">
        <div class="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead
                class="cursor-pointer hover:bg-muted/50"
                @click="toggleSort('customer.firstName')"
              >
                Customer
                <component
                  :is="getSortIcon('customer.firstName')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'customer.firstName' }"
                />
              </TableHead>
              <TableHead class="cursor-pointer hover:bg-muted/50" @click="toggleSort('plan.name')">
                Plan
                <component
                  :is="getSortIcon('plan.name')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'plan.name' }"
                />
              </TableHead>
              <TableHead
                class="cursor-pointer hover:bg-muted/50"
                @click="toggleSort('plan.priceAmount')"
              >
                Base Price
                <component
                  :is="getSortIcon('plan.priceAmount')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'plan.priceAmount' }"
                />
              </TableHead>
              <TableHead class="cursor-pointer hover:bg-muted/50" @click="toggleSort('isActive')">
                Status
                <component
                  :is="getSortIcon('isActive')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'isActive' }"
                />
              </TableHead>
              <TableHead
                class="cursor-pointer hover:bg-muted/50"
                @click="toggleSort('currentPeriodEnd')"
              >
                Renews
                <component
                  :is="getSortIcon('currentPeriodEnd')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'currentPeriodEnd' }"
                />
              </TableHead>
              <TableHead class="w-[70px]">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="subscription in paginatedSubscriptions"
              :key="subscription.id"
              class="cursor-pointer hover:bg-muted/50"
              @click="onRowClick(subscription)"
            >
              <TableCell>
                <div class="flex flex-col">
                  <span class="font-medium">{{ subscription.customer.firstName }} {{ subscription.customer.lastName }}</span>
                  <span v-if="subscription.customer.email" class="text-xs text-muted-foreground">{{ subscription.customer.email }}</span>
                </div>
              </TableCell>
              <TableCell>{{ subscription.plan.name }}</TableCell>
              <TableCell class="tabular-nums">{{ formatPrice(subscription.plan.priceAmount) }}<span v-if="subscription.plan.intervalMonths" class="text-muted-foreground">/{{ formatIntervalShort(subscription.plan.intervalMonths) }}</span></TableCell>
              <TableCell>
                <Badge :class="getSubscriptionStatusClasses(subscription)" class="shadow-none">
                  {{ formatSubscriptionStatus(subscription) }}
                </Badge>
              </TableCell>
              <TableCell class="tabular-nums text-muted-foreground">{{ formatDate(subscription.currentPeriodEnd) }}</TableCell>
              <TableCell>
                <DropdownMenu>
                  <DropdownMenuTrigger as-child @click.stop>
                    <Button variant="ghost" size="icon" class="h-8 w-8">
                      <MoreHorizontal class="h-4 w-4" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem @click.stop="onRowClick(subscription)">
                      <Eye class="mr-2 h-4 w-4" />
                      View Details
                    </DropdownMenuItem>
                    <DropdownMenuItem
                      v-if="!subscription.isActive && !subscription.cancelledAt"
                      @click.stop="handleActivate(subscription.id)"
                    >
                      <Zap class="mr-2 h-4 w-4" />
                      Activate
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
        </div>

        <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between px-4 py-4 border-t">
          <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:gap-4">
            <div class="text-sm text-muted-foreground">
              Showing {{ (currentPage - 1) * pageSize + 1 }} to
              {{ Math.min(currentPage * pageSize, sortedSubscriptions.length) }} of
              {{ sortedSubscriptions.length }} entries
            </div>
            <div class="flex items-center gap-2">
              <span class="text-sm text-muted-foreground">Rows per page:</span>
              <Select
                :model-value="String(pageSize)"
                @update:model-value="
                  (v) => {
                    pageSize = Number(v)
                    currentPage = 1
                  }
                "
              >
                <SelectTrigger class="w-[70px] h-8">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="10">10</SelectItem>
                  <SelectItem value="25">25</SelectItem>
                  <SelectItem value="50">50</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              :disabled="currentPage === 1"
              @click="currentPage--"
            >
              Previous
            </Button>
            <span class="text-sm text-muted-foreground"
              >Page {{ currentPage }} of {{ totalPages }}</span
            >
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
      </div>

      <div v-else-if="subscriptions.length === 0" class="flex flex-col items-center justify-center py-12 text-muted-foreground">
        <Inbox class="w-12 h-12 mb-4" />
        <p class="text-lg font-medium text-foreground mb-2">No subscriptions yet</p>
        <p class="text-sm mb-4">
          Subscriptions will appear here when customers subscribe to your plans
        </p>
        <div class="flex gap-3">
          <Button @click="showCreateModal = true">
            <Plus class="w-4 h-4 mr-2" />
            Create Subscription
          </Button>
          <Button variant="outline" @click="showStripeModal = true">
            Import from Stripe
          </Button>
        </div>
      </div>

      <div v-else class="flex flex-col items-center justify-center py-12 text-muted-foreground">
        <Search class="w-12 h-12 mb-4" />
        <p class="text-lg font-medium text-foreground mb-2">No matching subscriptions</p>
        <p class="text-sm">Try adjusting your search or filter.</p>
      </div>
    </div>

  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import {
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  Search,
  Inbox,
  AlertCircle,
  RefreshCw,
  LogOut,
  MoreHorizontal,
  Eye,
  Plus,
  Zap
} from 'lucide-vue-next'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu'
import TableSkeleton from '@/shared/components/TableSkeleton.vue'
import CreateSubscriptionModal from '../components/CreateSubscriptionModal.vue'
import StripeConnectionModal from '@/features/integrations/components/StripeConnectionModal.vue'
import { useSubscriptionsQuery } from '../queries'
import { useActivateSubscriptionMutation } from '../mutations'
import { useAuthStore } from '@/stores/auth'
import { formatPrice, formatIntervalShort, formatDate, formatSubscriptionStatus, getSubscriptionStatusClasses } from '@/lib/formatters'
import type { Subscription } from '../types'
import { toast } from '@/components/ui/toast/use-toast'
import { parseApiError } from '@/lib/parseApiError'
import { useTracking } from '@/lib/tracking'
import { useDemoPrefix } from '@/lib/useDemoPrefix'

const router = useRouter()
const { demoPath, isDemo } = useDemoPrefix()
const { track } = useTracking()

// In demo mode, skip API calls and use mock data
const { data, isLoading, isError, refetch } = isDemo.value
  ? { data: ref(null), isLoading: ref(false), isError: ref(false), refetch: () => {} }
  : useSubscriptionsQuery()
const showCreateModal = ref(false)
const showStripeModal = ref(false)
const authStore = isDemo.value ? { logout: () => {} } : useAuthStore()

const { mutateAsync: activate } = isDemo.value
  ? { mutateAsync: async () => {} }
  : useActivateSubscriptionMutation()

async function handleActivate(id: string) {
  try {
    await activate(id)
    toast({ title: 'Subscription activated', description: 'Entitlements have been granted.' })
  } catch (error) {
    const parsedError = parseApiError(error)
    toast({ title: 'Error', description: parsedError.message, variant: 'destructive' })
  }
}

const demoCust = (id: string, first: string, last: string, email: string) => ({
  id, firstName: first, lastName: last, email, phoneNumber: null,
  createdAt: '2025-12-14T12:00:00Z', modifiedAt: '2026-02-14T12:00:00Z'
})
const demoPlan = (id: string, key: string, name: string, price: number, description: string) => ({
  id, key, name, description, priceAmount: price, intervalMonths: '1',
  billingTiming: 'IN_ADVANCE', createdAt: '2025-12-14T12:00:00Z', modifiedAt: '2026-02-14T12:00:00Z'
})

const DEMO_SUBSCRIPTIONS: Subscription[] = [
  { id: '11111111-aaaa-4bbb-cccc-dddddddddddd', isActive: true, customer: demoCust('a1b2c3d4-e5f6-4789-abcd-ef0123456789', 'Sarah', 'Chen', 'sarah@outbound.io'), plan: demoPlan('c2b3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e', 'growth', 'Growth', 199, 'For scaling teams that need advanced analytics and integrations'), currentPeriodStart: '2026-03-01T00:00:00Z', currentPeriodEnd: '2026-03-31T23:59:59Z', intervalMonths: '1', billingAnchorDay: 1, gracePeriodDays: 3, cancelMode: null, cancelEffectiveAt: null, cancelledAt: null, metadata: null },
  { id: '22222222-bbbb-4ccc-dddd-eeeeeeeeeeee', isActive: true, customer: demoCust('b2c3d4e5-f6a7-4890-bcde-f01234567890', 'Marcus', 'Rodriguez', 'marcus@pipelineai.com'), plan: demoPlan('d3c4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f', 'enterprise', 'Enterprise', 699, 'For large sales organizations with custom requirements'), currentPeriodStart: '2026-03-01T00:00:00Z', currentPeriodEnd: '2026-03-31T23:59:59Z', intervalMonths: '1', billingAnchorDay: 1, gracePeriodDays: 5, cancelMode: null, cancelEffectiveAt: null, cancelledAt: null, metadata: null },
  { id: '33333333-cccc-4ddd-eeee-ffffffffffff', isActive: true, customer: demoCust('c3d4e5f6-a7b8-4901-cdef-012345678901', 'Emily', 'Park', 'emily@prospectlab.io'), plan: demoPlan('b1a2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', 'starter', 'Starter', 99, 'For early-stage sales teams getting started with outbound'), currentPeriodStart: '2026-03-01T00:00:00Z', currentPeriodEnd: '2026-03-31T23:59:59Z', intervalMonths: '1', billingAnchorDay: 1, gracePeriodDays: 3, cancelMode: null, cancelEffectiveAt: null, cancelledAt: null, metadata: null },
  { id: '44444444-dddd-4eee-ffff-aaaaaaaaaaaa', isActive: true, customer: demoCust('d4e5f6a7-b8c9-4012-def0-123456789012', 'James', 'Mitchell', 'james@dealflow.co'), plan: demoPlan('c2b3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e', 'growth', 'Growth', 199, 'For scaling teams that need advanced analytics and integrations'), currentPeriodStart: '2026-03-01T00:00:00Z', currentPeriodEnd: '2026-03-31T23:59:59Z', intervalMonths: '1', billingAnchorDay: 1, gracePeriodDays: 3, cancelMode: null, cancelEffectiveAt: null, cancelledAt: null, metadata: null },
  { id: '55555555-eeee-4fff-aaaa-bbbbbbbbbbbb', isActive: true, customer: demoCust('e5f6a7b8-c9d0-4123-ef01-234567890123', 'Anika', 'Patel', 'anika@salesforge.dev'), plan: demoPlan('d3c4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f', 'enterprise', 'Enterprise', 699, 'For large sales organizations with custom requirements'), currentPeriodStart: '2026-03-01T00:00:00Z', currentPeriodEnd: '2026-03-31T23:59:59Z', intervalMonths: '1', billingAnchorDay: 1, gracePeriodDays: 5, cancelMode: null, cancelEffectiveAt: null, cancelledAt: null, metadata: null },
]

const subscriptions = computed(() => {
  if (isDemo.value) return DEMO_SUBSCRIPTIONS
  if (!data.value) return []
  // Handle both direct array and paginated response formats
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

const currentPage = ref(1)
const pageSize = ref(10)
const sortField = ref<string | null>(null)
const sortOrder = ref<'asc' | 'desc'>('asc')
const searchQuery = ref('')
const statusFilter = ref('all')

watch([searchQuery, statusFilter], () => {
  currentPage.value = 1
})

const statusCounts = computed(() => {
  const all = subscriptions.value.length
  const active = subscriptions.value.filter((s) => s.isActive).length
  const draft = subscriptions.value.filter((s) => !s.isActive && !s.cancelledAt).length
  return { all, active, draft }
})

function toggleSort(field: string) {
  if (sortField.value === field) {
    sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortField.value = field
    sortOrder.value = 'asc'
  }
}

function getSortIcon(field: string) {
  if (sortField.value !== field) return ArrowUpDown
  return sortOrder.value === 'asc' ? ArrowUp : ArrowDown
}

const filteredSubscriptions = computed(() => {
  let result = subscriptions.value

  if (statusFilter.value === 'active') {
    result = result.filter((sub) => sub.isActive)
  } else if (statusFilter.value === 'draft') {
    result = result.filter((sub) => !sub.isActive && !sub.cancelledAt)
  }

  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(
      (sub) =>
        sub.customer?.firstName?.toLowerCase().includes(query) ||
        sub.customer?.lastName?.toLowerCase().includes(query) ||
        (sub.customer?.firstName && sub.customer?.lastName && `${sub.customer.firstName} ${sub.customer.lastName}`.toLowerCase().includes(query)) ||
        sub.customer?.email?.toLowerCase().includes(query) ||
        sub.plan?.name?.toLowerCase().includes(query)
    )
  }

  return result
})

const sortedSubscriptions = computed(() => {
  if (!sortField.value) return filteredSubscriptions.value
  return [...filteredSubscriptions.value].sort((a, b) => {
    let aVal: unknown = a
    let bVal: unknown = b
    const fields = sortField.value!.split('.')
    for (const f of fields) {
      aVal = (aVal as Record<string, unknown>)?.[f]
      bVal = (bVal as Record<string, unknown>)?.[f]
    }
    if (aVal === null || aVal === undefined) return 1
    if (bVal === null || bVal === undefined) return -1
    if (aVal < bVal) return sortOrder.value === 'asc' ? -1 : 1
    if (aVal > bVal) return sortOrder.value === 'asc' ? 1 : -1
    return 0
  })
})

const totalPages = computed(() => Math.ceil(sortedSubscriptions.value.length / pageSize.value))

const paginatedSubscriptions = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return sortedSubscriptions.value.slice(start, end)
})

function onRowClick(subscription: Subscription) {
  track('subscription_detail_opened', { subscriptionId: subscription.id })
  router.push(demoPath(`/subscriptions/${subscription.id}`))
}
</script>
