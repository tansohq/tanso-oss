<template>
  <div>
    <h4 class="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-2">Grants</h4>

    <div v-if="isLoading" class="py-2">
      <Skeleton class="h-8 w-full mb-1" />
      <Skeleton class="h-8 w-full" />
    </div>

    <div v-else-if="grants.length === 0" class="text-xs text-muted-foreground py-2">
      No grants recorded.
    </div>

    <Table v-else>
      <TableHeader>
        <TableRow>
          <TableHead class="text-xs">Amount</TableHead>
          <TableHead class="text-xs">Remaining</TableHead>
          <TableHead class="text-xs">Source</TableHead>
          <TableHead class="text-xs">Expires</TableHead>
          <TableHead class="text-xs">Created</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        <TableRow v-for="grant in grants" :key="grant.id">
          <TableCell class="tabular-nums font-mono text-sm">{{ grant.amount.toLocaleString() }}</TableCell>
          <TableCell class="tabular-nums font-mono text-sm">{{ grant.remainingAmount?.toLocaleString() ?? '—' }}</TableCell>
          <TableCell class="text-sm">{{ grant.source || '—' }}</TableCell>
          <TableCell class="text-sm">{{ grant.expiresAt ? formatDateTime(grant.expiresAt) : 'Never' }}</TableCell>
          <TableCell class="text-sm tabular-nums">{{ formatDateTime(grant.createdAt) }}</TableCell>
        </TableRow>
      </TableBody>
    </Table>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import { formatDateTime } from '@/lib/formatters'
import { usePoolGrantsQuery } from '../queries'
import type { CreditGrant } from '../types'

const props = defineProps<{
  poolId: string
}>()

const { data, isLoading } = usePoolGrantsQuery(props.poolId)

const grants = computed<CreditGrant[]>(() => {
  if (!data.value?.data) return []
  return Array.isArray(data.value.data) ? data.value.data : []
})
</script>
