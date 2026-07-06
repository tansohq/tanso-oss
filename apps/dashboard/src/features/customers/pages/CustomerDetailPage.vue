<template>
  <div class="p-6 pb-16">
    <!-- Breadcrumb -->
    <div class="mb-6">
      <nav class="flex items-center gap-2 text-sm text-muted-foreground mb-2">
        <router-link :to="backPath" class="hover:text-foreground transition-colors">{{ backLabel }}</router-link>
        <ChevronRight class="h-4 w-4" />
        <span class="text-foreground">{{ customerName }}</span>
      </nav>
    </div>

    <!-- Loading State -->
    <div v-if="isLoading" class="flex items-center justify-center py-12">
      <Loader2 class="h-8 w-8 animate-spin text-muted-foreground" />
    </div>

    <!-- Error State -->
    <div v-else-if="isError" class="flex flex-col items-center justify-center py-12 bg-card rounded-lg border">
      <AlertCircle class="w-12 h-12 text-destructive mb-4" />
      <p class="text-lg font-medium text-foreground mb-2">Unable to load customer</p>
      <p class="text-sm text-muted-foreground mb-4">The customer could not be found or an error occurred.</p>
      <div class="flex gap-2">
        <Button variant="outline" @click="refetch">
          <RefreshCw class="w-4 h-4 mr-2" />
          Try Again
        </Button>
        <Button variant="outline" @click="router.push(backPath)">
          <ArrowLeft class="w-4 h-4 mr-2" />
          Back to {{ backLabel }}
        </Button>
      </div>
    </div>

    <div v-else-if="!customer" class="flex flex-col items-center justify-center py-12 bg-card rounded-lg border">
      <AlertCircle class="w-12 h-12 text-muted-foreground mb-4" />
      <p class="text-lg font-medium text-foreground mb-2">Customer not found</p>
      <p class="text-sm text-muted-foreground mb-4">This customer may have been deleted or the link is invalid.</p>
      <Button variant="outline" @click="router.push(backPath)">
        <ArrowLeft class="w-4 h-4 mr-2" />
        Back to {{ backLabel }}
      </Button>
    </div>

    <div v-else-if="customer" class="space-y-6 max-w-5xl">
      <!-- Header -->
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-semibold tracking-tight text-foreground">{{ customerName }}</h1>
          <p v-if="!isLoadingSubscriptions && activeSubscriptionCount > 0" class="text-muted-foreground mt-1">
            {{ activeSubscriptionCount }} active {{ activeSubscriptionCount === 1 ? 'subscription' : 'subscriptions' }}
          </p>
        </div>
        <Button v-if="!isEditing" variant="outline" size="sm" @click="startEditing">
          <Pencil class="w-3.5 h-3.5 mr-1" />
          Edit
        </Button>
      </div>

      <!-- Customer Details Card - View Mode -->
      <Card v-if="!isEditing">
        <CardHeader>
          <CardTitle class="text-base">Overview</CardTitle>
        </CardHeader>
        <CardContent>
          <dl class="grid grid-cols-2 lg:grid-cols-3 gap-6 text-sm">
            <div>
              <dt class="text-muted-foreground">Email</dt>
              <dd class="truncate" :title="customer.email">{{ customer.email }}</dd>
            </div>
            <div>
              <dt class="text-muted-foreground">Phone</dt>
              <dd>{{ customer.phoneNumber || '—' }}</dd>
            </div>
            <div>
              <dt class="text-muted-foreground">External ID</dt>
              <dd class="flex items-center gap-1">
                <span class="font-mono truncate max-w-[200px]">{{ customer.customerReferenceId }}</span>
                <CopyButton :value="customer.customerReferenceId" label="External ID" />
              </dd>
            </div>
            <div>
              <dt class="text-muted-foreground">Created</dt>
              <dd class="tabular-nums">{{ formatDate(customer.createdAt) }}</dd>
            </div>
            <div v-if="customer.modifiedAt">
              <dt class="text-muted-foreground">Modified</dt>
              <dd class="tabular-nums">{{ formatDate(customer.modifiedAt) }}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      <!-- Customer Details Card - Edit Mode -->
      <Card v-else>
        <CardHeader>
          <CardTitle class="text-base">Edit Customer</CardTitle>
        </CardHeader>
        <CardContent>
          <form @submit.prevent="onSubmit" class="space-y-4">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div class="flex flex-col gap-2">
                <Label for="email" class="text-sm text-muted-foreground">Email *</Label>
                <Input
                  id="email"
                  v-model="email"
                  type="email"
                  :class="{ 'border-destructive': errors.email }"
                  placeholder="e.g., john@example.com"
                />
                <span v-if="errors.email" class="text-destructive text-xs">{{ errors.email }}</span>
              </div>

              <div class="flex flex-col gap-2">
                <Label for="firstName" class="text-sm text-muted-foreground">First Name *</Label>
                <Input
                  id="firstName"
                  v-model="firstName"
                  :class="{ 'border-destructive': errors.firstName }"
                  placeholder="e.g., John"
                />
                <span v-if="errors.firstName" class="text-destructive text-xs">{{
                  errors.firstName
                }}</span>
              </div>

              <div class="flex flex-col gap-2">
                <Label for="lastName" class="text-sm text-muted-foreground">Last Name *</Label>
                <Input
                  id="lastName"
                  v-model="lastName"
                  :class="{ 'border-destructive': errors.lastName }"
                  placeholder="e.g., Doe"
                />
                <span v-if="errors.lastName" class="text-destructive text-xs">{{
                  errors.lastName
                }}</span>
              </div>

              <div class="flex flex-col gap-2">
                <Label for="phoneNumber" class="text-sm text-muted-foreground">Phone Number</Label>
                <Input
                  id="phoneNumber"
                  v-model="phoneNumber"
                  :class="{ 'border-destructive': errors.phoneNumber }"
                  placeholder="e.g., +1 555-0123"
                />
                <span v-if="errors.phoneNumber" class="text-destructive text-xs">{{
                  errors.phoneNumber
                }}</span>
              </div>
            </div>

            <Alert v-if="errorMessage" variant="destructive">
              <AlertCircle class="h-4 w-4" />
              <AlertDescription>{{ errorMessage }}</AlertDescription>
            </Alert>

            <div class="flex items-center justify-end gap-2 pt-2">
              <Button variant="outline" size="sm" :disabled="isSubmitting" @click="cancelEditing">Cancel</Button>
              <Button size="sm" :disabled="isSubmitting" @click="onSubmit">
                <Loader2 v-if="isSubmitting" class="mr-2 h-4 w-4 animate-spin" />
                Save
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <!-- Subscriptions -->
      <div v-if="isLoadingSubscriptions" class="py-8 flex justify-center">
        <Loader2 class="h-6 w-6 animate-spin text-muted-foreground" />
      </div>

      <Card v-else-if="subscriptions && subscriptions.length > 0">
        <CardHeader>
          <CardTitle class="text-base">Subscriptions</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Plan</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Period Start</TableHead>
                <TableHead>Period End</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow
                v-for="sub in subscriptions"
                :key="sub.id"
                class="cursor-pointer hover:bg-muted/50"
                @click="navigateToSubscription(sub.id)"
              >
                <TableCell class="font-medium">{{ sub.plan.name }}</TableCell>
                <TableCell>
                  <Badge :class="getSubscriptionStatusClasses(sub)" class="shadow-none">
                    {{ formatSubscriptionStatus(sub) }}
                  </Badge>
                </TableCell>
                <TableCell class="tabular-nums">{{ formatDate(sub.currentPeriodStart) }}</TableCell>
                <TableCell class="tabular-nums">{{ formatDate(sub.currentPeriodEnd) }}</TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Card v-else-if="!isLoadingSubscriptions">
        <CardContent class="py-8">
          <p class="text-sm text-muted-foreground text-center">No subscriptions yet</p>
        </CardContent>
      </Card>

      <!-- Credit Pools -->
      <Card>
        <CardHeader>
          <CardTitle class="text-base">Credit Pools</CardTitle>
        </CardHeader>
        <CardContent>
          <div v-if="isLoadingPools">
            <Skeleton class="h-10 w-full mb-2" />
            <Skeleton class="h-10 w-full" />
          </div>

          <template v-else-if="creditPools.length > 0">
            <div
              v-for="pool in creditPools"
              :key="pool.id"
              class="border rounded-lg p-4 mb-3 last:mb-0"
            >
              <div class="flex items-center justify-between mb-2 flex-wrap gap-2">
                <div class="flex items-center gap-2">
                  <Coins class="h-4 w-4 text-muted-foreground" />
                  <span class="text-sm font-medium">{{ pool.denomination || 'Credits' }}</span>
                  <Badge
                    :class="pool.status === 'ACTIVE' ? 'bg-emerald-50 text-emerald-700 border border-emerald-200/50' : 'bg-gray-50 text-gray-600 border border-gray-200/50'"
                    class="shadow-none"
                  >
                    {{ pool.status }}
                  </Badge>
                  <Badge v-if="pool.hardLimit" class="bg-amber-50 text-amber-700 border border-amber-200/50 shadow-none">
                    Hard Limit
                  </Badge>
                </div>
                <div class="flex items-center gap-2">
                  <Button variant="outline" size="sm" class="h-7 text-xs" @click="openGrantModal(pool)">
                    Grant Credits
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    class="h-7 text-xs"
                    @click="togglePoolDetails(pool.id)"
                  >
                    {{ expandedPoolIds.has(pool.id) ? 'Hide' : 'Details' }}
                  </Button>
                </div>
              </div>
              <div class="text-2xl font-semibold tabular-nums">
                {{ pool.balance.toLocaleString() }}
                <span class="text-sm font-normal text-muted-foreground">{{ pool.denomination || 'credits' }}</span>
              </div>

              <!-- Expanded Pool Details -->
              <div v-if="expandedPoolIds.has(pool.id)" class="mt-4 border-t pt-4 space-y-4">
                <PoolGrantsTable :pool-id="pool.id" />
                <PoolTransactionsTable :pool-id="pool.id" />
              </div>
            </div>
          </template>

          <div v-else class="text-center py-6 text-muted-foreground">
            <Coins class="w-10 h-10 mx-auto mb-2" />
            <p class="text-sm">No credit pools for this customer.</p>
          </div>
        </CardContent>
      </Card>

      <!-- Grant Credits Modal -->
      <Dialog :open="showGrantModal" @update:open="showGrantModal = $event">
        <DialogContent class="sm:max-w-[400px]">
          <DialogHeader>
            <DialogTitle>Grant Credits</DialogTitle>
            <DialogDescription>Add credits to this customer's pool by hand.</DialogDescription>
          </DialogHeader>
          <div class="flex flex-col gap-4">
            <div class="flex flex-col gap-2">
              <Label>Amount *</Label>
              <Input v-model.number="grantAmount" type="number" min="1" placeholder="e.g., 500" />
            </div>
            <div class="flex flex-col gap-2">
              <Label>Reason</Label>
              <Input v-model="grantReason" placeholder="Optional reason" />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" @click="showGrantModal = false">Cancel</Button>
            <Button :disabled="!grantAmount || isGranting" @click="handleGrantCredits">
              <Loader2 v-if="isGranting" class="mr-2 h-4 w-4 animate-spin" />
              Grant
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import {
  ChevronRight,
  Pencil,
  AlertCircle,
  RefreshCw,
  ArrowLeft,
  Loader2,
  Coins
} from 'lucide-vue-next'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { toast } from '@/components/ui/toast/use-toast'
import { useCustomerQuery } from '../queries'
import { useCustomerSubscriptionsQuery } from '@/features/subscriptions/queries'
import { updateCustomerSchema } from '../schemas'
import { updateCustomer } from '../api'
import { parseApiError } from '@/lib/parseApiError'
import { useTracking } from '@/lib/tracking'
import { useDemoPrefix } from '@/lib/useDemoPrefix'
import { formatDate, formatSubscriptionStatus, getSubscriptionStatusClasses } from '@/lib/formatters'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import CopyButton from '@/components/CopyButton.vue'
import PoolGrantsTable from '@/features/credits/components/PoolGrantsTable.vue'
import PoolTransactionsTable from '@/features/credits/components/PoolTransactionsTable.vue'
import { useCustomerCreditPoolsQuery } from '@/features/credits/queries'
import { useGrantCreditsMutation } from '@/features/credits/mutations'
import type { CreditPool } from '@/features/credits/types'
import type { UpdateCustomer } from '../types'

