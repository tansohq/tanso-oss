<template>
  <div class="p-6 pb-16">
    <!-- Breadcrumb -->
    <div class="flex items-center gap-2 text-sm text-muted-foreground mb-6">
      <router-link :to="isDemo ? '/demo/invoices' : '/invoices'" class="hover:text-foreground"
        >Invoices</router-link
      >
      <ChevronRight class="h-4 w-4" />
      <span class="text-foreground">{{ invoice?.id || 'Invoice' }}</span>
    </div>

    <div v-if="invoice" class="max-w-4xl">
      <!-- Header -->
      <div class="flex items-center justify-between mb-6">
        <div class="flex items-center gap-4">
          <h1 class="text-2xl font-semibold text-foreground font-mono">{{ invoice.id }}</h1>
          <Badge :class="getStatusColor(invoice.status)" class="border-0 capitalize">
            {{ formatStatus(invoice.status) }}
          </Badge>
        </div>
        <div class="flex gap-2">
          <Button variant="outline" v-if="invoice.status === 'draft'">
            <Send class="h-4 w-4 mr-2" />
            Issue Invoice
          </Button>
          <Button variant="outline" v-if="invoice.status === 'issued'">
            <CreditCard class="h-4 w-4 mr-2" />
            Record Payment
          </Button>
          <Button variant="ghost" size="icon">
            <MoreHorizontal class="h-4 w-4" />
          </Button>
        </div>
      </div>

      <!-- Summary Card -->
      <Card class="p-6 mb-6">
        <div class="flex items-start justify-between mb-4">
          <div>
            <div class="text-sm text-muted-foreground mb-1">
              {{ invoice.isFinalized ? 'Total amount due' : 'This period to date' }}
            </div>
            <div class="text-4xl font-semibold tabular-nums">
              ${{ invoice.amount.toLocaleString() }}
            </div>
          </div>
          <div class="text-right text-sm text-muted-foreground">
            <div>{{ invoice.customerName }}</div>
            <div>{{ invoice.customerEmail }}</div>
          </div>
        </div>
        <div
          v-if="!invoice.isFinalized"
          class="flex items-start gap-2 p-3 bg-amber-50 text-amber-800 rounded-lg text-sm"
        >
          <AlertTriangle class="h-4 w-4 mt-0.5 flex-shrink-0" />
          <span
            >Invoice amounts are not finalized and may change before the billing period
            closes.</span
          >
        </div>
        <div class="flex items-center justify-between mt-4 text-sm text-muted-foreground">
          <span
            >Billing period:
            {{ formatDateRange(invoice.billingPeriodStart, invoice.billingPeriodEnd) }}</span
          >
          <span>Due: {{ formatDate(invoice.dueDate) }}</span>
        </div>
      </Card>

      <!-- Line Items -->
      <Card class="p-6">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-sm font-medium">{{ invoice.planName }} Plan</h3>
          <router-link
            :to="isDemo ? `/demo/plans/${invoice.planId}` : `/plans/${invoice.planId}`"
            class="text-sm text-primary hover:underline"
          >
            View subscription
          </router-link>
        </div>

        <!-- Usage-Based Charges -->
        <div class="mb-6" v-if="usageBasedItems.length > 0">
          <div class="text-sm font-medium text-muted-foreground mb-3">Usage-Based Charges</div>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Feature</TableHead>
                <TableHead>Dimension</TableHead>
                <TableHead class="text-right">Quantity</TableHead>
                <TableHead class="text-right">Rate</TableHead>
                <TableHead class="text-right">Amount</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="item in usageBasedItems" :key="item.id">
                <TableCell class="font-medium">{{ item.featureName }}</TableCell>
                <TableCell class="text-muted-foreground">{{ item.dimension || '—' }}</TableCell>
                <TableCell class="text-right tabular-nums">{{
                  item.quantity.toLocaleString()
                }}</TableCell>
                <TableCell class="text-right tabular-nums">${{ formatRate(item.rate) }}</TableCell>
                <TableCell class="text-right tabular-nums font-medium"
                  >${{ item.amount.toLocaleString() }}</TableCell
                >
              </TableRow>
            </TableBody>
          </Table>
        </div>

        <!-- Fixed Fees -->
        <div class="mb-6" v-if="fixedItems.length > 0">
          <div class="text-sm font-medium text-muted-foreground mb-3">Fixed Fees</div>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Description</TableHead>
                <TableHead class="text-right">Quantity</TableHead>
                <TableHead class="text-right">Rate</TableHead>
                <TableHead class="text-right">Amount</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="item in fixedItems" :key="item.id">
                <TableCell class="font-medium">{{ item.description }}</TableCell>
                <TableCell class="text-right tabular-nums">{{ item.quantity }}</TableCell>
                <TableCell class="text-right tabular-nums"
                  >${{ item.rate.toLocaleString() }}</TableCell
                >
                <TableCell class="text-right tabular-nums font-medium"
                  >${{ item.amount.toLocaleString() }}</TableCell
                >
              </TableRow>
            </TableBody>
          </Table>
        </div>

        <!-- Totals -->
        <div class="border-t pt-4 space-y-2">
          <div class="flex justify-between text-sm">
            <span class="text-muted-foreground">Subtotal</span>
            <span class="tabular-nums">${{ invoice.subtotal.toLocaleString() }}</span>
          </div>
          <div class="flex justify-between text-sm" v-if="invoice.tax > 0">
            <span class="text-muted-foreground">Tax</span>
            <span class="tabular-nums">${{ invoice.tax.toLocaleString() }}</span>
          </div>
          <div class="flex justify-between font-semibold pt-2 border-t">
            <span>Total</span>
            <span class="tabular-nums">${{ invoice.amount.toLocaleString() }}</span>
          </div>
        </div>

        <!-- Usage Details Toggle -->
        <button
          @click="showUsageDetails = !showUsageDetails"
          class="flex items-center gap-2 text-sm text-primary mt-6 hover:underline"
        >
          <ChevronDown
            :class="['h-4 w-4 transition-transform', showUsageDetails && 'rotate-180']"
          />
          {{ showUsageDetails ? 'Hide' : 'Show' }} usage details
        </button>

        <div v-if="showUsageDetails" class="mt-4 p-4 bg-muted rounded-lg">
          <div class="text-sm font-medium mb-3">Usage Breakdown</div>
          <div class="space-y-3 text-sm">
            <div
              v-for="item in usageBasedItems"
              :key="item.id"
              class="flex items-center justify-between"
            >
              <div class="flex items-center gap-2">
                <div class="w-2 h-2 rounded-full bg-primary"></div>
                <span
                  >{{ item.featureName }}{{ item.dimension ? ` (${item.dimension})` : '' }}</span
                >
              </div>
              <div class="flex items-center gap-4">
                <span class="text-muted-foreground"
                  >{{ item.quantity.toLocaleString() }} units</span
                >
                <span class="tabular-nums font-medium">${{ item.amount.toLocaleString() }}</span>
              </div>
            </div>
          </div>
        </div>
      </Card>

      <!-- Customer Info -->
      <Card class="p-6 mt-6">
        <h3 class="text-sm font-medium mb-4">Customer Information</h3>
        <div class="grid grid-cols-2 gap-4 text-sm">
          <div>
            <div class="text-muted-foreground mb-1">Customer</div>
            <router-link
              :to="
                isDemo
                  ? `/demo/customers/${invoice.customerId}`
                  : `/customers/${invoice.customerId}`
              "
              class="font-medium text-primary hover:underline"
            >
              {{ invoice.customerName }}
            </router-link>
          </div>
          <div>
            <div class="text-muted-foreground mb-1">Email</div>
            <div class="font-medium">{{ invoice.customerEmail }}</div>
          </div>
          <div>
            <div class="text-muted-foreground mb-1">Plan</div>
            <div class="font-medium">{{ invoice.planName }}</div>
          </div>
          <div>
            <div class="text-muted-foreground mb-1">Invoice Date</div>
            <div class="font-medium">{{ formatDate(invoice.invoiceDate) }}</div>
          </div>
        </div>
      </Card>
    </div>

    <!-- Not Found -->
    <Card v-else class="p-8 text-center">
      <AlertCircle class="h-8 w-8 text-muted-foreground mx-auto mb-4" />
      <h3 class="text-lg font-medium mb-2">Invoice Not Found</h3>
      <p class="text-muted-foreground mb-4">The invoice you're looking for doesn't exist.</p>
      <Button variant="outline" @click="router.push(isDemo ? '/demo/invoices' : '/invoices')">
        Back to Invoices
      </Button>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ChevronRight,
  ChevronDown,
  MoreHorizontal,
  Send,
  CreditCard,
  AlertTriangle,
  AlertCircle
} from 'lucide-vue-next'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import { invoices } from '../data/mockData'

