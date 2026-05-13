<template>
  <Card>
    <CardHeader class="pb-3">
      <div class="flex items-center justify-between">
        <CardTitle class="text-base">Results</CardTitle>
        <div v-if="results && results.rows.length > 0" class="flex items-center gap-3">
          <span class="text-sm text-muted-foreground"> {{ results.rows.length }} rows </span>
          <Button variant="outline" size="sm" @click="exportToCsv">
            <Download class="h-4 w-4 mr-2" />
            Download CSV
          </Button>
        </div>
        <span v-else-if="results" class="text-sm text-muted-foreground">
          {{ results.rows.length }} rows
        </span>
      </div>
    </CardHeader>
    <CardContent>
      <div v-if="!results" class="text-center py-12 text-muted-foreground">
        <Table2 class="h-12 w-12 mx-auto mb-4 opacity-50" />
        <p class="text-sm">Add at least one row dimension and one metric to see results</p>
      </div>

      <div v-else-if="results.rows.length === 0" class="text-center py-12 text-muted-foreground">
        <AlertCircle class="h-12 w-12 mx-auto mb-4 opacity-50" />
        <p class="text-sm">No data matches your current filters</p>
      </div>

      <div v-else class="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead v-for="header in results.headers" :key="header" class="font-semibold">
                {{ header }}
              </TableHead>
              <TableHead
                v-for="colHeader in results.columnHeaders"
                :key="colHeader"
                class="text-right font-semibold"
              >
                {{ colHeader }}
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="(row, idx) in results.rows" :key="idx">
              <TableCell
                v-for="(key, keyIdx) in row.rowKeys"
                :key="`row-${idx}-${keyIdx}`"
                class="font-medium"
              >
                {{ formatRowKey(key) }}
              </TableCell>
              <TableCell
                v-for="colHeader in results.columnHeaders"
                :key="`val-${idx}-${colHeader}`"
                class="text-right tabular-nums"
              >
                {{ formatValue(row.values[colHeader], colHeader) }}
              </TableCell>
            </TableRow>
            <!-- Totals row -->
            <TableRow class="bg-muted/50 font-semibold">
              <TableCell :colspan="results.headers.length"> Total </TableCell>
              <TableCell
                v-for="colHeader in results.columnHeaders"
                :key="`total-${colHeader}`"
                class="text-right tabular-nums"
              >
                {{ formatValue(results.totals[colHeader], colHeader) }}
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
    </CardContent>
  </Card>
</template>

<script setup lang="ts">
import { Table2, AlertCircle, Download } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import type { PivotResult } from '../../composables/usePivotState'

const props = defineProps<{
  results: PivotResult | null
}>()

function escapeCsvValue(value: string | number | null | undefined): string {
  if (value === null || value === undefined) {
    return ''
  }
  const stringValue = String(value)
  // If value contains comma, quote, or newline, wrap in quotes and escape existing quotes
  if (stringValue.includes(',') || stringValue.includes('"') || stringValue.includes('\n')) {
    return `"${stringValue.replace(/"/g, '""')}"`
  }
  return stringValue
}

function exportToCsv() {
  if (!props.results || props.results.rows.length === 0) return

  const { headers, columnHeaders, rows, totals } = props.results
  const csvRows: string[] = []

  // Build header row
  const headerRow = [...headers, ...columnHeaders].map(escapeCsvValue)
  csvRows.push(headerRow.join(','))

  // Build data rows
  for (const row of rows) {
    const rowData: string[] = []
    // Add row keys
    for (const key of row.rowKeys) {
      rowData.push(escapeCsvValue(key))
    }
    // Add values (raw numeric, not formatted)
    for (const colHeader of columnHeaders) {
      const value = row.values[colHeader]
      rowData.push(escapeCsvValue(value ?? ''))
    }
    csvRows.push(rowData.join(','))
  }

  // Add totals row
  const totalsRow: string[] = ['Total']
  // Fill empty cells for remaining row headers
  for (let i = 1; i < headers.length; i++) {
    totalsRow.push('')
  }
  // Add totals values
  for (const colHeader of columnHeaders) {
    const value = totals[colHeader]
    totalsRow.push(escapeCsvValue(value ?? ''))
  }
  csvRows.push(totalsRow.join(','))

  // Create blob and trigger download
  const csvContent = csvRows.join('\n')
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
  const filename = `pivot-export-${timestamp}.csv`

  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

function formatRowKey(key: string): string {
  // Format margin status for display
  switch (key) {
    case 'healthy':
      return 'Healthy'
    case 'at_risk':
      return 'At Risk'
    case 'underwater':
      return 'Underwater'
    default:
      return key
  }
}

function formatValue(value: number | null | undefined, header: string): string {
  if (value === null || value === undefined) {
    return '-'
  }

  // Check if this is a margin/percentage column
  const isMargin = header.toLowerCase().includes('margin')
  if (isMargin) {
    return `${value.toFixed(1)}%`
  }

  // Check if this is a count column
  const isCount = header.toLowerCase().includes('count')
  if (isCount) {
    return Math.round(value).toLocaleString()
  }

  // Default to currency format for MRR/Costs
  const isCurrency =
    header.toLowerCase().includes('mrr') ||
    header.toLowerCase().includes('cost') ||
    header === 'MRR' ||
    header === 'Costs'

  if (isCurrency) {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 0,
      maximumFractionDigits: 0
    }).format(value)
  }

  // For usage/actions, format as number
  if (header.toLowerCase().includes('usage') || header.toLowerCase().includes('action')) {
    return Math.round(value).toLocaleString()
  }

  // Default number format
  return value.toLocaleString(undefined, { maximumFractionDigits: 2 })
}
</script>