const { track } = useTracking()
const route = useRoute()
const router = useRouter()
const { demoPath, isDemo } = useDemoPrefix()
const queryClient = isDemo.value ? ({} as ReturnType<typeof useQueryClient>) : useQueryClient()

const customerId = computed(() => route.params.id as string)

const backPath = computed(() => demoPath(route.query.from === 'analytics' ? '/analytics' : '/customers'))
const backLabel = computed(() => route.query.from === 'analytics' ? 'Analytics' : 'Customers')

const { data: customerData, isLoading, isError, refetch } = isDemo.value
  ? { data: ref(null), isLoading: ref(false), isError: ref(false), refetch: () => {} }
  : useCustomerQuery(customerId)

const DEMO_CUSTOMERS = [
  { id: 'a1b2c3d4-e5f6-4789-abcd-ef0123456789', firstName: 'Sarah', lastName: 'Chen', email: 'sarah@outbound.io', phoneNumber: null, customerReferenceId: 'outbound-io', referenceId: 'outbound-io', createdAt: '2025-12-14T12:00:00Z', modifiedAt: '2026-02-14T12:00:00Z' },
  { id: 'b2c3d4e5-f6a7-4890-bcde-f01234567890', firstName: 'Marcus', lastName: 'Rodriguez', email: 'marcus@pipelineai.com', phoneNumber: null, customerReferenceId: 'pipeline-ai', referenceId: 'pipeline-ai', createdAt: '2025-12-14T12:00:00Z', modifiedAt: '2026-02-14T12:00:00Z' },
  { id: 'c3d4e5f6-a7b8-4901-cdef-012345678901', firstName: 'Emily', lastName: 'Park', email: 'emily@prospectlab.io', phoneNumber: null, customerReferenceId: 'prospect-lab', referenceId: 'prospect-lab', createdAt: '2025-12-14T12:00:00Z', modifiedAt: '2026-02-14T12:00:00Z' },
  { id: 'd4e5f6a7-b8c9-4012-def0-123456789012', firstName: 'James', lastName: 'Mitchell', email: 'james@dealflow.co', phoneNumber: null, customerReferenceId: 'deal-flow', referenceId: 'deal-flow', createdAt: '2025-12-14T12:00:00Z', modifiedAt: '2026-02-14T12:00:00Z' },
  { id: 'e5f6a7b8-c9d0-4123-ef01-234567890123', firstName: 'Anika', lastName: 'Patel', email: 'anika@salesforge.dev', phoneNumber: null, customerReferenceId: 'sales-forge', referenceId: 'sales-forge', createdAt: '2025-12-14T12:00:00Z', modifiedAt: '2026-02-14T12:00:00Z' },
]

