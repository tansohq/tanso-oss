<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-semibold text-foreground">Invoices</h1>
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
            <SelectItem value="past_due">Past Due</SelectItem>
            <SelectItem value="issued">Issued</SelectItem>
            <SelectItem value="draft">Draft</SelectItem>
            <SelectItem value="paid">Paid</SelectItem>
            <SelectItem value="voided">Voided</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div v-if="sortedInvoices.length > 0">
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
              <TableHead
                class="cursor-pointer hover:bg-muted/50"
                @click="toggleSort('planName')"
              >
                Plan
                <component
                  :is="getSortIcon('planName')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'planName' }"
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
              <TableCell class="font-medium">{{ invoice.customerName }}</TableCell>
              <TableCell>{{ invoice.planName }}</TableCell>
              <TableCell class="tabular-nums">{{ formatAmount(invoice.amount) }}</TableCell>
              <TableCell>
                <Badge :class="getStatusClasses(invoice.status)" class="shadow-none">
                  {{ formatStatus(invoice.status) }}
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
        <p class="text-sm">Invoices will appear here when billing cycles generate them</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
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
import { invoices } from '../data/mockData'
import { useDemoPrefix } from '@/lib/useDemoPrefix'
import type { Invoice } from '../types'

const router = useRouter()
const { demoPath } = useDemoPrefix()

// Filter state
const searchQuery = ref('')
const statusFilter = ref<string>('all')

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

// Sorting functions
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

// Status-filtered invoices
const statusFilteredInvoices = computed(() => {
  if (statusFilter.value === 'all') return invoices
  return invoices.filter((invoice) => invoice.status === statusFilter.value)
})

// Search-filtered invoices
const filteredInvoices = computed(() => {
  if (!searchQuery.value.trim()) return statusFilteredInvoices.value
  const query = searchQuery.value.toLowerCase()
  return statusFilteredInvoices.value.filter(
    (invoice) =>
      invoice.customerName.toLowerCase().includes(query) ||
      invoice.planName.toLowerCase().includes(query) ||
      invoice.id.toLowerCase().includes(query) ||
      invoice.status.toLowerCase().includes(query)
  )
})

// Sorted invoices
const sortedInvoices = computed(() => {
  if (!sortField.value) return filteredInvoices.value
  return [...filteredInvoices.value].sort((a, b) => {
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
const totalPages = computed(() => Math.ceil(sortedInvoices.value.length / pageSize.value))

const paginatedInvoices = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return sortedInvoices.value.slice(start, end)
})

// Formatters (matching real page patterns)
function formatAmount(amount: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2
  }).format(amount)
}

function formatDate(dateStr: string): string {
  if (!dateStr) return '\u2014'
  const date = new Date(dateStr + 'T00:00:00')
  return date.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
}

function formatStatus(status: string): string {
  const statusMap: Record<string, string> = {
    draft: 'Draft',
    pending_issue: 'Pending',
    issued: 'Issued',
    paid: 'Paid',
    past_due: 'Past Due',
    payment_failed: 'Payment Failed',
    voided: 'Voided'
  }
  return statusMap[status] || status.charAt(0).toUpperCase() + status.slice(1).replace(/_/g, ' ')
}

function getStatusClasses(status: string): string {
  switch (status) {
    case 'paid':
      return 'bg-emerald-50 text-emerald-700 border border-emerald-200/50 shadow-none'
    case 'issued':
    case 'pending_issue':
      return 'bg-amber-50 text-amber-700 border border-amber-200/50 shadow-none'
    case 'past_due':
    case 'payment_failed':
      return 'bg-red-50 text-red-700 border border-red-200/50 shadow-none'
    case 'draft':
    case 'voided':
      return 'bg-gray-100 text-gray-700 border border-gray-200/50 shadow-none'
    default:
      return 'bg-gray-100 text-gray-700 border-0 shadow-none'
  }
}

function onRowClick(invoice: Invoice) {
  router.push(demoPath(`/invoices/${invoice.id}`))
}
</script>
