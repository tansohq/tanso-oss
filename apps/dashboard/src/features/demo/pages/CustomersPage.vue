<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-semibold text-foreground">Customers</h1>
        <p class="text-muted-foreground mt-1">Manage your customer accounts</p>
      </div>
      <Button>
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

      <div v-if="sortedCustomers.length > 0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead class="cursor-pointer hover:bg-muted/50" @click="toggleSort('name')">
                Customer
                <component
                  :is="getSortIcon('name')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'name' }"
                />
              </TableHead>
              <TableHead class="cursor-pointer hover:bg-muted/50" @click="toggleSort('email')">
                Email
                <component
                  :is="getSortIcon('email')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'email' }"
                />
              </TableHead>
              <TableHead
                class="cursor-pointer hover:bg-muted/50"
                @click="toggleSort('referenceId')"
              >
                Reference ID
                <component
                  :is="getSortIcon('referenceId')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'referenceId' }"
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
              @click="goToCustomerDetail(customer.id)"
            >
              <TableCell class="font-medium">{{ customer.name }}</TableCell>
              <TableCell>{{ customer.email }}</TableCell>
              <TableCell class="font-mono text-sm text-muted-foreground">
                <span v-if="customer.referenceId">{{ customer.referenceId }}</span>
                <span v-else class="text-muted-foreground/60">&mdash;</span>
              </TableCell>
              <TableCell>
                <DropdownMenu>
                  <DropdownMenuTrigger as-child @click.stop>
                    <Button variant="ghost" size="icon" class="h-8 w-8">
                      <MoreHorizontal class="h-4 w-4" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem @click.stop="goToCustomerDetail(customer.id)">
                      <Eye class="mr-2 h-4 w-4" />
                      View Details
                    </DropdownMenuItem>
                    <DropdownMenuItem @click.stop>
                      <Pencil class="mr-2 h-4 w-4" />
                      Edit
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
        <p class="text-lg font-medium text-foreground mb-2">No customers found</p>
        <p class="text-sm mb-4">Try adjusting your search or create a new customer</p>
        <Button>
          <Plus class="w-4 h-4 mr-2" />
          Create Customer
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
  Pencil,
  Inbox
} from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
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
import { customers as rawCustomers } from '../data/mockData'

const router = useRouter()

// Add referenceId to mock customers for demo display
const customers = rawCustomers.map((c) => ({
  ...c,
  referenceId: `ref_${c.id.replace('cust_', '')}`
}))

const searchQuery = ref('')
const sortField = ref<string | null>(null)
const sortOrder = ref<'asc' | 'desc'>('asc')
const currentPage = ref(1)
const pageSize = ref(10)

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
  if (!searchQuery.value.trim()) return customers
  const query = searchQuery.value.toLowerCase()
  return customers.filter(
    (c) =>
      c.name.toLowerCase().includes(query) ||
      c.email.toLowerCase().includes(query) ||
      c.referenceId?.toLowerCase().includes(query)
  )
})

const sortedCustomers = computed(() => {
  if (!sortField.value) return filteredCustomers.value
  return [...filteredCustomers.value].sort((a, b) => {
    const aVal = a[sortField.value as keyof typeof a]
    const bVal = b[sortField.value as keyof typeof b]
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

function goToCustomerDetail(id: string) {
  router.push(`/demo/customers/${id}`)
}
</script>