const customer = computed(() => {
  if (isDemo.value) {
    const id = route.params.id as string
    return DEMO_CUSTOMERS.find((c) => c.id === id) ?? DEMO_CUSTOMERS[0]
  }
  return customerData.value?.data || null
})

const customerName = computed(() => {
  if (!customer.value) return 'Customer'
  return `${customer.value.firstName} ${customer.value.lastName}`
})

// Fetch customer subscriptions
const { data: subscriptionsData, isLoading: isLoadingSubscriptions } = isDemo.value
  ? { data: ref(null), isLoading: ref(false) }
  : useCustomerSubscriptionsQuery(customerId)

// Map customer ID to their subscription for demo mode
const DEMO_CUSTOMER_SUBSCRIPTIONS: Record<string, Array<{ id: string; isActive: boolean; plan: { id: string; name: string }; currentPeriodStart: string; currentPeriodEnd: string }>> = {
  'a1b2c3d4-e5f6-4789-abcd-ef0123456789': [{ id: '11111111-aaaa-4bbb-cccc-dddddddddddd', isActive: true, plan: { id: 'c2b3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e', name: 'Growth' }, currentPeriodStart: '2026-03-01T00:00:00Z', currentPeriodEnd: '2026-03-31T23:59:59Z' }],
  'b2c3d4e5-f6a7-4890-bcde-f01234567890': [{ id: '22222222-bbbb-4ccc-dddd-eeeeeeeeeeee', isActive: true, plan: { id: 'd3c4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f', name: 'Enterprise' }, currentPeriodStart: '2026-03-01T00:00:00Z', currentPeriodEnd: '2026-03-31T23:59:59Z' }],
  'c3d4e5f6-a7b8-4901-cdef-012345678901': [{ id: '33333333-cccc-4ddd-eeee-ffffffffffff', isActive: true, plan: { id: 'b1a2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', name: 'Starter' }, currentPeriodStart: '2026-03-01T00:00:00Z', currentPeriodEnd: '2026-03-31T23:59:59Z' }],
  'd4e5f6a7-b8c9-4012-def0-123456789012': [{ id: '44444444-dddd-4eee-ffff-aaaaaaaaaaaa', isActive: true, plan: { id: 'c2b3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e', name: 'Growth' }, currentPeriodStart: '2026-03-01T00:00:00Z', currentPeriodEnd: '2026-03-31T23:59:59Z' }],
  'e5f6a7b8-c9d0-4123-ef01-234567890123': [{ id: '55555555-eeee-4fff-aaaa-bbbbbbbbbbbb', isActive: true, plan: { id: 'd3c4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f', name: 'Enterprise' }, currentPeriodStart: '2026-03-01T00:00:00Z', currentPeriodEnd: '2026-03-31T23:59:59Z' }],
}

