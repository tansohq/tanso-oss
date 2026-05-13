<template>
  <div class="bg-card rounded-lg border shadow-sm">
    <div class="overflow-x-auto">
      <Table class="[&_td]:whitespace-nowrap [&_th]:whitespace-nowrap">
        <TableHeader>
          <TableRow>
            <TableHead>{{ groupLabel }}</TableHead>
            <TableHead class="text-right">Events</TableHead>
            <TableHead class="text-right">Total Cost</TableHead>
            <TableHead class="text-right">Total Revenue</TableHead>
            <TableHead class="text-right">Margin</TableHead>
            <TableHead class="text-right">Last Seen</TableHead>
            <TableHead class="w-10" />
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow
            v-for="group in groups"
            :key="group.groupKey"
            class="cursor-pointer hover:bg-muted/50 transition-colors"
            @click="emit('drill-down', group)"
          >
            <TableCell class="font-medium">{{ group.groupLabel }}</TableCell>
            <TableCell class="text-right tabular-nums">
              {{ group.eventCount.toLocaleString() }}
            </TableCell>
            <TableCell class="text-right tabular-nums text-sm">
              <span v-if="group.totalCost != null" class="font-medium">{{
                formatCost(Number(group.totalCost))
              }}</span>
              <span v-else class="text-muted-foreground/40">&mdash;</span>
            </TableCell>
            <TableCell class="text-right tabular-nums text-sm">
              <span v-if="group.totalRevenue != null" class="font-medium">{{
                formatCost(Number(group.totalRevenue))
              }}</span>
              <span v-else class="text-muted-foreground/40">&mdash;</span>
            </TableCell>
            <TableCell class="text-right tabular-nums text-sm">
              <span
                v-if="
                  group.totalCost != null &&
                  group.totalRevenue != null &&
                  Number(group.totalRevenue) > 0
                "
                :class="
                  Number(group.totalRevenue) - Number(group.totalCost) >= 0
                    ? 'text-green-600 dark:text-green-400'
                    : 'text-red-600 dark:text-red-400'
                "
              >
                {{
                  (
                    ((Number(group.totalRevenue) - Number(group.totalCost)) /
                      Number(group.totalRevenue)) *
                    100
                  ).toFixed(0)
                }}%
              </span>
              <span v-else class="text-muted-foreground/40">&mdash;</span>
            </TableCell>
            <TableCell class="text-right tabular-nums text-sm text-muted-foreground">
              {{ formatDateTime(group.lastOccurredAt) }}
            </TableCell>
            <TableCell>
              <ChevronRight class="h-4 w-4 text-muted-foreground/40" />
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ChevronRight } from 'lucide-vue-next'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import { formatDateTime, formatCost } from '@/lib/formatters'
import type { EventGroup, GroupBy } from '../types'

const props = defineProps<{
  groups: EventGroup[]
  groupBy: GroupBy
}>()

const emit = defineEmits<{
  'drill-down': [group: EventGroup]
}>()

const groupLabel = computed(
  () =>
    ({
      MODEL: 'Model',
      MODEL_PROVIDER: 'Provider',
      CUSTOMER: 'Customer',
      FEATURE: 'Feature',
      EVENT_NAME: 'Event Name'
    })[props.groupBy]
)
</script>
