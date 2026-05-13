<template>
  <div class="p-6 pb-16">
    <!-- Breadcrumb -->
    <div class="flex items-center gap-2 text-sm text-muted-foreground mb-6">
      <router-link
        :to="isDemo ? '/demo/subscriptions' : '/subscriptions'"
        class="hover:text-foreground"
        >Subscriptions</router-link
      >
      <ChevronRight class="h-4 w-4" />
      <span class="text-foreground">{{ subscription?.customerName || 'Subscription' }}</span>
    </div>

    <div v-if="subscription">
      <!-- Header -->
      <div class="flex items-center justify-between mb-6">
        <div>
          <div class="flex items-center gap-3">
            <h1 class="text-2xl font-semibold text-foreground">{{ subscription.customerName }}</h1>
            <Badge :class="getStatusColor(subscription.status)" class="border-0 capitalize">
              {{ subscription.status }}
            </Badge>
          </div>
          <p class="text-muted-foreground mt-1 font-mono text-sm">{{ subscription.id }}</p>
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="outline">
              Actions
              <ChevronDown class="h-4 w-4 ml-2" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem
              v-if="subscription.status === 'active'"
              @click="showPauseDialog = true"
            >
              <Pause class="h-4 w-4 mr-2" />
              Pause Subscription
            </DropdownMenuItem>
            <DropdownMenuItem
              v-if="subscription.status === 'paused'"
              @click="showResumeDialog = true"
            >
              <Play class="h-4 w-4 mr-2" />
              Resume Subscription
            </DropdownMenuItem>
            <DropdownMenuItem @click="showChangePlanModal = true">
              <RefreshCw class="h-4 w-4 mr-2" />
              Change Plan
            </DropdownMenuItem>
            <DropdownMenuItem @click="showEditBillingModal = true">
              <Settings class="h-4 w-4 mr-2" />
              Edit Billing
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              v-if="subscription.status !== 'canceled'"
              class="text-red-600"
              @click="showCancelDialog = true"
            >
              <XCircle class="h-4 w-4 mr-2" />
              Cancel Subscription
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      <!-- Overview Cards -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
        <!-- Subscription Details -->
        <Card class="p-6">
          <h3 class="text-sm font-medium mb-4">Subscription Details</h3>
          <div class="grid grid-cols-2 gap-4 text-sm">
            <div>
              <div class="text-muted-foreground mb-1">Plan</div>
              <router-link
                :to="
                  isDemo ? `/demo/plans/${subscription.planId}` : `/plans/${subscription.planId}`
                "
                class="font-medium text-primary hover:underline"
              >
                {{ subscription.planName }}
              </router-link>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Version</div>
              <div class="font-medium">v{{ subscription.version }}</div>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Billing Cycle</div>
              <div class="font-medium capitalize">{{ subscription.billingCycle }}</div>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Start Date</div>
              <div class="font-medium">{{ formatDate(subscription.startDate) }}</div>
            </div>
            <div v-if="subscription.endDate">
              <div class="text-muted-foreground mb-1">End Date</div>
              <div class="font-medium">{{ formatDate(subscription.endDate) }}</div>
            </div>
          </div>
        </Card>

        <!-- Billing Information -->
        <Card class="p-6">
          <h3 class="text-sm font-medium mb-4">Billing Information</h3>
          <div class="grid grid-cols-2 gap-4 text-sm">
            <div>
              <div class="text-muted-foreground mb-1">Current Period</div>
              <div class="font-medium">
                {{ formatDate(subscription.currentPeriodStart) }} -
                {{ formatDate(subscription.currentPeriodEnd) }}
              </div>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Next Invoice</div>
              <div class="font-medium">{{ formatDate(subscription.nextInvoiceDate) }}</div>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Customer Email</div>
              <div class="font-medium">{{ subscription.customerEmail || '-' }}</div>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Customer</div>
              <router-link
                :to="
                  isDemo
                    ? `/demo/customers/${subscription.customerId}`
                    : `/customers/${subscription.customerId}`
                "
                class="font-medium text-primary hover:underline"
              >
                View Profile
              </router-link>
            </div>
          </div>
        </Card>
      </div>

      <!-- Pricing Overrides Section -->
      <Card class="p-6 mb-6">
        <div class="flex items-center justify-between mb-4">
          <div class="flex items-center gap-2">
            <h3 class="text-sm font-medium">Custom Pricing Overrides</h3>
            <Badge
              v-if="subscription.overrides?.length"
              class="bg-purple-100 text-purple-700 border-0"
            >
              {{ subscription.overrides.length }} overrides
            </Badge>
          </div>
          <Button size="sm" variant="outline" @click="openAddOverrideModal">
            <Plus class="h-4 w-4 mr-2" />
            Add Override
          </Button>
        </div>
        <Table v-if="subscription.overrides?.length">
          <TableHeader>
            <TableRow>
              <TableHead>Feature</TableHead>
              <TableHead>Original Price</TableHead>
              <TableHead>Override Price</TableHead>
              <TableHead>Discount</TableHead>
              <TableHead>Reason</TableHead>
              <TableHead class="w-[100px]">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="override in subscription.overrides" :key="override.featureId">
              <TableCell class="font-medium">{{ override.featureName }}</TableCell>
              <TableCell class="tabular-nums">${{ override.originalPrice.toFixed(4) }}</TableCell>
              <TableCell class="tabular-nums text-green-600"
                >${{ override.overridePrice.toFixed(4) }}</TableCell
              >
              <TableCell>
                <Badge class="bg-green-100 text-green-700 border-0">
                  -{{ ((1 - override.overridePrice / override.originalPrice) * 100).toFixed(0) }}%
                </Badge>
              </TableCell>
              <TableCell class="text-muted-foreground">{{ override.reason || '-' }}</TableCell>
              <TableCell>
                <div class="flex items-center gap-1">
                  <Button size="sm" variant="ghost" @click="openEditOverrideModal(override)">
                    <Pencil class="h-4 w-4" />
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    class="text-red-600 hover:text-red-700"
                    @click="handleRemoveOverride(override)"
                  >
                    <Trash2 class="h-4 w-4" />
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
        <div v-else class="text-sm text-muted-foreground text-center py-4">
          No pricing overrides. Standard plan pricing applies.
        </div>
      </Card>

      <!-- Fixed Fee Quantities Section -->
      <Card v-if="planFixedFees.length > 0" class="p-6 mb-6">
        <div class="flex items-center justify-between mb-4">
          <div class="flex items-center gap-2">
            <h3 class="text-sm font-medium">Fixed Fee Quantities</h3>
            <Badge
              v-if="subscription.fixedFeeQuantities?.length"
              class="bg-blue-100 text-blue-700 border-0"
            >
              {{ subscription.fixedFeeQuantities.length }} customized
            </Badge>
          </div>
          <Button size="sm" variant="outline" @click="openAddFixedFeeModal">
            <Plus class="h-4 w-4 mr-2" />
            Update Quantity
          </Button>
        </div>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Component</TableHead>
              <TableHead>Plan Default</TableHead>
              <TableHead>Quantity</TableHead>
              <TableHead>Unit Price</TableHead>
              <TableHead>Total</TableHead>
              <TableHead class="w-[100px]">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="fee in fixedFeesWithQuantities" :key="fee.id">
              <TableCell class="font-medium">{{ fee.component }}</TableCell>
              <TableCell class="tabular-nums text-muted-foreground">{{
                fee.planQuantity
              }}</TableCell>
              <TableCell>
                <div class="flex items-center gap-2">
                  <span class="tabular-nums">{{ fee.quantity }}</span>
                  <Badge v-if="fee.isCustom" class="bg-blue-100 text-blue-700 border-0 text-xs"
                    >Custom</Badge
                  >
                </div>
              </TableCell>
              <TableCell class="tabular-nums">${{ fee.amount.toFixed(2) }}</TableCell>
              <TableCell class="tabular-nums font-medium"
                >${{ (fee.amount * fee.quantity).toFixed(2) }}</TableCell
              >
              <TableCell>
                <div class="flex items-center gap-1">
                  <Button size="sm" variant="ghost" @click="openEditFixedFeeModal(fee)">
                    <Pencil class="h-4 w-4" />
                  </Button>
                  <Button
                    v-if="fee.isCustom"
                    size="sm"
                    variant="ghost"
                    class="text-red-600 hover:text-red-700"
                    @click="handleRemoveFixedFee(fee)"
                  >
                    <Trash2 class="h-4 w-4" />
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </Card>

      <!-- Recent Invoices -->
      <Card class="p-6 mb-6">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-sm font-medium">Recent Invoices</h3>
          <router-link
            :to="{
              path: isDemo ? '/demo/invoices' : '/invoices',
              query: { customer: subscription?.customerId }
            }"
            class="text-sm text-primary hover:underline"
          >
            View All
          </router-link>
        </div>
        <Table v-if="customerInvoices.length > 0">
          <TableHeader>
            <TableRow>
              <TableHead>Invoice</TableHead>
              <TableHead>Date</TableHead>
              <TableHead>Period</TableHead>
              <TableHead>Amount</TableHead>
              <TableHead>Status</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="invoice in customerInvoices"
              :key="invoice.id"
              class="cursor-pointer hover:bg-muted/50"
              @click="navigateToInvoice(invoice.id)"
            >
              <TableCell class="font-mono">{{ invoice.id }}</TableCell>
              <TableCell>{{ formatDate(invoice.invoiceDate) }}</TableCell>
              <TableCell class="text-muted-foreground">
                {{ formatDate(invoice.billingPeriodStart) }} -
                {{ formatDate(invoice.billingPeriodEnd) }}
              </TableCell>
              <TableCell class="tabular-nums font-medium"
                >${{ invoice.amount.toLocaleString() }}</TableCell
              >
              <TableCell>
                <Badge :class="getInvoiceStatusColor(invoice.status)" class="border-0 capitalize">
                  {{ invoice.status.replace('_', ' ') }}
                </Badge>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
        <div v-else class="text-sm text-muted-foreground text-center py-4">
          No invoices yet for this subscription
        </div>
      </Card>

      <!-- Activity Log -->
      <Card class="p-6">
        <h3 class="text-sm font-medium mb-4">Activity</h3>
        <div v-if="subscription.activity?.length" class="space-y-3">
          <div
            v-for="(activity, i) in subscription.activity.slice().reverse()"
            :key="i"
            class="flex items-start gap-3 text-sm"
          >
            <div class="w-2 h-2 rounded-full bg-muted-foreground mt-1.5 shrink-0"></div>
            <div>
              <span class="font-medium">{{ activity.action }}</span>
              <span v-if="activity.details" class="text-muted-foreground">
                - {{ activity.details }}</span
              >
              <div class="text-xs text-muted-foreground mt-0.5">
                {{ formatDate(activity.date) }}
              </div>
            </div>
          </div>
        </div>
        <div v-else class="text-sm text-muted-foreground text-center py-4">
          No activity recorded
        </div>
      </Card>
    </div>

    <!-- Not Found -->
    <Card v-else class="p-8 text-center">
      <AlertCircle class="h-8 w-8 text-muted-foreground mx-auto mb-4" />
      <h3 class="text-lg font-medium mb-2">Subscription Not Found</h3>
      <p class="text-muted-foreground mb-4">The subscription you're looking for doesn't exist.</p>
      <Button
        variant="outline"
        @click="router.push(isDemo ? '/demo/subscriptions' : '/subscriptions')"
      >
        Back to Subscriptions
      </Button>
    </Card>

    <!-- Dialogs and Modals -->
    <DemoPauseSubscriptionDialog
      v-model:visible="showPauseDialog"
      :subscription-id="subscriptionId"
    />

    <DemoResumeSubscriptionDialog
      v-model:visible="showResumeDialog"
      :subscription-id="subscriptionId"
    />

    <DemoCancelSubscriptionDialog
      v-model:visible="showCancelDialog"
      :subscription-id="subscriptionId"
    />

    <DemoChangePlanModal v-model:visible="showChangePlanModal" :subscription-id="subscriptionId" />

    <DemoEditBillingModal
      v-model:visible="showEditBillingModal"
      :subscription-id="subscriptionId"
    />

    <DemoAddOverrideModal
      v-model:visible="showAddOverrideModal"
      :subscription-id="subscriptionId"
      :editing-override="editingOverride"
    />

    <DemoUpdateFixedFeeModal
      v-model:visible="showFixedFeeModal"
      :subscription-id="subscriptionId"
      :editing-fee="editingFixedFee"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ChevronRight,
  ChevronDown,
  AlertCircle,
  Pause,
  Play,
  RefreshCw,
  XCircle,
  Settings,
  Plus,
  Pencil,
  Trash2
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu'
import { invoices } from '../data/mockData'
import { useDemoState } from '../composables/useDemoState'
import DemoPauseSubscriptionDialog from '../components/subscriptions/DemoPauseSubscriptionDialog.vue'
import DemoResumeSubscriptionDialog from '../components/subscriptions/DemoResumeSubscriptionDialog.vue'
import DemoCancelSubscriptionDialog from '../components/subscriptions/DemoCancelSubscriptionDialog.vue'
import DemoChangePlanModal from '../components/subscriptions/DemoChangePlanModal.vue'
import DemoEditBillingModal from '../components/subscriptions/DemoEditBillingModal.vue'
import DemoAddOverrideModal from '../components/subscriptions/DemoAddOverrideModal.vue'
import DemoUpdateFixedFeeModal from '../components/subscriptions/DemoUpdateFixedFeeModal.vue'
import { toast } from '@/components/ui/toast/use-toast'
import type { SubscriptionOverride, FixedFeeQuantity } from '../types'