const subscriptions = computed(() => {
  if (isDemo.value) return DEMO_CUSTOMER_SUBSCRIPTIONS[route.params.id as string] ?? []
  const data = subscriptionsData.value?.data
  if (!data) return []
  // Handle paginated format: { items: [...] }
  if ('items' in data && Array.isArray(data.items)) {
    return data.items
  }
  // Handle direct array format
  if (Array.isArray(data)) {
    return data
  }
  return []
})

const activeSubscriptionCount = computed(() => {
  return subscriptions.value.filter((s: { isActive: boolean }) => s.isActive).length
})

// Inline editing state
const isEditing = ref(false)
const errorMessage = ref<string | null>(null)

// Form setup - skip validation setup in demo mode (useForm is safe to always call)
const { defineField, handleSubmit, errors, setValues, resetForm, setFieldError } = useForm({
  validationSchema: toTypedSchema(updateCustomerSchema)
})

const [firstName] = defineField('firstName')
const [lastName] = defineField('lastName')
const [email] = defineField('email')
const [phoneNumber] = defineField('phoneNumber')

// Mutation
const { mutateAsync, isPending } = isDemo.value
  ? { mutateAsync: async () => {}, isPending: ref(false) }
  : useMutation({
    mutationFn: (data: { customerId: string; customerData: UpdateCustomer }) =>
      updateCustomer(data.customerId, data.customerData),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['customer', customerId.value] })
      queryClient.invalidateQueries({ queryKey: ['customers'] })
    }
  })

