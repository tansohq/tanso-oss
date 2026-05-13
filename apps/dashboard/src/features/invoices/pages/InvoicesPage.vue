<template>
  <div class="p-6">
    <!-- STRIPE_INTEGRATION banner -->
    <div
      v-if="isStripeIntegration"
      class="rounded-md border border-blue-200 bg-blue-50 p-4 mb-6 text-sm text-blue-800 dark:border-blue-800 dark:bg-blue-950 dark:text-blue-200"
    >
      <p class="font-medium">Invoices are managed in Stripe</p>
      <p class="text-xs mt-1">
        Full invoice lifecycle management is available when Tanso handles billing.
        Invoices from webhook events will still appear below.
      </p>
    </div>

    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight text-foreground">Invoices</h1>
        <p class="text-muted-foreground mt-1">Manage billing and payment records</p>
      </div>
    </div>

    <div class="bg-card rounded-lg border shadow-sm">
      <div class="flex items-center justify-between gap-4 p-4 border-b">
        <div class="relative max-w-sm flex-1">
          <Search
            class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground"
          />
          <Input v-model="searchQuery" placeholder="Search invoices..." class="pl-9" />
        </div>
        <Select v-model="statusFilter">
          <SelectTrigger class="w-[200px]">
            <SelectValue placeholder="All Statuses" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Statuses</SelectItem>
            <SelectItem value="PAST_DUE">Past Due</SelectItem>
            <SelectItem value="DUE">Due</SelectItem>
            <SelectItem value="PENDING">Pending</SelectItem>
            <SelectItem value="PAID">Paid</SelectItem>
            <SelectItem value="CANCELLED">Cancelled</SelectItem>
            <SelectItem value="VOID">Void</SelectItem>
            <SelectItem value="ADJUSTMENT">Adjustment</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <TableSkeleton
        v-if="isLoading"
        :columns="[
          'Customer',
          'Plan',
          'Amount',
          'Status',
          'Due Date',
          'Actions'
        ]"
        :rows="5"
        :column-widths="['w-24', 'w-24', 'w-16', 'w-16', 'w-24', 'w-[70px]']"
      />

      <div v-else-if="isError" class="flex flex-col items-center justify-center py-12">
        <AlertCircle class="w-12 h-12 text-destructive mb-4" />
        <p class="text-lg font-medium text-foreground mb-2">Unable to load invoices</p>
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

      <div v-else-if="invoices && invoices.length > 0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead
                class="cursor-pointer hover:bg-muted/50"
                @click="toggleSort('subscription.customer.firstName')"
              >
                Customer
                <component
                  :is="getSortIcon('subscription.customer.firstName')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'subscription.customer.firstName' }"
                />
              </TableHead>
              <TableHead
                class="cursor-pointer hover:bg-muted/50"
                @click="toggleSort('subscription.plan.name')"
              >
                Plan
                <component
                  :is="getSortIcon('subscription.plan.name')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'subscription.plan.name' }"
                />
              </TableHead>
              <TableHead class="cursor-pointer hover:bg-muted/50" @click="toggleSort('amount')">
                Amount
                <component
                  :is="getSortIcon('amount')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'amount' }"
                />
              </TableHead>
              <TableHead class="cursor-pointer hover:bg-muted/50" @click="toggleSort('status')">
                Status
                <component
                  :is="getSortIcon('status')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'status' }"
                />
              </TableHead>
              <TableHead class="cursor-pointer hover:bg-muted/50" @click="toggleSort('dueDate')">
                Due Date
                <component
                  :is="getSortIcon('dueDate')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'dueDate' }"
                />
              </TableHead>
              <TableHead class="w-[70px]">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="invoice in paginatedInvoices"
              :key="invoice.id"
              class="cursor-pointer hover:bg-muted/50"
              @click="onRowClick(invoice)"
            >
              <TableCell>
                <div class="flex flex-col">
                  <span class="font-medium">{{ invoice.subscription?.customer?.firstName || '\u2014' }} {{ invoice.subscription?.customer?.lastName || '' }}</span>
                  <span v-if="invoice.subscription?.customer?.email" class="text-xs text-muted-foreground">{{ invoice.subscription.customer.email }}</span>
                </div>
              </TableCell>
              <TableCell>{{ invoice.subscription?.plan?.name || '\u2014' }}</TableCell>
              <TableCell class="tabular-nums">{{ formatAmount(invoice.amount) }}</TableCell>
              <TableCell>
                <Badge :class="getInvoiceStatusClasses(invoice.status)" class="shadow-none">
                  {{ formatInvoiceStatus(invoice.status) }}
                </Badge>
              </TableCell>
              <TableCell class="tabular-nums text-muted-foreground">{{ formatDate(invoice.dueDate) }}</TableCell>
              <TableCell>
                <DropdownMenu>
                  <DropdownMenuTrigger as-child @click.stop>
                    <Button variant="ghost" size="icon" class="h-8 w-8">
                      <MoreHorizontal class="h-4 w-4" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem @click.stop="onRowClick(invoice)">
                      <Eye class="mr-2 h-4 w-4" />
                      View Details
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>

        <div class="flex items-center justify-between px-4 py-4 border-t">
          <div class="flex items-center gap-4">
            <div class="text-sm text-muted-foreground">
              Showing {{ (currentPage - 1) * pageSize + 1 }} to
              {{ Math.min(currentPage * pageSize, sortedInvoices.length) }} of
              {{ sortedInvoices.length }} entries
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

      <div v-else class="flex flex-col items-center justify-center py-12 text-muted-foreground">
        <Inbox class="w-12 h-12 mb-4" />
        <p class="text-lg font-medium text-foreground mb-2">No invoices yet</p>
        <p class="text-sm mb-4">Invoices will appear here when billing cycles generate them</p>
        <Button variant="outline" @click="$router.push('/subscriptions')">
          View Subscriptions
        </Button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
} from 'lucide-vue-next'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu'
import TableSkeleton from '@/shared/components/TableSkeleton.vue'
import { useAccountSettingsQuery } from '@/features/integrations/queries'
import { useInvoicesQuery } from '../queries'
import { useAuthStore } from '@/stores/auth'
import { formatAmount, formatDate, formatInvoiceStatus, getInvoiceStatusClasses } from '@/lib/formatters'
import type { Invoice } from '../types'
import { useTracking } from '@/lib/tracking'
import { useDemoPrefix } from '@/lib/useDemoPrefix'

