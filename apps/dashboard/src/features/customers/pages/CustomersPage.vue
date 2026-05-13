<template>
  <div class="p-6">
    <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between mb-6">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight text-foreground">Customers</h1>
        <p class="text-muted-foreground mt-1">Manage your customer accounts</p>
      </div>
      <Button @click="showCreateModal = true">
        <Plus class="w-4 h-4 mr-2" />
        Create Customer
      </Button>
    </div>

    <div class="bg-card rounded-lg border shadow-sm">
      <div class="p-4 border-b">
        <div class="relative max-w-sm">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input v-model="searchQuery" placeholder="Search customers..." class="pl-9" />
        </div>
      </div>

      <div v-if="isLoading" class="overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Customer</TableHead>
            <TableHead>Reference ID</TableHead>
            <TableHead>Created</TableHead>
            <TableHead class="w-[70px]">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="i in 5" :key="i">
            <TableCell><Skeleton class="h-4 w-32" /><Skeleton class="h-3 w-40 mt-1" /></TableCell>
            <TableCell><Skeleton class="h-4 w-20" /></TableCell>
            <TableCell><Skeleton class="h-4 w-24" /></TableCell>
            <TableCell><Skeleton class="h-8 w-8 rounded" /></TableCell>
          </TableRow>
        </TableBody>
      </Table>
      </div>

      <div v-else-if="isError" class="flex flex-col items-center justify-center py-12">
        <AlertCircle class="w-12 h-12 text-destructive mb-4" />
        <p class="text-lg font-medium text-foreground mb-2">Unable to load customers</p>
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

      <div v-else-if="customers && customers.length > 0">
        <div class="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead class="cursor-pointer hover:bg-muted/50" @click="toggleSort('firstName')">
                Customer
                <component
                  :is="getSortIcon('firstName')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'firstName' }"
                />
              </TableHead>
              <TableHead>Reference ID</TableHead>
              <TableHead class="cursor-pointer hover:bg-muted/50" @click="toggleSort('createdAt')">
                Created
                <component
                  :is="getSortIcon('createdAt')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'createdAt' }"
                />
              </TableHead>
              <TableHead class="w-[70px]">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="customer in paginatedCustomers"
              :key="customer.id"
              class="cursor-pointer hover:bg-muted/50"
              @click="onRowClick(customer)"
            >
              <TableCell>
                <div class="flex flex-col">
                  <span class="font-medium">{{ customer.firstName }} {{ customer.lastName }}</span>
                  <span class="text-xs text-muted-foreground">{{ customer.email }}</span>
                </div>
              </TableCell>
              <TableCell class="font-mono text-xs text-muted-foreground">{{ customer.referenceId || '' }}</TableCell>
              <TableCell class="text-sm text-muted-foreground">{{ customer.createdAt ? formatDateTime(customer.createdAt) : '' }}</TableCell>
              <TableCell>
                <DropdownMenu>
                  <DropdownMenuTrigger as-child @click.stop>
                    <Button variant="ghost" size="icon" class="h-8 w-8">
                      <MoreHorizontal class="h-4 w-4" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem @click.stop="onRowClick(customer)">
                      <Eye class="mr-2 h-4 w-4" />
                      View Details
                    </DropdownMenuItem>
                    <DropdownMenuItem @click.stop="onEditClick(customer)">
                      <Pencil class="mr-2 h-4 w-4" />
                      Edit
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
              {{ Math.min(currentPage * pageSize, sortedCustomers.length) }} of
              {{ sortedCustomers.length }} entries
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
        <p class="text-lg font-medium text-foreground mb-2">No customers yet</p>
        <p class="text-sm mb-4">Create your first customer to get started</p>
        <div class="flex gap-2">
          <Button @click="showCreateModal = true">
            <Plus class="w-4 h-4 mr-2" />
            Create Customer
          </Button>
          <Button variant="outline" @click="showStripeModal = true">
            Import from Stripe
          </Button>
        </div>
      </div>
    </div>

    <CreateCustomerModal v-model:visible="showCreateModal" />
    <StripeConnectionModal v-model:visible="showStripeModal" />
    <CustomerEditDrawer v-model:visible="showEditDrawer" :customer="selectedCustomerElement" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
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
import { Skeleton } from '@/components/ui/skeleton'
import {
  Plus,
  AlertCircle,
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  Search,
  Inbox,
  RefreshCw,
  LogOut,
  MoreHorizontal,
  Eye,
  Pencil
} from 'lucide-vue-next'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu'
import { useCustomersQuery } from '../queries'
import { useAuthStore } from '@/stores/auth'
import CreateCustomerModal from '../components/CreateCustomerModal.vue'
import StripeConnectionModal from '@/features/integrations/components/StripeConnectionModal.vue'
import CustomerEditDrawer from '../components/CustomerEditDrawer.vue'
import { formatDateTime } from '@/lib/formatters'
import type { CustomerElement } from '../types'
import { useTracking } from '@/lib/tracking'
import { useDemoPrefix } from '@/lib/useDemoPrefix'

