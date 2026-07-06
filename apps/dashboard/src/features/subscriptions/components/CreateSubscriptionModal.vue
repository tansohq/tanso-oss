<template>
  <Dialog :open="visible" @update:open="emit('update:visible', $event)">
    <DialogContent class="sm:max-w-[600px]">
      <DialogHeader>
        <DialogTitle>Create New Subscription</DialogTitle>
        <DialogDescription>Put a customer on a plan to start billing them.</DialogDescription>
      </DialogHeader>

      <form @submit.prevent="onSubmit" class="flex flex-col gap-6">
        <div class="grid grid-cols-2 gap-5">
          <div class="flex flex-col gap-2">
            <Label for="customerId">Customer *</Label>
            <Popover v-model:open="customerDropdownOpen">
              <PopoverTrigger as-child>
                <Button
                  variant="outline"
                  role="combobox"
                  :aria-expanded="customerDropdownOpen"
                  class="justify-between font-normal"
                  :class="[errors.customerId ? 'border-destructive' : '', !customerId ? 'text-muted-foreground' : '']"
                >
                  <span class="truncate">{{ selectedCustomerLabel || 'Select a customer' }}</span>
                  <ChevronsUpDown class="ml-2 h-4 w-4 shrink-0 opacity-50" />
                </Button>
              </PopoverTrigger>
              <PopoverContent class="w-[--reka-popover-trigger-width] p-0" align="start">
                <Command>
                  <CommandInput placeholder="Search by name or email..." />
                  <CommandEmpty>No customers found.</CommandEmpty>
                  <CommandList>
                    <CommandGroup>
                      <CommandItem
                        v-for="customer in customers"
                        :key="customer.id"
                        :value="`${customer.firstName} ${customer.lastName} ${customer.email}`"
                        @select="selectCustomer(customer.id)"
                      >
                        <Check class="mr-2 h-4 w-4 shrink-0" :class="customerId === customer.id ? 'opacity-100' : 'opacity-0'" />
                        <div class="flex flex-col">
                          <span class="text-sm">{{ customer.firstName }} {{ customer.lastName }}</span>
                          <span class="text-xs text-muted-foreground">{{ customer.email }}</span>
                        </div>
                      </CommandItem>
                    </CommandGroup>
                  </CommandList>
                </Command>
              </PopoverContent>
            </Popover>
            <span v-if="errors.customerId" class="text-destructive text-xs">
              {{ errors.customerId }}
            </span>
            <p v-if="stripeMode === 'FULL_SYNC'" class="text-xs text-muted-foreground">
              Only customers without an active subscription are shown.
            </p>
          </div>

          <div class="flex flex-col gap-2">
            <Label for="planId">Active Plan *</Label>
            <Select v-model="planId">
              <SelectTrigger
                id="planId"
                :class="{ 'border-destructive': errors.planId }"
              >
                <SelectValue placeholder="Select a plan" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem
                  v-for="plan in plans"
                  :key="plan.id"
                  :value="plan.id"
                >
                  {{ plan.name }}
                </SelectItem>
              </SelectContent>
            </Select>
            <span v-if="errors.planId" class="text-destructive text-xs">
              {{ errors.planId }}
            </span>
          </div>
        </div>

        <Alert v-if="errorMessage" variant="destructive">
          <AlertCircle class="h-4 w-4" />
          <AlertDescription>{{ errorMessage }}</AlertDescription>
        </Alert>
      </form>

      <DialogFooter>
        <Button variant="outline" @click="close" :disabled="isSubmitting"> Cancel </Button>
        <Button @click="onSubmit" :disabled="isSubmitting">
          <Loader2 v-if="isSubmitting" class="mr-2 h-4 w-4 animate-spin" />
          Create Subscription
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { toast } from '@/components/ui/toast/use-toast'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { AlertCircle, Loader2, ChevronsUpDown, Check } from 'lucide-vue-next'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList
} from '@/components/ui/command'
import { parseApiError } from '@/lib/parseApiError'
import { useTracking } from '@/lib/tracking'
import { createSubscriptionSchema } from '../schemas'
import { useCreateSubscriptionMutation } from '../mutations'
import { useSubscriptionsQuery } from '../queries'
import type { Subscription } from '../types'
import { useCustomersQuery } from '@/features/customers/queries'
import { usePlansQuery } from '@/features/plans/queries'
import { useAccountSettingsQuery } from '@/features/integrations/queries'

defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const { track } = useTracking()
const { mutateAsync, isPending } = useCreateSubscriptionMutation()
const isSubmitting = computed(() => isPending.value)
const errorMessage = ref<string | null>(null)

const { defineField, handleSubmit, errors, resetForm } = useForm({
  validationSchema: toTypedSchema(createSubscriptionSchema)
})

const [customerId] = defineField('customerId')
const [planId] = defineField('planId')

const { data: customersData } = useCustomersQuery()
const { data: plansData } = usePlansQuery()
const { data: subscriptionsData } = useSubscriptionsQuery()
const { data: accountSettingsData } = useAccountSettingsQuery()

const customerDropdownOpen = ref(false)

const stripeMode = computed(() => accountSettingsData.value?.data?.stripeMode ?? 'NONE')

const customerIdsWithActiveSub = computed(() => {
  if (!subscriptionsData.value?.data) return new Set<string>()
  let subs: Subscription[] = []
  const data = subscriptionsData.value.data
  if (Array.isArray(data)) {
    subs = data
  }
  return new Set(subs.filter((s) => s.isActive).map((s) => s.customer.id))
})

const customers = computed(() => {
  const all = customersData.value?.data?.customers ?? []
  if (stripeMode.value === 'FULL_SYNC') {
    return all.filter((c: { id: string }) => !customerIdsWithActiveSub.value.has(c.id))
  }
  return all
})

const selectedCustomerLabel = computed(() => {
  if (!customerId.value) return ''
  const customer = customers.value.find((c: { id: string }) => c.id === customerId.value)
  if (!customer) return ''
  return `${customer.firstName} ${customer.lastName}`
})

function selectCustomer(id: string) {
  customerId.value = id
  customerDropdownOpen.value = false
}
const plans = computed(() =>
  (plansData.value?.data ?? []).filter((plan) => (plan.status ?? '').toLowerCase() === 'active')
)

const onSubmit = handleSubmit(async (values) => {
  try {
    errorMessage.value = null
    await mutateAsync(values)
    track('subscription_created')
    toast({ title: 'Success', description: 'Subscription created successfully' })
    resetForm()
    close()
  } catch (error) {
    const parsedError = parseApiError(error)
    errorMessage.value = parsedError.message
    toast({ title: 'Error', description: parsedError.message, variant: 'destructive' })
  }
})

function close() {
  resetForm()
  errorMessage.value = null
  emit('update:visible', false)
}
</script>
