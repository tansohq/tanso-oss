<template>
  <div class="p-6 pb-16">
    <!-- Breadcrumb -->
    <div class="mb-6">
      <nav class="flex items-center gap-2 text-sm text-muted-foreground">
        <router-link :to="demoPath('/invoices')" class="hover:text-foreground transition-colors">Invoices</router-link>
        <ChevronRight class="h-4 w-4" />
        <span class="text-foreground">{{ invoice?.id || 'Invoice' }}</span>
      </nav>
    </div>

    <!-- Loading State -->
    <div v-if="isLoading" class="flex items-center justify-center py-12">
      <Loader2 class="h-8 w-8 animate-spin text-muted-foreground" />
    </div>

    <!-- Error State -->
    <div v-else-if="isError" class="flex flex-col items-center justify-center py-12 bg-card rounded-lg border">
      <AlertCircle class="w-12 h-12 text-destructive mb-4" />
      <p class="text-lg font-medium text-foreground mb-2">Unable to load invoice</p>
      <p class="text-sm text-muted-foreground mb-4">The invoice could not be found or an error occurred.</p>
      <div class="flex gap-2">
        <Button variant="outline" @click="refetch">
          <RefreshCw class="w-4 h-4 mr-2" />
          Try Again
        </Button>
        <Button variant="outline" @click="router.push(demoPath('/invoices'))">
          <ArrowLeft class="w-4 h-4 mr-2" />
          Back to Invoices
        </Button>
      </div>
    </div>

    <!-- Not Found -->
    <div v-else-if="!invoice" class="flex flex-col items-center justify-center py-12 bg-card rounded-lg border">
      <AlertCircle class="w-8 h-8 text-muted-foreground mb-4" />
      <h3 class="text-lg font-medium mb-2">Invoice Not Found</h3>
      <p class="text-muted-foreground mb-4">The invoice you're looking for doesn't exist.</p>
      <Button variant="outline" @click="router.push(demoPath('/invoices'))">
        Back to Invoices
      </Button>
    </div>

    <div v-else class="space-y-6 max-w-5xl">
      <!-- Header -->
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-4">
          <h1 class="text-2xl font-semibold text-foreground font-mono">{{ invoice.id }}</h1>
          <CopyButton :value="invoice.id" label="Invoice ID" />
          <Badge :class="getInvoiceStatusClasses(invoice.status)" class="shadow-none">
            {{ formatInvoiceStatus(invoice.status) }}
          </Badge>
          <span v-if="daysOverdue > 0" class="text-xs font-medium text-red-600">
            {{ daysOverdue }} {{ daysOverdue === 1 ? 'day' : 'days' }} overdue
          </span>
        </div>
      </div>

      <!-- Pending/Due banner -->
      <div
        v-if="invoice.status === 'PENDING' || invoice.status === 'DUE'"
        class="flex items-start gap-2 p-3 rounded-lg bg-amber-50 text-amber-800 text-sm"
      >
        <AlertTriangle class="h-4 w-4 mt-0.5 shrink-0" />
        <span>Usage-based charges may not yet be final and are updated as the billing period progresses.</span>
      </div>

      <!-- Summary Card -->
      <Card class="p-6">
        <div class="flex items-start justify-between mb-4">
          <div>
            <div class="text-sm text-muted-foreground mb-1">Total amount</div>
            <div class="text-4xl font-semibold tabular-nums">
              {{ formatAmount(invoice.amount, invoice.currency) }}
            </div>
          </div>
          <div class="text-right text-sm text-muted-foreground">
            <router-link
              v-if="invoice.subscription?.customer?.id"
              :to="demoPath(`/customers/${invoice.subscription.customer.id}`)"
              class="font-medium text-primary hover:underline"
            >
              {{ customerName }}
            </router-link>
            <div v-else>{{ customerName }}</div>
            <div v-if="planName">{{ planName }}</div>
          </div>
        </div>
        <div class="flex items-center justify-between text-sm text-muted-foreground mt-4 pt-4 border-t">
          <span v-if="invoice.subscription?.currentPeriodStart && invoice.subscription?.currentPeriodEnd">
            Billing period: {{ formatDateRange(invoice.subscription.currentPeriodStart, invoice.subscription.currentPeriodEnd) }}
          </span>
          <span v-else>Billing period: —</span>
          <span>Due: {{ formatDate(invoice.dueDate) }}</span>
        </div>
      </Card>

      <!-- Line Items Card -->
      <Card class="p-6">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-base font-medium">{{ planName || 'Line Items' }}</h3>
          <router-link
            v-if="invoice.subscription?.id"
            :to="demoPath(`/subscriptions/${invoice.subscription.id}`)"
            class="text-sm text-primary hover:underline"
          >
            View subscription
          </router-link>
        </div>
        <div v-if="invoice.items && invoice.items.length > 0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Description</TableHead>
                <TableHead class="text-right">Amount</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="item in invoice.items" :key="item.id">
                <TableCell class="font-medium">{{ item.description || '—' }}</TableCell>
                <TableCell class="text-right tabular-nums">{{ formatAmount(item.chargeAmount, invoice.currency) }}</TableCell>
              </TableRow>
            </TableBody>
          </Table>
          <div class="border-t pt-4 mt-2">
            <div class="flex justify-between font-semibold">
              <span>Total</span>
              <span class="tabular-nums">{{ formatAmount(invoice.amount, invoice.currency) }}</span>
            </div>
          </div>
        </div>
        <div v-else class="text-sm text-muted-foreground py-4">
          No line items for this invoice.
        </div>
      </Card>

      <!-- Details Card -->
      <Card class="p-6">
        <h3 class="text-base font-medium mb-4">Details</h3>
        <div class="grid grid-cols-2 gap-4 text-sm">
          <div>
            <div class="text-muted-foreground mb-1">Customer</div>
            <router-link
              v-if="invoice.subscription?.customer?.id"
              :to="demoPath(`/customers/${invoice.subscription.customer.id}`)"
              class="font-medium text-primary hover:underline"
            >
              {{ invoice.subscription.customer.firstName }} {{ invoice.subscription.customer.lastName }}
            </router-link>
            <div v-else class="font-medium">—</div>
          </div>

          <div>
            <div class="text-muted-foreground mb-1">Plan</div>
            <router-link
              v-if="invoice.subscription?.plan?.id"
              :to="demoPath(`/plans/${invoice.subscription.plan.id}`)"
              class="font-medium text-primary hover:underline"
            >
              {{ invoice.subscription.plan.name }}
            </router-link>
            <div v-else class="font-medium">—</div>
          </div>

          <div>
            <div class="text-muted-foreground mb-1">Due Date</div>
            <div class="font-medium tabular-nums">{{ formatDate(invoice.dueDate) }}</div>
          </div>

          <div>
            <div class="text-muted-foreground mb-1">Status</div>
            <Badge :class="getInvoiceStatusClasses(invoice.status)" class="shadow-none">
              {{ formatInvoiceStatus(invoice.status) }}
            </Badge>
          </div>

          <div>
            <div class="text-muted-foreground mb-1">Amount</div>
            <div class="font-medium tabular-nums">{{ formatAmount(invoice.amount, invoice.currency) }}</div>
          </div>

          <div>
            <div class="text-muted-foreground mb-1">Currency</div>
            <div class="font-medium">{{ invoice.currency || '—' }}</div>
          </div>

          <div>
            <div class="text-muted-foreground mb-1">Plan Price / Interval</div>
            <div class="font-medium">
              {{ formatPrice(invoice.subscription?.plan?.priceAmount) }} / {{ formatInterval(invoice.subscription?.plan?.intervalMonths) }}
            </div>
          </div>

          <div>
            <div class="text-muted-foreground mb-1">Billing Timing</div>
            <div class="font-medium">{{ formatBillingTiming(invoice.subscription?.plan?.billingTiming) }}</div>
          </div>

          <div>
            <div class="text-muted-foreground mb-1">Subscription Status</div>
            <Badge :class="invoice.subscription?.isActive ? 'bg-emerald-50 text-emerald-700 border border-emerald-200/50' : 'bg-gray-50 text-gray-600 border border-gray-200/50'" class="shadow-none">
              {{ invoice.subscription?.isActive ? 'Active' : 'Inactive' }}
            </Badge>
          </div>

          <div>
            <div class="text-muted-foreground mb-1">Created</div>
            <div class="font-medium tabular-nums">{{ formatDate(invoice.createdAt) }}</div>
          </div>
        </div>
      </Card>

      <!-- Additional Details (standalone collapsible) -->
      <Collapsible v-if="hasMetadata || invoice.modifiedAt" v-model:open="showAdditionalDetails">
        <CollapsibleTrigger class="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors">
          <ChevronRight class="h-4 w-4 transition-transform" :class="{ 'rotate-90': showAdditionalDetails }" />
          Additional Details
        </CollapsibleTrigger>
        <CollapsibleContent class="mt-4">
          <Card class="p-6">
            <div class="space-y-4">
              <div v-if="hasMetadata" class="flex flex-col gap-1">
                <Label class="text-muted-foreground text-sm">Metadata</Label>
                <pre class="bg-muted p-4 rounded-lg text-xs font-mono overflow-x-auto">{{ formatJson(invoice.metadata) }}</pre>
              </div>
              <div v-if="invoice.modifiedAt" :class="{ 'border-t pt-4 mt-4': hasMetadata }" class="flex flex-col gap-1">
                <Label class="text-muted-foreground text-sm">Modified At</Label>
                <p class="text-sm tabular-nums">{{ formatDateTime(invoice.modifiedAt) }}</p>
              </div>
            </div>
          </Card>
        </CollapsibleContent>
      </Collapsible>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ChevronRight,
  AlertCircle,
  AlertTriangle,
  RefreshCw,
  ArrowLeft,
  Loader2
} from 'lucide-vue-next'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import {
  Collapsible,
  CollapsibleTrigger,
  CollapsibleContent
} from '@/components/ui/collapsible'
import {
  formatAmount,
  formatPrice,
  formatInterval,
  formatDate,
  formatDateTime,
  formatDateRange,
  formatInvoiceStatus,
  getInvoiceStatusClasses
} from '@/lib/formatters'
import CopyButton from '@/components/CopyButton.vue'
import { useInvoiceDetailQuery } from '../queries'
import { useDemoPrefix } from '@/lib/useDemoPrefix'