const route = useRoute()
const router = useRouter()
const { demoPath, isDemo } = useDemoPrefix()
const { track } = useTracking()

// In demo mode, skip API calls and use mock data
const { data, isLoading, isError, refetch } = isDemo.value
  ? { data: ref(null), isLoading: ref(false), isError: ref(false), refetch: () => {} }
  : useInvoicesQuery()
const { data: settingsData } = isDemo.value
  ? { data: ref(null) }
  : useAccountSettingsQuery()
const isStripeIntegration = computed(() => isDemo.value ? false : settingsData.value?.data?.stripeMode === 'STRIPE_INTEGRATION')
const authStore = isDemo.value ? { logout: () => {} } : useAuthStore()

const demoSub = (id: string, isActive: boolean, cust: { id: string; firstName: string; lastName: string; email: string }, plan: { id: string; name: string; priceAmount: number }) => ({
  id, isActive, intervalMonths: '1', billingAnchorDay: 1, gracePeriodDays: 3,
  currentPeriodStart: '2026-03-01T00:00:00Z', currentPeriodEnd: '2026-03-31T23:59:59Z',
  cancelMode: null, cancelEffectiveAt: null, cancelledAt: null, metadata: null,
  customer: cust,
  plan: { ...plan, key: plan.id, description: null, intervalMonths: '1', billingTiming: 'IN_ADVANCE', createdAt: '2025-12-14T12:00:00Z', modifiedAt: '2026-02-14T12:00:00Z' }
})

