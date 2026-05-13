<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-semibold text-foreground">Subscriptions</h1>
        <p class="text-muted-foreground mt-1">View active customer subscriptions</p>
      </div>
      <Button @click="showAddModal = true">
        <Plus class="w-4 h-4 mr-2" />
        Create Subscription
      </Button>
    </div>

    <DemoAddSubscriptionModal v-model:visible="showAddModal" />

    <div class="bg-card rounded-lg border shadow-sm">
      <div class="flex items-center justify-between gap-4 p-4 border-b">
        <div class="relative max-w-sm flex-1">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input v-model="searchQuery" placeholder="Search subscriptions..." class="pl-9" />
        </div>
        <Tabs v-model="statusFilter">
          <TabsList>
            <TabsTrigger value="all"> All ({{ statusCounts.all }}) </TabsTrigger>
            <TabsTrigger value="active"> Active ({{ statusCounts.active }}) </TabsTrigger>
            <TabsTrigger value="inactive"> Inactive ({{ statusCounts.inactive }}) </TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      <div v-if="subscriptionsData.length > 0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead
                class="cursor-pointer hover:bg-muted/50"
                @click="toggleSort('customerName')"
              >
                Customer
                <component
                  :is="getSortIcon('customerName')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'customerName' }"
                />
              </TableHead>
              <TableHead class="cursor-pointer hover:bg-muted/50" @click="toggleSort('planName')">
                Plan
                <component
                  :is="getSortIcon('planName')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'planName' }"
                />
              </TableHead>
              <TableHead
                class="cursor-pointer hover:bg-muted/50"
                @click="toggleSort('planName')"
              >
                Base Price
                <component
                  :is="getSortIcon('planName')"
                  class="ml-2 h-4 w-4 inline"
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
              v-for="sub in paginatedSubscriptions"
              :key="sub.id"
              class="cursor-pointer hover:bg-muted/50"
              @click="navigateToDetail(sub.id)"
            >
              <TableCell class="font-medium">{{ sub.customerName }}</TableCell>
              <TableCell>{{ sub.planName }}</TableCell>
              <TableCell class="tabular-nums">{{ formatPrice(getPlanPrice(sub.planName)) }}<span class="text-muted-foreground">/{{ formatIntervalShort(getBillingIntervalMonths(sub.billingCycle)) }}</span></TableCell>
              <TableCell>
                <Badge :class="getStatusClasses(sub.status)" class="shadow-none">
                  {{ formatStatus(sub.status) }}
                </Badge>
              </TableCell>
              <TableCell class="tabular-nums text-muted-foreground">{{ formatDate(sub.currentPeriodEnd) }}</TableCell>
              <TableCell>
                <DropdownMenu>
                  <DropdownMenuTrigger as-child @click.stop>
                    <Button variant="ghost" size="icon" class="h-8 w-8">
                      <MoreHorizontal class="h-4 w-4" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem @click.stop="navigateToDetail(sub.id)">
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

      <div v-else class="flex flex-col items-center justify-center py-12 text-muted-foreground">
        <Inbox class="w-12 h-12 mb-4" />
        <p class="text-lg font-medium text-foreground mb-2">No subscriptions yet</p>
        <p class="text-sm mb-4">
          Subscriptions will appear here when customers subscribe to your plans
        </p>
        <Button @click="showAddModal = true">
          <Plus class="w-4 h-4 mr-2" />
          Create Subscription
        </Button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Plus,
  Search,
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  MoreHorizontal,
  Eye,
  Inbox
} from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
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
import { useDemoState } from '../composables/useDemoState'
import { formatPrice, formatIntervalShort, formatDate } from '@/lib/formatters'
import DemoAddSubscriptionModal from '../components/subscriptions/DemoAddSubscriptionModal.vue'

const router = useRouter()
const { subscriptionsData } = useDemoState()

const showAddModal = ref(false)

// Filter state
const statusFilter = ref('all')
const searchQuery = ref('')

// Sorting state
const sortField = ref<string | null>(null)
const sortOrder = ref<'asc' | 'desc'>('asc')

// Pagination state
const currentPage = ref(1)
const pageSize = ref(10)

// Reset page when filters change
watch([searchQuery, statusFilter], () => {
  currentPage.value = 1
})

// Map demo status to active/inactive for tab counts
function isActive(status: string): boolean {
  return status === 'active'
}

const statusCounts = computed(() => {
  const all = subscriptionsData.value.length
  const active = subscriptionsData.value.filter((s) => isActive(s.status)).length
  return { all, active, inactive: all - active }
})

// Map plan names to mock base prices
function getPlanPrice(planName: string): number {
  switch (planName) {
    case 'Starter':
      return 49
    case 'Growth':
      return 199
    case 'Enterprise':
      return 799
    default:
      return 0
  }
}

// Map billing cycle string to interval months
function getBillingIntervalMonths(billingCycle: string): number {
  switch (billingCycle) {
    case 'monthly':
      return 1
    case 'quarterly':
      return 3
    case 'annual':
      return 12
    default:
      return 1
  }
}

// Format status for display (matching real page style)
function formatStatus(status: string): string {
  switch (status) {
    case 'active':
      return 'Active'
    case 'canceled':
      return 'Inactive'
    case 'paused':
      return 'Inactive'
    default:
      return status
  }
}

// Status badge classes matching the real page's getSubscriptionStatusClasses
function getStatusClasses(status: string): string {
  switch (status) {
    case 'active':
      return 'bg-emerald-50 text-emerald-700 border border-emerald-200/50 shadow-none'
    case 'canceled':
      return 'bg-gray-100 text-gray-700 border border-gray-200/50 shadow-none'
    case 'paused':
      return 'bg-amber-50 text-amber-700 border border-amber-200/50 shadow-none'
    default:
      return 'bg-gray-100 text-gray-700 border border-gray-200/50 shadow-none'
  }
}

// Sorting
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

// Filtered subscriptions
const filteredSubscriptions = computed(() => {
  let result = subscriptionsData.value

  if (statusFilter.value === 'active') {
    result = result.filter((s) => isActive(s.status))
  } else if (statusFilter.value === 'inactive') {
    result = result.filter((s) => !isActive(s.status))
  }

  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(
      (s) =>
        s.customerName?.toLowerCase().includes(query) ||
        s.customerEmail?.toLowerCase().includes(query) ||
        s.planName?.toLowerCase().includes(query)
    )
  }

  return result
})

// Sorted subscriptions
const sortedSubscriptions = computed(() => {
  if (!sortField.value) return filteredSubscriptions.value
  return [...filteredSubscriptions.value].sort((a, b) => {
    const aVal = a[sortField.value as keyof typeof a]
    const bVal = b[sortField.value as keyof typeof b]
    if (aVal === null || aVal === undefined) return 1
    if (bVal === null || bVal === undefined) return -1
    if (aVal < bVal) return sortOrder.value === 'asc' ? -1 : 1
    if (aVal > bVal) return sortOrder.value === 'asc' ? 1 : -1
    return 0
  })
})

// Pagination
const totalPages = computed(() => Math.ceil(sortedSubscriptions.value.length / pageSize.value))

const paginatedSubscriptions = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return sortedSubscriptions.value.slice(start, end)
})

function navigateToDetail(id: string) {
  router.push({ name: 'demo-subscription-detail', params: { id } })
}
</script>
