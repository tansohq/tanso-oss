<template>
  <div class="p-6 pb-16">
    <!-- Breadcrumb -->
    <div class="mb-6">
      <nav class="flex items-center gap-2 text-sm text-muted-foreground">
        <router-link :to="demoPath('/subscriptions')" class="hover:text-foreground transition-colors">Subscriptions</router-link>
        <ChevronRight class="h-4 w-4" />
        <span class="text-foreground">{{ customerFullName || 'Subscription' }}</span>
      </nav>
    </div>

    <!-- Loading State -->
    <div v-if="isLoading" class="flex items-center justify-center py-12">
      <Loader2 class="h-8 w-8 animate-spin text-muted-foreground" />
    </div>

    <!-- Error State -->
    <div v-else-if="isError" class="flex flex-col items-center justify-center py-12 bg-card rounded-lg border">
      <AlertCircle class="w-12 h-12 text-destructive mb-4" />
      <p class="text-lg font-medium text-foreground mb-2">Unable to load subscription</p>
      <p class="text-sm text-muted-foreground mb-4">The subscription could not be found or an error occurred.</p>
      <div class="flex gap-2">
        <Button variant="outline" @click="refetch">
          <RefreshCw class="w-4 h-4 mr-2" />
          Try Again
        </Button>
        <Button variant="outline" @click="router.push(demoPath('/subscriptions'))">
          <ArrowLeft class="w-4 h-4 mr-2" />
          Back to Subscriptions
        </Button>
      </div>
    </div>

    <div v-else-if="subscription" class="space-y-6 max-w-5xl">
      <!-- Header -->
      <div class="flex items-center justify-between">
        <div>
          <div class="flex items-center gap-3">
            <h1 class="text-2xl font-semibold tracking-tight text-foreground">{{ customerFullName }}</h1>
            <Badge :class="getSubscriptionStatusClasses(subscription)" class="shadow-none">
              {{ formatSubscriptionStatus(subscription) }}
            </Badge>
          </div>
          <div class="flex items-center gap-2 mt-1">
            <p class="text-sm font-mono text-muted-foreground">{{ subscription.id }}</p>
            <CopyButton :value="subscription.id" label="Subscription ID" />
          </div>
        </div>
        <div class="flex items-center gap-2">
          <Button
            v-if="!subscription.isActive && !subscription.cancelledAt"
            @click="handleActivate"
            :disabled="isActivating"
          >
            <Loader2 v-if="isActivating" class="h-4 w-4 mr-2 animate-spin" />
            Activate Subscription
          </Button>
          <DropdownMenu v-if="subscription.isActive">
            <DropdownMenuTrigger as-child>
              <Button variant="outline">
                Actions
                <ChevronDown class="h-4 w-4 ml-2" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem
                v-if="canUndoCancellation"
                @click="handleUndoCancellation"
                :disabled="isUndoing"
              >
                Undo Cancellation
              </DropdownMenuItem>
              <DropdownMenuItem
                v-if="!subscription.cancelledAt"
                class="text-destructive focus:text-destructive"
                @click="showCancelDialog = true"
              >
                Cancel Subscription
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      <!-- Overview Cards -->
      <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
        <!-- Subscription Details -->
        <Card class="p-6">
          <h3 class="text-base font-medium mb-4">Subscription Details</h3>
          <div class="grid grid-cols-2 gap-4 text-sm">
            <div>
              <div class="text-muted-foreground mb-1">Plan</div>
              <router-link
                :to="demoPath(`/plans/${subscription.plan.id}`)"
                class="font-medium text-primary hover:underline"
              >
                {{ subscription.plan.name }}
              </router-link>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Status</div>
              <div class="font-medium">{{ formatSubscriptionStatus(subscription) }}</div>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Billing Cycle</div>
              <div class="font-medium">{{ formatInterval(subscription.intervalMonths) }}</div>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Grace Period</div>
              <div class="font-medium">{{ subscription.gracePeriodDays }} days</div>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Price</div>
              <div class="font-medium">
                {{ formatPrice(subscription.plan.priceAmount) }}/{{ formatIntervalShort(subscription.plan.intervalMonths) }}
                <span v-if="hasUsageFeatures" class="text-muted-foreground font-normal"> + usage</span>
              </div>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Anchor Day</div>
              <div class="font-medium">Day {{ subscription.billingAnchorDay }}</div>
            </div>
          </div>
        </Card>

        <!-- Billing Information -->
        <Card class="p-6">
          <h3 class="text-base font-medium mb-4">Billing Information</h3>
          <div class="grid grid-cols-2 gap-4 text-sm">
            <div class="col-span-2">
              <div class="text-muted-foreground mb-1">Current Period</div>
              <div class="font-medium tabular-nums">
                {{ formatDate(subscription.currentPeriodStart) }} – {{ formatDate(subscription.currentPeriodEnd) }}
              </div>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Customer</div>
              <router-link
                :to="demoPath(`/customers/${subscription.customer.id}`)"
                class="font-medium text-primary hover:underline"
              >
                {{ customerFullName }}
              </router-link>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Email</div>
              <div class="font-medium">{{ subscription.customer.email }}</div>
            </div>
            <div class="col-span-2">
              <router-link
                :to="{ path: demoPath('/invoices'), query: { search: customerFullName } }"
                class="inline-flex items-center gap-1 text-sm text-primary hover:underline"
              >
                View {{ subscription.customer.firstName }}'s invoices
                <ArrowRight class="h-3 w-3" />
              </router-link>
            </div>
          </div>
        </Card>
      </div>

      <!-- Current Period Usage -->
      <UsageCard
        :subscription-id="subscription.id"
        :plan-id="subscription.plan.id"
        :period-start="subscription.currentPeriodStart"
        :period-end="subscription.currentPeriodEnd"
        :base-price="subscription.plan.priceAmount"
        :interval-months="subscription.plan.intervalMonths"
      />

      <!-- Cancellation Card (conditional) -->
      <Card v-if="subscription.cancelledAt || subscription.cancelEffectiveAt" class="p-6 border-red-200 bg-red-50/50">
        <div class="flex items-center justify-between mb-4">
          <h3 class="text-base font-medium text-red-700">Cancellation</h3>
          <Button
            v-if="canUndoCancellation"
            variant="outline"
            size="sm"
            @click="handleUndoCancellation"
            :disabled="isUndoing"
          >
            <Loader2 v-if="isUndoing" class="h-3 w-3 mr-2 animate-spin" />
            Undo Cancellation
          </Button>
        </div>
        <div class="grid grid-cols-3 gap-6">
          <div>
            <div class="text-sm text-muted-foreground mb-1">Mode</div>
            <div class="text-sm">{{ formatCancelMode(subscription.cancelMode) }}</div>
          </div>
          <div v-if="subscription.cancelEffectiveAt">
            <div class="text-sm text-muted-foreground mb-1">Effective At</div>
            <div class="text-sm tabular-nums">{{ formatDate(subscription.cancelEffectiveAt) }}</div>
          </div>
          <div v-if="subscription.cancelledAt">
            <div class="text-sm text-muted-foreground mb-1">Cancelled At</div>
            <div class="text-sm tabular-nums">{{ formatDate(subscription.cancelledAt) }}</div>
          </div>
        </div>
      </Card>

      <!-- Scheduled Change Card (conditional) -->
      <Card v-if="subscription.scheduledChange" class="p-6 border-blue-200 bg-blue-50/50">
        <h3 class="text-base font-medium text-blue-700 mb-4">Scheduled Change</h3>
        <div class="grid grid-cols-2 gap-6">
          <div>
            <div class="text-sm text-muted-foreground mb-1">Change Type</div>
            <div class="text-sm">{{ subscription.scheduledChange.changeType }}</div>
          </div>
          <div>
            <div class="text-sm text-muted-foreground mb-1">Effective At</div>
            <div class="text-sm tabular-nums">{{ formatDate(subscription.scheduledChange.effectiveAt) }}</div>
          </div>
        </div>
      </Card>

      <!-- Related Details -->
      <Card class="p-6">
        <Collapsible v-model:open="showAdditionalDetails">
          <CollapsibleTrigger class="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors">
            <ChevronRight class="h-4 w-4 transition-transform" :class="{ 'rotate-90': showAdditionalDetails }" />
            Related Details
          </CollapsibleTrigger>
          <CollapsibleContent class="mt-4">
            <div class="space-y-4">
              <!-- Metadata -->
              <div v-if="hasMetadata" class="flex flex-col gap-1">
                <Label class="text-muted-foreground text-sm">Metadata</Label>
                <pre class="bg-muted p-4 rounded-lg text-xs font-mono overflow-x-auto">{{ formatJson(subscription.metadata) }}</pre>
              </div>

              <!-- Customer Details Section -->
              <div :class="{ 'border-t pt-4 mt-4': hasMetadata }">
                <h4 class="text-sm font-medium text-muted-foreground mb-3">Customer Details</h4>
                <div class="space-y-3">
                  <div v-if="subscription.customer.phoneNumber" class="flex flex-col gap-1">
                    <Label class="text-muted-foreground text-sm">Phone</Label>
                    <p class="text-sm">{{ subscription.customer.phoneNumber }}</p>
                  </div>
                  <div v-if="subscription.customer.createdAt" class="flex flex-col gap-1">
                    <Label class="text-muted-foreground text-sm">Customer Created</Label>
                    <p class="text-sm tabular-nums">{{ formatDate(subscription.customer.createdAt) }}</p>
                  </div>
                  <div v-if="subscription.customer.modifiedAt" class="flex flex-col gap-1">
                    <Label class="text-muted-foreground text-sm">Customer Modified</Label>
                    <p class="text-sm tabular-nums">{{ formatDate(subscription.customer.modifiedAt) }}</p>
                  </div>
                </div>
              </div>

              <!-- Plan Details Section -->
              <div class="border-t pt-4 mt-4">
                <h4 class="text-sm font-medium text-muted-foreground mb-3">Plan Details</h4>
                <div class="space-y-3">
                  <div v-if="subscription.plan.description" class="flex flex-col gap-1">
                    <Label class="text-muted-foreground text-sm">Plan Description</Label>
                    <p class="text-sm">{{ subscription.plan.description }}</p>
                  </div>
                  <div v-if="subscription.plan.createdAt" class="flex flex-col gap-1">
                    <Label class="text-muted-foreground text-sm">Plan Created</Label>
                    <p class="text-sm tabular-nums">{{ formatDate(subscription.plan.createdAt) }}</p>
                  </div>
                  <div v-if="subscription.plan.modifiedAt" class="flex flex-col gap-1">
                    <Label class="text-muted-foreground text-sm">Plan Modified</Label>
                    <p class="text-sm tabular-nums">{{ formatDate(subscription.plan.modifiedAt) }}</p>
                  </div>
                </div>
              </div>

              <!-- Scheduled Change Details -->
              <div v-if="subscription.scheduledChange" class="border-t pt-4 mt-4">
                <h4 class="text-sm font-medium text-muted-foreground mb-3">Scheduled Change Details</h4>
                <div class="space-y-3">
                  <div v-if="subscription.scheduledChange.id" class="flex flex-col gap-1">
                    <Label class="text-muted-foreground text-sm">Scheduled Change ID</Label>
                    <div class="flex items-center gap-2">
                      <p class="text-sm font-mono bg-muted px-2 py-1 rounded truncate flex-1">{{ subscription.scheduledChange.id }}</p>
                      <CopyButton :value="subscription.scheduledChange.id" label="Scheduled Change ID" />
                    </div>
                  </div>
                  <div v-if="subscription.scheduledChange.newPlanId" class="flex flex-col gap-1">
                    <Label class="text-muted-foreground text-sm">New Plan ID</Label>
                    <div class="flex items-center gap-2">
                      <p class="text-sm font-mono bg-muted px-2 py-1 rounded truncate flex-1">{{ subscription.scheduledChange.newPlanId }}</p>
                      <CopyButton :value="subscription.scheduledChange.newPlanId" label="New Plan ID" />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </CollapsibleContent>
        </Collapsible>
      </Card>

      <!-- Cancel Subscription Dialog -->
      <CancelSubscriptionDialog
        v-if="subscription"
        :subscription-id="subscription.id"
        :plan-name="subscription.plan.name"
        :customer-name="customerFullName"
        :current-period-end="subscription.currentPeriodEnd"
        :visible="showCancelDialog"
        @update:visible="showCancelDialog = $event"
      />
    </div>

  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ChevronRight,
  ChevronDown,
  AlertCircle,
  RefreshCw,
  ArrowLeft,
  ArrowRight,
  Loader2,
} from 'lucide-vue-next'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import {
  Collapsible,
  CollapsibleTrigger,
  CollapsibleContent
} from '@/components/ui/collapsible'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu'
import { toast } from '@/components/ui/toast/use-toast'
import { parseApiError } from '@/lib/parseApiError'
import { useDemoPrefix } from '@/lib/useDemoPrefix'
import { useSubscriptionQuery } from '../queries'
import { useActivateSubscriptionMutation, useUndoScheduledCancellationMutation } from '../mutations'
import { useSubscriptionUsage } from '../composables/useSubscriptionUsage'
import {
  formatPrice,
  formatIntervalShort,
  formatInterval,
  formatDate,
  formatSubscriptionStatus,
  getSubscriptionStatusClasses
} from '@/lib/formatters'
import CopyButton from '@/components/CopyButton.vue'
import UsageCard from '../components/UsageCard.vue'
import CancelSubscriptionDialog from '../components/CancelSubscriptionDialog.vue'