const DEMO_INVOICES = [
  { id: '66666666-0000-4aaa-bbbb-cccccccccccc', amount: 199, dueDate: '2026-03-15T12:00:00Z', currency: 'USD', status: 'PAID', createdAt: '2026-03-05T12:00:00Z', modifiedAt: '2026-03-10T12:00:00Z', metadata: null, subscription: demoSub('11111111-aaaa-4bbb-cccc-dddddddddddd', true, { id: 'a1b2c3d4-e5f6-4789-abcd-ef0123456789', firstName: 'Sarah', lastName: 'Chen', email: 'sarah@outbound.io' }, { id: 'c2b3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e', name: 'Growth', priceAmount: 199 }) },
  { id: '66666666-0001-4aaa-bbbb-cccccccccccc', amount: 699, dueDate: '2026-03-15T12:00:00Z', currency: 'USD', status: 'PAID', createdAt: '2026-03-05T12:00:00Z', modifiedAt: '2026-03-10T12:00:00Z', metadata: null, subscription: demoSub('22222222-bbbb-4ccc-dddd-eeeeeeeeeeee', true, { id: 'b2c3d4e5-f6a7-4890-bcde-f01234567890', firstName: 'Marcus', lastName: 'Rodriguez', email: 'marcus@pipelineai.com' }, { id: 'd3c4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f', name: 'Enterprise', priceAmount: 699 }) },
  { id: '66666666-0002-4aaa-bbbb-cccccccccccc', amount: 99, dueDate: '2026-03-15T12:00:00Z', currency: 'USD', status: 'PENDING', createdAt: '2026-03-05T12:00:00Z', modifiedAt: '2026-03-05T12:00:00Z', metadata: null, subscription: demoSub('33333333-cccc-4ddd-eeee-ffffffffffff', true, { id: 'c3d4e5f6-a7b8-4901-cdef-012345678901', firstName: 'Emily', lastName: 'Park', email: 'emily@prospectlab.io' }, { id: 'b1a2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', name: 'Starter', priceAmount: 99 }) },
  { id: '66666666-0003-4aaa-bbbb-cccccccccccc', amount: 199, dueDate: '2026-03-15T12:00:00Z', currency: 'USD', status: 'PENDING', createdAt: '2026-03-05T12:00:00Z', modifiedAt: '2026-03-05T12:00:00Z', metadata: null, subscription: demoSub('44444444-dddd-4eee-ffff-aaaaaaaaaaaa', true, { id: 'd4e5f6a7-b8c9-4012-def0-123456789012', firstName: 'James', lastName: 'Mitchell', email: 'james@dealflow.co' }, { id: 'c2b3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e', name: 'Growth', priceAmount: 199 }) },
  { id: '66666666-0004-4aaa-bbbb-cccccccccccc', amount: 699, dueDate: '2026-03-15T12:00:00Z', currency: 'USD', status: 'PAID', createdAt: '2026-03-05T12:00:00Z', modifiedAt: '2026-03-10T12:00:00Z', metadata: null, subscription: demoSub('55555555-eeee-4fff-aaaa-bbbbbbbbbbbb', true, { id: 'e5f6a7b8-c9d0-4123-ef01-234567890123', firstName: 'Anika', lastName: 'Patel', email: 'anika@salesforge.dev' }, { id: 'd3c4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f', name: 'Enterprise', priceAmount: 699 }) },
] as Invoice[]

const invoices = computed(() => {
  if (isDemo.value) return DEMO_INVOICES
  if (!data.value) return []
  if (Array.isArray(data.value)) return data.value
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
const searchQuery = ref((route.query.search as string) || '')
const statusFilter = ref<string>('all')

watch([searchQuery, statusFilter], () => {
  currentPage.value = 1
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

const statusGroupMap: Record<string, string[]> = {
  CANCELLED: ['CANCELLED', 'CANCELLED_PROCESSED'],
  ADJUSTMENT: ['ADJUSTMENT_OPEN', 'ADJUSTMENT_PAID']
}

const statusFilteredInvoices = computed(() => {
  if (statusFilter.value === 'all') return invoices.value
  const statuses = statusGroupMap[statusFilter.value] ?? [statusFilter.value]
  return invoices.value.filter((invoice) => statuses.includes(invoice.status))
})

const filteredInvoices = computed(() => {
  if (!searchQuery.value.trim()) return statusFilteredInvoices.value
  const query = searchQuery.value.toLowerCase()
  return statusFilteredInvoices.value.filter(
    (invoice) => {
      const cust = invoice.subscription?.customer
      return (
        cust?.firstName?.toLowerCase().includes(query) ||
        cust?.lastName?.toLowerCase().includes(query) ||
        (cust?.firstName && cust?.lastName && `${cust.firstName} ${cust.lastName}`.toLowerCase().includes(query)) ||
        cust?.email?.toLowerCase().includes(query) ||
        invoice.subscription?.plan?.name?.toLowerCase().includes(query) ||
        invoice.status?.toLowerCase().includes(query)
      )
    }
  )
})

const sortedInvoices = computed(() => {
  if (!sortField.value) return filteredInvoices.value
  return [...filteredInvoices.value].sort((a, b) => {
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

const totalPages = computed(() => Math.ceil(sortedInvoices.value.length / pageSize.value))

const paginatedInvoices = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return sortedInvoices.value.slice(start, end)
})

function onRowClick(invoice: Invoice) {
  track('invoice_detail_opened', { invoiceId: invoice.id })
  router.push(demoPath(`/invoices/${invoice.id}`))
}

</script>