const route = useRoute()
const router = useRouter()

const { subscriptionsData, pricingPlansData, removeSubscriptionOverride, removeFixedFeeQuantity } =
  useDemoState()

const isDemo = computed(() => route.path.startsWith('/demo'))

const subscriptionId = computed(() => route.params.id as string)

const subscription = computed(() => {
  return subscriptionsData.value.find((s) => s.id === subscriptionId.value) ?? null
})

const subscriptionPlan = computed(() => {
  if (!subscription.value) return null
  return pricingPlansData.value.find((p) => p.id === subscription.value!.planId) ?? null
})

const subscriptionVersion = computed(() => {
  if (!subscriptionPlan.value || !subscription.value) return null
  return (
    subscriptionPlan.value.versions.find((v) => v.version === subscription.value!.version) ?? null
  )
})

const planFixedFees = computed(() => {
  return subscriptionVersion.value?.fixedFees || []
})

// Merge plan fixed fees with any custom quantities
const fixedFeesWithQuantities = computed(() => {
  const customQuantities = subscription.value?.fixedFeeQuantities || []

  return planFixedFees.value.map((fee) => {
    const customQty = customQuantities.find((cq) => cq.feeId === fee.id)
    return {
      id: fee.id,
      component: fee.component,
      amount: fee.amount,
      planQuantity: fee.quantity,
      quantity: customQty?.quantity ?? fee.quantity,
      isCustom: !!customQty
    }
  })
})