const route = useRoute()
const router = useRouter()
const { demoPath, isDemo } = useDemoPrefix()

const subscriptionId = computed(() => route.params.id as string)

const { data: subscriptionData, isLoading, isError, refetch } = isDemo.value
  ? { data: ref(null), isLoading: ref(false), isError: ref(false), refetch: () => {} }
  : useSubscriptionQuery(subscriptionId)

const demoSubCust = (id: string, first: string, last: string, email: string) => ({
  id, firstName: first, lastName: last, email, phoneNumber: null,
  createdAt: '2025-12-14T12:00:00Z', modifiedAt: '2026-02-14T12:00:00Z'
})
const demoSubPlan = (id: string, key: string, name: string, description: string, price: number) => ({
  id, key, name, description, priceAmount: price, intervalMonths: '1',
  billingTiming: 'IN_ADVANCE', createdAt: '2025-12-14T12:00:00Z', modifiedAt: '2026-02-14T12:00:00Z'
})

const DEMO_SUBSCRIPTIONS = [
  { id: '11111111-aaaa-4bbb-cccc-dddddddddddd', isActive: true, intervalMonths: '1', billingAnchorDay: 1, gracePeriodDays: 3, currentPeriodStart: '2026-03-01T00:00:00Z', currentPeriodEnd: '2026-03-31T23:59:59Z', cancelledAt: null, cancelEffectiveAt: null, cancelMode: null, scheduledChange: null, metadata: null, customer: demoSubCust('a1b2c3d4-e5f6-4789-abcd-ef0123456789', 'Sarah', 'Chen', 'sarah@outbound.io'), plan: demoSubPlan('c2b3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e', 'growth', 'Growth', 'For scaling teams that need advanced analytics and integrations', 199) },
  { id: '22222222-bbbb-4ccc-dddd-eeeeeeeeeeee', isActive: true, intervalMonths: '1', billingAnchorDay: 1, gracePeriodDays: 5, currentPeriodStart: '2026-03-01T00:00:00Z', currentPeriodEnd: '2026-03-31T23:59:59Z', cancelledAt: null, cancelEffectiveAt: null, cancelMode: null, scheduledChange: null, metadata: null, customer: demoSubCust('b2c3d4e5-f6a7-4890-bcde-f01234567890', 'Marcus', 'Rodriguez', 'marcus@pipelineai.com'), plan: demoSubPlan('d3c4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f', 'enterprise', 'Enterprise', 'For large sales organizations with custom requirements', 699) },
  { id: '33333333-cccc-4ddd-eeee-ffffffffffff', isActive: true, intervalMonths: '1', billingAnchorDay: 1, gracePeriodDays: 3, currentPeriodStart: '2026-03-01T00:00:00Z', currentPeriodEnd: '2026-03-31T23:59:59Z', cancelledAt: null, cancelEffectiveAt: null, cancelMode: null, scheduledChange: null, metadata: null, customer: demoSubCust('c3d4e5f6-a7b8-4901-cdef-012345678901', 'Emily', 'Park', 'emily@prospectlab.io'), plan: demoSubPlan('b1a2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', 'starter', 'Starter', 'For early-stage sales teams getting started with outbound', 99) },
  { id: '44444444-dddd-4eee-ffff-aaaaaaaaaaaa', isActive: true, intervalMonths: '1', billingAnchorDay: 1, gracePeriodDays: 3, currentPeriodStart: '2026-03-01T00:00:00Z', currentPeriodEnd: '2026-03-31T23:59:59Z', cancelledAt: null, cancelEffectiveAt: null, cancelMode: null, scheduledChange: null, metadata: null, customer: demoSubCust('d4e5f6a7-b8c9-4012-def0-123456789012', 'James', 'Mitchell', 'james@dealflow.co'), plan: demoSubPlan('c2b3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e', 'growth', 'Growth', 'For scaling teams that need advanced analytics and integrations', 199) },
  { id: '55555555-eeee-4fff-aaaa-bbbbbbbbbbbb', isActive: true, intervalMonths: '1', billingAnchorDay: 1, gracePeriodDays: 5, currentPeriodStart: '2026-03-01T00:00:00Z', currentPeriodEnd: '2026-03-31T23:59:59Z', cancelledAt: null, cancelEffectiveAt: null, cancelMode: null, scheduledChange: null, metadata: null, customer: demoSubCust('e5f6a7b8-c9d0-4123-ef01-234567890123', 'Anika', 'Patel', 'anika@salesforge.dev'), plan: demoSubPlan('d3c4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f', 'enterprise', 'Enterprise', 'For large sales organizations with custom requirements', 699) },
]

