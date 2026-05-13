<template>
  <Table>
    <TableHeader>
      <TableRow>
        <TableHead v-for="(col, i) in columns" :key="i">{{ col }}</TableHead>
      </TableRow>
    </TableHeader>
    <TableBody>
      <TableRow v-for="row in rows" :key="row">
        <TableCell v-for="(_col, i) in columns" :key="i">
          <Skeleton :class="getColumnWidth(i)" />
        </TableCell>
      </TableRow>
    </TableBody>
  </Table>
</template>

<script setup lang="ts">
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import { Skeleton } from '@/components/ui/skeleton'

const props = withDefaults(
  defineProps<{
    columns: string[]
    rows?: number
    columnWidths?: string[]
  }>(),
  {
    rows: 5,
    columnWidths: () => []
  }
)

function getColumnWidth(index: number): string {
  if (props.columnWidths[index]) {
    return `h-4 ${props.columnWidths[index]}`
  }
  return 'h-4 w-24'
}
</script>