const customerInvoices = computed(() => {
  if (!subscription.value) return []
  return invoices.filter((inv) => inv.customerId === subscription.value!.customerId).slice(0, 5)
})

// Dialog/Modal visibility states
const showPauseDialog = ref(false)
const showResumeDialog = ref(false)
const showCancelDialog = ref(false)
const showChangePlanModal = ref(false)
const showEditBillingModal = ref(false)
const showAddOverrideModal = ref(false)
const showFixedFeeModal = ref(false)

// Editing states
const editingOverride = ref<SubscriptionOverride | null>(null)
const editingFixedFee = ref<FixedFeeQuantity | null>(null)

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

function getStatusColor(status: string): string {
  switch (status) {
    case 'active':
      return 'bg-green-100 text-green-700'
    case 'canceled':
      return 'bg-red-100 text-red-700'
    case 'paused':
      return 'bg-yellow-100 text-yellow-700'
    default:
      return 'bg-gray-100 text-gray-700'
  }
}

function getInvoiceStatusColor(status: string): string {
  switch (status) {
    case 'paid':
      return 'bg-green-100 text-green-700'
    case 'issued':
      return 'bg-blue-100 text-blue-700'
    case 'draft':
      return 'bg-gray-100 text-gray-700'
    case 'pending_issue':
      return 'bg-yellow-100 text-yellow-700'
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

function navigateToInvoice(id: string) {
  router.push({
    name: isDemo.value ? 'demo-invoice-detail' : 'invoice-detail',
    params: { id }
  })
}

// Override management
function openAddOverrideModal() {
  editingOverride.value = null
  showAddOverrideModal.value = true
}

function openEditOverrideModal(override: SubscriptionOverride) {
  editingOverride.value = override
  showAddOverrideModal.value = true
}

function handleRemoveOverride(override: SubscriptionOverride) {
  if (!subscription.value) return
  removeSubscriptionOverride(subscription.value.id, override.featureId)
  toast({
    title: 'Override removed',
    description: `Custom pricing for ${override.featureName} has been removed.`
  })
}

// Fixed fee management
function openAddFixedFeeModal() {
  editingFixedFee.value = null
  showFixedFeeModal.value = true
}

function openEditFixedFeeModal(fee: {
  id: string
  component: string
  planQuantity: number
  quantity: number
}) {
  editingFixedFee.value = {
    feeId: fee.id,
    component: fee.component,
    originalQuantity: fee.planQuantity,
    quantity: fee.quantity
  }
  showFixedFeeModal.value = true
}

function handleRemoveFixedFee(fee: { id: string; component: string }) {
  if (!subscription.value) return
  removeFixedFeeQuantity(subscription.value.id, fee.id)
  toast({
    title: 'Custom quantity removed',
    description: `${fee.component} will use the plan default quantity.`
  })
}
</script>