const route = useRoute()
const router = useRouter()

const showUsageDetails = ref(false)

// Check if we're in demo mode
const isDemo = computed(() => route.path.startsWith('/demo'))

const invoice = computed(() => {
  const id = route.params.id as string
  return invoices.find((i) => i.id === id)
})

const usageBasedItems = computed(() => {
  if (!invoice.value) return []
  return invoice.value.lineItems.filter((item) => item.type === 'usage_based')
})

const fixedItems = computed(() => {
  if (!invoice.value) return []
  return invoice.value.lineItems.filter((item) => item.type === 'fixed')
})

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

function formatDateRange(start: string, end: string): string {
  const startDate = new Date(start)
  const endDate = new Date(end)
  const startStr = startDate.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
  const endStr = endDate.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  })
  return `${startStr} - ${endStr}`
}

function formatStatus(status: string): string {
  return status.replace(/_/g, ' ')
}

function formatRate(rate: number): string {
  if (rate < 0.01) {
    return rate.toFixed(4)
  } else if (rate < 1) {
    return rate.toFixed(3)
  }
  return rate.toFixed(2)
}

function getStatusColor(status: string): string {
  switch (status) {
    case 'draft':
      return 'bg-gray-100 text-gray-700'
    case 'pending_issue':
      return 'bg-yellow-100 text-yellow-700'
    case 'issued':
      return 'bg-blue-100 text-blue-700'
    case 'paid':
      return 'bg-green-100 text-green-700'
    case 'past_due':
      return 'bg-red-100 text-red-700'
    case 'payment_failed':
      return 'bg-red-100 text-red-700'
    case 'voided':
      return 'bg-gray-100 text-gray-500'
    default:
      return 'bg-gray-100 text-gray-700'
  }
}
</script>
