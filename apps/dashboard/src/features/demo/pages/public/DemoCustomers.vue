<template>
  <div class="p-6 pb-20">
    <!-- Header -->
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-semibold text-foreground">Customer Margins</h1>
        <p class="text-muted-foreground mt-1">Per-customer profitability analysis</p>
      </div>
      <div class="flex items-center gap-3">
        <div class="relative">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input v-model="searchQuery" placeholder="Search customers..." class="pl-9 w-64" />
        </div>
        <Select v-model="sortBy">
          <SelectTrigger class="w-48">
            <SelectValue placeholder="Sort by" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="margin_asc">Margin (low to high)</SelectItem>
            <SelectItem value="margin_desc">Margin (high to low)</SelectItem>
            <SelectItem value="mrr_desc">MRR (high to low)</SelectItem>
            <SelectItem value="cost_desc">Cost (high to low)</SelectItem>
          </SelectContent>
        </Select>
      </div>
    </div>

    <!-- Customers Table -->
    <Card>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead class="w-[250px]">Customer</TableHead>
            <TableHead>Plan</TableHead>
            <TableHead class="text-right">MRR</TableHead>
            <TableHead class="text-right">Costs</TableHead>
            <TableHead class="text-right">Profit</TableHead>
            <TableHead class="text-right">Margin</TableHead>
            <TableHead class="text-center">Status</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow
            v-for="customer in filteredCustomers"
            :key="customer.id"
            class="cursor-pointer hover:bg-muted/50"
            :class="customer.margin < 0.4 ? 'bg-red-500/5' : ''"
          >
            <TableCell>
              <div>
                <p class="font-medium">{{ customer.name }}</p>
                <p class="text-xs text-muted-foreground">{{ customer.id }}</p>
              </div>
            </TableCell>
            <TableCell>
              <Badge variant="outline">{{ customer.plan || 'Growth' }}</Badge>
            </TableCell>
            <TableCell class="text-right font-medium">
              ${{ customer.mrr.toLocaleString() }}
            </TableCell>
            <TableCell class="text-right text-muted-foreground">
              ${{ customer.costs.total.toLocaleString() }}
            </TableCell>
            <TableCell class="text-right">
              <span
                :class="
                  customer.mrr - customer.costs.total < 0 ? 'text-red-500' : 'text-emerald-500'
                "
              >
                ${{ (customer.mrr - customer.costs.total).toLocaleString() }}
              </span>
            </TableCell>
            <TableCell class="text-right">
              <Badge :class="getMarginBadgeClass(customer.margin)" class="font-medium">
                {{ Math.round(customer.margin * 100) }}%
              </Badge>
            </TableCell>
            <TableCell class="text-center">
              <AlertTriangle v-if="customer.margin < 0.4" class="h-4 w-4 text-amber-500 mx-auto" />
              <Check v-else class="h-4 w-4 text-emerald-500 mx-auto" />
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Search, AlertTriangle, Check } from 'lucide-vue-next'
import { Card } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
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
import { useDemoState } from '../../composables/useDemoState'

const { customers } = useDemoState()

const searchQuery = ref('')
const sortBy = ref('margin_asc')

const filteredCustomers = computed(() => {
  let result = [...customers]

  // Search filter
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(
      (c) => c.name.toLowerCase().includes(query) || c.id.toLowerCase().includes(query)
    )
  }

  // Sort
  switch (sortBy.value) {
    case 'margin_asc':
      result.sort((a, b) => a.margin - b.margin)
      break
    case 'margin_desc':
      result.sort((a, b) => b.margin - a.margin)
      break
    case 'mrr_desc':
      result.sort((a, b) => b.mrr - a.mrr)
      break
    case 'cost_desc':
      result.sort((a, b) => b.costs.total - a.costs.total)
      break
  }

  return result
})

function getMarginBadgeClass(margin: number) {
  if (margin >= 0.7) return 'bg-emerald-500/20 text-emerald-600 border-emerald-500/30'
  if (margin >= 0.4) return 'bg-amber-500/20 text-amber-600 border-amber-500/30'
  return 'bg-red-500/20 text-red-600 border-red-500/30'
}
</script>
