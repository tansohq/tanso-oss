<template>
  <div>
    <h4 class="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-2">Transactions</h4>

    <div v-if="isLoading" class="py-2">
      <Skeleton class="h-8 w-full mb-1" />
      <Skeleton class="h-8 w-full" />
    </div>

    <div v-else-if="transactions.length === 0" class="text-xs text-muted-foreground py-2">
      No transactions recorded.
    </div>

    <Table v-else>
      <TableHeader>
        <TableRow>
          <TableHead class="text-xs">Amount</TableHead>
          <TableHead class="text-xs">Type</TableHead>
          <TableHead class="text-xs">Description</TableHead>
          <TableHead class="text-xs">Date</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        <TableRow v-for="tx in transactions" :key="tx.id">
          <TableCell class="tabular-nums font-mono text-sm" :class="tx.amount < 0 ? 'text-red-600' : 'text-green-600'">
            {{ tx.amount > 0 ? '+' : '' }}{{ tx.amount.toLocaleString() }}
          </TableCell>
          <TableCell>
            <Badge class="shadow-none" :class="tx.type === 'DEDUCTION' ? 'bg-red-50 text-red-700 border border-red-200/50' : 'bg-green-50 text-green-700 border border-green-200/50'">
              {{ tx.type }}
            </Badge>
          </TableCell>
          <TableCell class="text-sm text-muted-foreground">{{ tx.description || '—' }}</TableCell>
          <TableCell class="text-sm tabular-nums">{{ formatDateTime(tx.createdAt) }}</TableCell>
        </TableRow>
      </TableBody>
    </Table>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Skeleton } from '@/components/ui/skeleton'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import { formatDateTime } from '@/lib/formatters'
import { usePoolTransactionsQuery } from '../queries'
import type { CreditTransaction } from '../types'

const props = defineProps<{
  poolId: string
}>()

const { data, isLoading } = usePoolTransactionsQuery(props.poolId)

const transactions = computed<CreditTransaction[]>(() => {
  if (!data.value?.data) return []
  return Array.isArray(data.value.data) ? data.value.data : []
})
</script>