const isSubmitting = computed(() => isPending.value)

// Initialize form when customer loads
watch(customer, (c) => {
  if (c) {
    setValues({
      firstName: c.firstName,
      lastName: c.lastName,
      email: c.email,
      phoneNumber: c.phoneNumber || ''
    })
  }
}, { immediate: true })

function startEditing() {
  if (customer.value) {
    setValues({
      firstName: customer.value.firstName,
      lastName: customer.value.lastName,
      email: customer.value.email,
      phoneNumber: customer.value.phoneNumber || ''
    })
  }
  errorMessage.value = null
  isEditing.value = true
}

function cancelEditing() {
  resetForm()
  if (customer.value) {
    setValues({
      firstName: customer.value.firstName,
      lastName: customer.value.lastName,
      email: customer.value.email,
      phoneNumber: customer.value.phoneNumber || ''
    })
  }
  errorMessage.value = null
  isEditing.value = false
}

const onSubmit = handleSubmit(async (values) => {
  if (!customer.value?.id) {
    errorMessage.value = 'No customer selected'
    return
  }

  try {
    errorMessage.value = null
    await mutateAsync({ customerId: customer.value.id, customerData: values })
    toast({ title: 'Success', description: 'Customer updated successfully' })
    isEditing.value = false
  } catch (error) {
    const parsedError = parseApiError(error)
    errorMessage.value = parsedError.message
    toast({ title: 'Error', description: parsedError.message, variant: 'destructive' })

    if (parsedError.type === 'duplicate') {
      const lowerMessage = parsedError.message.toLowerCase()
      if (lowerMessage.includes('email')) {
        setFieldError('email', parsedError.message)
      }
    }
  }
})

