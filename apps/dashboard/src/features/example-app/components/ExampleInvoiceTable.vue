<template>
  <Card v-if="invoices.length === 0" class="p-12">
    <div class="text-center space-y-4">
      <FileText class="w-16 h-16 mx-auto text-muted-foreground" />
      <div>
        <p class="font-semibold text-lg mb-2">No invoices yet</p>
        <p class="text-muted-foreground">
          Your invoice history will appear here once you subscribe to a plan.
        </p>
      </div>
    </div>
  </Card>

  <Card v-else class="overflow-hidden">
    <div class="overflow-x-auto">
      <table class="w-full">
        <thead class="bg-muted/50 border-b">
          <tr>
            <th class="text-left py-4 px-6 font-semibold text-sm">Invoice ID</th>
            <th class="text-left py-4 px-6 font-semibold text-sm">Date</th>
            <th class="text-left py-4 px-6 font-semibold text-sm">Plan</th>
            <th class="text-left py-4 px-6 font-semibold text-sm">Amount</th>
            <th class="text-left py-4 px-6 font-semibold text-sm">Status</th>
          </tr>
        </thead>
        <tbody class="divide-y">
          <tr
            v-for="invoice in invoices"
            :key="invoice.id"
            class="hover:bg-muted/30 transition-colors"
          >
            <td class="py-4 px-6 font-mono text-sm">{{ invoice.id }}</td>
            <td class="py-4 px-6 text-sm">{{ formatInvoiceDate(invoice.date) }}</td>
            <td class="py-4 px-6 text-sm">{{ invoice.planName }}</td>
            <td class="py-4 px-6 text-sm font-semibold">{{ formatPrice(invoice.amount) }}</td>
            <td class="py-4 px-6">
              <span :class="['inline-flex items-center px-3 py-1 rounded-full text-xs font-medium', statusColor(invoice.status)]">
                {{ invoice.status }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </Card>
</template>

<script setup lang="ts">
import { FileText } from 'lucide-vue-next'
import { Card } from '@/components/ui/card'
import { formatPrice } from '@/lib/formatters'
import type { SimulatedInvoice } from '../types'

defineProps<{
  invoices: SimulatedInvoice[]
}>()

function formatInvoiceDate(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  })
}

function statusColor(status: string): string {
  if (status === 'Paid') return 'text-green-600 bg-green-50'
  return 'text-yellow-600 bg-yellow-50'
}
</script>