const router = useRouter()
const { isDemo, demoPath } = useDemoPrefix()

// In demo mode, skip API calls and use mock data
const { data, isLoading, isError, refetch } = isDemo.value
  ? { data: ref(null), isLoading: ref(false), isError: ref(false), refetch: () => {} }
  : useCustomersQuery()
const authStore = isDemo.value ? { logout: () => {} } : useAuthStore()
const { track } = useTracking()

const DEMO_CUSTOMERS: CustomerElement[] = [
  { id: 'a1b2c3d4-e5f6-4789-abcd-ef0123456789', firstName: 'Sarah', lastName: 'Chen', email: 'sarah@outbound.io', referenceId: 'outbound-io', createdAt: '2026-03-18T10:00:00Z' },
  { id: 'b2c3d4e5-f6a7-4890-bcde-f01234567890', firstName: 'Marcus', lastName: 'Rodriguez', email: 'marcus@pipelineai.com', referenceId: 'pipeline-ai', createdAt: '2026-03-15T14:30:00Z' },
  { id: 'c3d4e5f6-a7b8-4901-cdef-012345678901', firstName: 'Emily', lastName: 'Park', email: 'emily@prospectlab.io', referenceId: 'prospect-lab', createdAt: '2026-03-10T09:00:00Z' },
  { id: 'd4e5f6a7-b8c9-4012-def0-123456789012', firstName: 'James', lastName: 'Mitchell', email: 'james@dealflow.co', referenceId: 'deal-flow', createdAt: '2026-02-28T16:00:00Z' },
  { id: 'e5f6a7b8-c9d0-4123-ef01-234567890123', firstName: 'Anika', lastName: 'Patel', email: 'anika@salesforge.dev', referenceId: 'sales-forge', createdAt: '2026-02-20T11:00:00Z' }
]

const customers = computed(() => {
  if (isDemo.value) return DEMO_CUSTOMERS
  if (!data.value) return []
  if (data.value.data) {
    // Format: { data: { customers: [...] } }
    if ('customers' in data.value.data && Array.isArray(data.value.data.customers)) {
      return data.value.data.customers
    }
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

const selectedCustomerElement = ref<CustomerElement | null>(null)
const showCreateModal = ref(false)
const showStripeModal = ref(false)
const showEditDrawer = ref(false)

const currentPage = ref(1)
const pageSize = ref(10)
const sortField = ref<string | null>(null)
const sortOrder = ref<'asc' | 'desc'>('asc')
const searchQuery = ref('')

watch(searchQuery, () => {
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

const filteredCustomers = computed(() => {
  if (!searchQuery.value.trim()) return customers.value
  const query = searchQuery.value.toLowerCase()
  return customers.value.filter(
    (customer) =>
      customer.referenceId?.toLowerCase().includes(query) ||
      customer.firstName?.toLowerCase().includes(query) ||
      customer.lastName?.toLowerCase().includes(query) ||
      `${customer.firstName} ${customer.lastName}`.toLowerCase().includes(query) ||
      customer.email?.toLowerCase().includes(query)
  )
})

const sortedCustomers = computed(() => {
  if (!sortField.value) return filteredCustomers.value
  return [...filteredCustomers.value].sort((a, b) => {
    const aVal = a[sortField.value as keyof CustomerElement]
    const bVal = b[sortField.value as keyof CustomerElement]
    if (aVal === null || aVal === undefined) return 1
    if (bVal === null || bVal === undefined) return -1
    if (aVal < bVal) return sortOrder.value === 'asc' ? -1 : 1
    if (aVal > bVal) return sortOrder.value === 'asc' ? 1 : -1
    return 0
  })
})

const totalPages = computed(() => Math.ceil(sortedCustomers.value.length / pageSize.value))

const paginatedCustomers = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return sortedCustomers.value.slice(start, end)
})

function onRowClick(customer: CustomerElement) {
  track('customer_detail_opened', { customerId: customer.id })
  router.push(demoPath(`/customers/${customer.id}`))
}

function onEditClick(customer: CustomerElement) {
  track('customer_edit_opened', { customerId: customer.id })
  selectedCustomerElement.value = customer
  showEditDrawer.value = true
}

</script>