const subscription = computed(() => {
  if (isDemo.value) {
    const id = route.params.id as string
    return DEMO_SUBSCRIPTIONS.find((s) => s.id === id) ?? DEMO_SUBSCRIPTIONS[0]
  }
  return subscriptionData.value?.data ?? null
})

const customerFullName = computed(() => {
  if (!subscription.value) return ''
  return `${subscription.value.customer.firstName} ${subscription.value.customer.lastName}`
})

const showAdditionalDetails = ref(false)
const showCancelDialog = ref(false)

const { mutateAsync: activate, isPending: isActivating } = isDemo.value
  ? { mutateAsync: async () => {}, isPending: ref(false) }
  : useActivateSubscriptionMutation()

const { mutateAsync: undoCancellation, isPending: isUndoing } = isDemo.value
  ? { mutateAsync: async () => {}, isPending: ref(false) }
  : useUndoScheduledCancellationMutation()

const hasMetadata = computed(() => {
  const metadata = subscription.value?.metadata
  return metadata && typeof metadata === 'object' && Object.keys(metadata).length > 0
})

const canUndoCancellation = computed(() => {
  if (!subscription.value?.cancelEffectiveAt) return false
  return new Date(subscription.value.cancelEffectiveAt) > new Date()
})

// Detect if plan has usage-based features by checking usage data
const subIdRef = computed(() => subscription.value?.id ?? '')
const planIdRef = computed(() => subscription.value?.plan?.id ?? '')
const periodStartRef = computed(() => subscription.value?.currentPeriodStart ?? '')
const periodEndRef = computed(() => subscription.value?.currentPeriodEnd ?? '')