const route = useRoute()
const router = useRouter()
const { demoPath, isDemo } = useDemoPrefix()

const invoiceId = computed(() => route.params.id as string)

const { data: invoiceData, isLoading, isError, refetch } = isDemo.value
  ? { data: ref(null), isLoading: ref(false), isError: ref(false), refetch: () => {} }
  : useInvoiceDetailQuery(invoiceId)

const demoInvSub = (subId: string, custId: string, firstName: string, lastName: string, email: string, planId: string, planName: string, priceAmount: number) => ({
  id: subId,
  isActive: true,
  currentPeriodStart: '2026-03-01T00:00:00Z',
  currentPeriodEnd: '2026-03-31T23:59:59Z',
  customer: { id: custId, firstName, lastName, email },
  plan: { id: planId, name: planName, priceAmount, intervalMonths: '1', billingTiming: 'IN_ADVANCE' }
})

const DEMO_INVOICES = [
  { id: '66666666-0000-4aaa-bbbb-cccccccccccc', amount: 199, dueDate: '2026-03-15T12:00:00Z', currency: 'USD', status: 'PAID', createdAt: '2026-03-05T12:00:00Z', modifiedAt: '2026-03-10T12:00:00Z', metadata: null, items: [], subscription: demoInvSub('11111111-aaaa-4bbb-cccc-dddddddddddd', 'a1b2c3d4-e5f6-4789-abcd-ef0123456789', 'Sarah', 'Chen', 'sarah@outbound.io', 'c2b3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e', 'Growth', 199) },
  { id: '66666666-0001-4aaa-bbbb-cccccccccccc', amount: 699, dueDate: '2026-03-15T12:00:00Z', currency: 'USD', status: 'PAID', createdAt: '2026-03-05T12:00:00Z', modifiedAt: '2026-03-10T12:00:00Z', metadata: null, items: [], subscription: demoInvSub('22222222-bbbb-4ccc-dddd-eeeeeeeeeeee', 'b2c3d4e5-f6a7-4890-bcde-f01234567890', 'Marcus', 'Rodriguez', 'marcus@pipelineai.com', 'd3c4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f', 'Enterprise', 699) },
  { id: '66666666-0002-4aaa-bbbb-cccccccccccc', amount: 99, dueDate: '2026-03-15T12:00:00Z', currency: 'USD', status: 'PENDING', createdAt: '2026-03-05T12:00:00Z', modifiedAt: '2026-03-05T12:00:00Z', metadata: null, items: [], subscription: demoInvSub('33333333-cccc-4ddd-eeee-ffffffffffff', 'c3d4e5f6-a7b8-4901-cdef-012345678901', 'Emily', 'Park', 'emily@prospectlab.io', 'b1a2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', 'Starter', 99) },
  { id: '66666666-0003-4aaa-bbbb-cccccccccccc', amount: 199, dueDate: '2026-03-15T12:00:00Z', currency: 'USD', status: 'PENDING', createdAt: '2026-03-05T12:00:00Z', modifiedAt: '2026-03-05T12:00:00Z', metadata: null, items: [], subscription: demoInvSub('44444444-dddd-4eee-ffff-aaaaaaaaaaaa', 'd4e5f6a7-b8c9-4012-def0-123456789012', 'James', 'Mitchell', 'james@dealflow.co', 'c2b3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e', 'Growth', 199) },
  { id: '66666666-0004-4aaa-bbbb-cccccccccccc', amount: 699, dueDate: '2026-03-15T12:00:00Z', currency: 'USD', status: 'PAID', createdAt: '2026-03-05T12:00:00Z', modifiedAt: '2026-03-10T12:00:00Z', metadata: null, items: [], subscription: demoInvSub('55555555-eeee-4fff-aaaa-bbbbbbbbbbbb', 'e5f6a7b8-c9d0-4123-ef01-234567890123', 'Anika', 'Patel', 'anika@salesforge.dev', 'd3c4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f', 'Enterprise', 699) },
]

