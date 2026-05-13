<template>
  <Dialog :open="visible" @update:open="emit('update:visible', $event)">
    <DialogContent class="sm:max-w-[550px]">
      <DialogHeader>
        <DialogTitle>New Subscription</DialogTitle>
        <DialogDescription>
          Create a new subscription by linking a customer to a plan.
        </DialogDescription>
      </DialogHeader>

      <div class="flex flex-col gap-4 py-4">
        <!-- Customer Selection -->
        <div class="space-y-2">
          <Label>Customer</Label>
          <Select v-model="selectedCustomerId">
            <SelectTrigger>
              <SelectValue placeholder="Select a customer" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem
                v-for="customer in availableCustomers"
                :key="customer.id"
                :value="customer.id"
              >
                {{ customer.name }}
              </SelectItem>
            </SelectContent>
          </Select>
        </div>

        <!-- Plan Selection -->
        <div class="space-y-2">
          <Label>Plan</Label>
          <Select v-model="selectedPlanId">
            <SelectTrigger>
              <SelectValue placeholder="Select a plan" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem v-for="plan in availablePlans" :key="plan.id" :value="plan.id">
                {{ plan.name }}
              </SelectItem>
            </SelectContent>
          </Select>
        </div>

        <!-- Version Selection -->
        <div v-if="selectedPlanId" class="space-y-2">
          <Label>Version</Label>
          <Select v-model="selectedVersion">
            <SelectTrigger>
              <SelectValue placeholder="Select a version" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem
                v-for="version in availableVersions"
                :key="version.version"
                :value="String(version.version)"
              >
                v{{ version.version }}
                <span v-if="version.isDefault" class="text-muted-foreground ml-1">(default)</span>
              </SelectItem>
            </SelectContent>
          </Select>
        </div>

        <!-- Billing Cycle -->
        <div class="space-y-2">
          <Label>Billing Cycle</Label>
          <Select v-model="billingCycle">
            <SelectTrigger>
              <SelectValue placeholder="Select billing cycle" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="monthly">Monthly</SelectItem>
              <SelectItem value="quarterly">Quarterly</SelectItem>
              <SelectItem value="annual">Annual</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <!-- Start Date -->
        <div class="space-y-2">
          <Label>Start Date</Label>
          <Input type="date" v-model="startDate" />
        </div>

        <!-- Preview -->
        <div v-if="canSave" class="p-4 bg-muted rounded-lg space-y-2">
          <div class="text-sm font-medium">Subscription Preview</div>
          <div class="grid grid-cols-2 gap-2 text-sm">
            <div class="text-muted-foreground">Customer:</div>
            <div>{{ selectedCustomerName }}</div>
            <div class="text-muted-foreground">Plan:</div>
            <div>{{ selectedPlanName }} v{{ selectedVersion }}</div>
            <div class="text-muted-foreground">Billing:</div>
            <div class="capitalize">{{ billingCycle }}</div>
            <div class="text-muted-foreground">Starts:</div>
            <div>{{ formatDate(startDate) }}</div>
          </div>
        </div>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="handleClose">Cancel</Button>
        <Button @click="handleSave" :disabled="!canSave">
          <Plus class="h-4 w-4 mr-2" />
          Create Subscription
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Plus } from 'lucide-vue-next'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import { useDemoState } from '../../composables/useDemoState'
import { toast } from '@/components/ui/toast/use-toast'
import type { BillingCycle } from '../../types'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const { customers, pricingPlansData, addSubscription } = useDemoState()

const selectedCustomerId = ref<string>('')
const selectedPlanId = ref<string>('')
const selectedVersion = ref<string>('')
const billingCycle = ref<BillingCycle>('monthly')
const startDate = ref<string>(new Date().toISOString().split('T')[0])

const availableCustomers = computed(() => customers)

const availablePlans = computed(() => {
  return pricingPlansData.value.filter((p) => p.status === 'active')
})

const selectedPlan = computed(() => {
  if (!selectedPlanId.value) return null
  return pricingPlansData.value.find((p) => p.id === selectedPlanId.value) ?? null
})

const selectedPlanName = computed(() => selectedPlan.value?.name ?? '')

const selectedCustomer = computed(() => {
  if (!selectedCustomerId.value) return null
  return customers.find((c) => c.id === selectedCustomerId.value) ?? null
})

const selectedCustomerName = computed(() => selectedCustomer.value?.name ?? '')

const availableVersions = computed(() => {
  if (!selectedPlan.value) return []
  return selectedPlan.value.versions.filter((v) => v.status === 'published')
})

const canSave = computed(() => {
  return (
    selectedCustomerId.value &&
    selectedPlanId.value &&
    selectedVersion.value &&
    billingCycle.value &&
    startDate.value
  )
})

// Reset form when modal opens
watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      resetForm()
    }
  }
)

// Set default version when plan changes
watch(selectedPlanId, (newPlanId) => {
  if (newPlanId) {
    const plan = pricingPlansData.value.find((p) => p.id === newPlanId)
    if (plan) {
      const defaultVersion = plan.versions.find((v) => v.isDefault)
      selectedVersion.value = defaultVersion ? String(defaultVersion.version) : ''
    }
  } else {
    selectedVersion.value = ''
  }
})

function resetForm() {
  selectedCustomerId.value = ''
  selectedPlanId.value = ''
  selectedVersion.value = ''
  billingCycle.value = 'monthly'
  startDate.value = new Date().toISOString().split('T')[0]
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

function calculatePeriodEnd(start: string, cycle: BillingCycle): string {
  const date = new Date(start)
  switch (cycle) {
    case 'monthly':
      date.setMonth(date.getMonth() + 1)
      break
    case 'quarterly':
      date.setMonth(date.getMonth() + 3)
      break
    case 'annual':
      date.setFullYear(date.getFullYear() + 1)
      break
  }
  return date.toISOString().split('T')[0]
}

function handleClose() {
  emit('update:visible', false)
}

function handleSave() {
  if (!canSave.value || !selectedCustomer.value || !selectedPlan.value) return

  const periodEnd = calculatePeriodEnd(startDate.value, billingCycle.value)
  const nextInvoice = periodEnd

  addSubscription({
    customerId: selectedCustomerId.value,
    customerName: selectedCustomer.value.name,
    customerEmail: selectedCustomer.value.email,
    planId: selectedPlanId.value,
    planName: selectedPlan.value.name,
    startDate: startDate.value,
    version: parseInt(selectedVersion.value),
    billingCycle: billingCycle.value,
    currentPeriodStart: startDate.value,
    currentPeriodEnd: periodEnd,
    nextInvoiceDate: nextInvoice,
    hasOverrides: false,
    status: 'active'
  })

  toast({
    title: 'Subscription created',
    description: `${selectedCustomerName.value} subscribed to ${selectedPlanName.value}.`
  })

  handleClose()
}
</script>