function navigateToSubscription(subscriptionId: string) {
  router.push(demoPath(`/subscriptions/${subscriptionId}`))
}

// Credit Pools
const { data: poolsData, isLoading: isLoadingPools } = isDemo.value
  ? { data: ref(null), isLoading: ref(false) }
  : useCustomerCreditPoolsQuery(customerId)
const creditPools = computed<CreditPool[]>(() => {
  if (!poolsData.value?.data) return []
  return Array.isArray(poolsData.value.data) ? poolsData.value.data : []
})

const expandedPoolIds = ref(new Set<string>())
function togglePoolDetails(poolId: string) {
  if (expandedPoolIds.value.has(poolId)) {
    expandedPoolIds.value.delete(poolId)
  } else {
    expandedPoolIds.value.add(poolId)
  }
}

// Grant Credits
const showGrantModal = ref(false)
const grantPoolId = ref<string>('')
const grantAmount = ref<number | undefined>(undefined)
const grantReason = ref('')
const { mutateAsync: grantCreditsMutate, isPending: isGranting } = isDemo.value
  ? { mutateAsync: async () => {}, isPending: ref(false) }
  : useGrantCreditsMutation()

function openGrantModal(pool: CreditPool) {
  grantPoolId.value = pool.id
  grantAmount.value = undefined
  grantReason.value = ''
  showGrantModal.value = true
}

async function handleGrantCredits() {
  if (!grantAmount.value || !grantPoolId.value) return
  try {
    await grantCreditsMutate({
      poolId: grantPoolId.value,
      amount: grantAmount.value,
      reason: grantReason.value || undefined
    })
    track('credits_granted')
    toast({ title: 'Success', description: 'Credits granted' })
    showGrantModal.value = false
  } catch (error) {
    const parsedError = parseApiError(error)
    toast({ title: 'Error', description: parsedError.message, variant: 'destructive' })
  }
}
</script>