const invoice = computed(() => {
  if (isDemo.value) {
    const id = route.params.id as string
    return DEMO_INVOICES.find((inv) => inv.id === id) ?? DEMO_INVOICES[0]
  }
  return invoiceData.value?.data ?? null
})

const showAdditionalDetails = ref(false)

const customerName = computed(() => {
  if (!invoice.value?.subscription?.customer) return 'Invoice'
  const c = invoice.value.subscription.customer
  return `${c.firstName} ${c.lastName}`
})

const planName = computed(() => {
  return invoice.value?.subscription?.plan?.name || null
})

const daysOverdue = computed(() => {
  if (!invoice.value || invoice.value.status !== 'PAST_DUE' || !invoice.value.dueDate) return 0
  const due = new Date(invoice.value.dueDate)
  const now = new Date()
  const diffMs = now.getTime() - due.getTime()
  return Math.max(0, Math.floor(diffMs / (1000 * 60 * 60 * 24)))
})

const hasMetadata = computed(() => {
  const metadata = invoice.value?.metadata
  return metadata && typeof metadata === 'object' && Object.keys(metadata).length > 0
})

function formatBillingTiming(timing: string | null | undefined): string {
  if (!timing) return '—'
  switch (timing) {
    case 'IN_ADVANCE': return 'In advance'
    case 'IN_ARREARS': return 'In arrears'
    default: return timing
  }
}

function formatJson(data: Record<string, unknown> | null | undefined): string {
  if (!data) return '{}'
  return JSON.stringify(data, null, 2)
}
</script>