const { groups: usageGroups } = isDemo.value
  ? { groups: ref([]) }
  : useSubscriptionUsage(subIdRef, planIdRef, periodStartRef, periodEndRef)

const hasUsageFeatures = computed(() => {
  return usageGroups.value.length > 0
})

async function handleActivate() {
  if (!subscription.value) return
  try {
    await activate(subscription.value.id)
    toast({
      title: 'Subscription activated',
      description: 'The subscription is now active and entitlements have been granted.'
    })
  } catch (error) {
    const parsedError = parseApiError(error)
    toast({ title: 'Error', description: parsedError.message, variant: 'destructive' })
  }
}

async function handleUndoCancellation() {
  if (!subscription.value) return
  try {
    await undoCancellation(subscription.value.id)
    toast({
      title: 'Cancellation undone',
      description: 'The scheduled cancellation has been removed.'
    })
  } catch (error) {
    const parsedError = parseApiError(error)
    toast({ title: 'Error', description: parsedError.message, variant: 'destructive' })
  }
}

function formatJson(data: Record<string, unknown> | null | undefined): string {
  if (!data) return '{}'
  return JSON.stringify(data, null, 2)
}

function formatCancelMode(mode: string | null | undefined): string {
  if (!mode) return '\u2014'
  switch (mode) {
    case 'END_OF_PERIOD': return 'End of period'
    case 'IMMEDIATE': return 'Immediate'
    default: return mode
  }
}
</script>
