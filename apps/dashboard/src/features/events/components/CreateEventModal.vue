<template>
  <Dialog :open="visible" @update:open="emit('update:visible', $event)">
    <DialogContent class="sm:max-w-[600px]">
      <DialogHeader>
        <DialogTitle>Submit test event</DialogTitle>
      </DialogHeader>

      <form @submit.prevent="onSubmit" class="flex flex-col gap-5">
        <Alert>
          <Info class="h-4 w-4" />
          <AlertDescription>
            <span class="font-medium">Sandbox tool</span> — This submits a manual usage event for testing entitlement behavior.
          </AlertDescription>
        </Alert>

        <div class="grid grid-cols-2 gap-4">
          <div class="flex flex-col gap-2">
            <Label for="customerReferenceId">Customer</Label>
            <Popover v-model:open="customerDropdownOpen">
              <PopoverTrigger as-child>
                <Button
                  variant="outline"
                  role="combobox"
                  :aria-expanded="customerDropdownOpen"
                  class="justify-between font-normal"
                  :class="[hasAttemptedSubmit && errors.customerReferenceId ? 'border-destructive' : '', !customerReferenceId ? 'text-muted-foreground' : '']"
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
                    <CommandGroup v-if="customersWithSub.length > 0" heading="With active subscription">
                      <CommandItem
                        v-for="c in customersWithSub"
                        :key="c.referenceId"
                        :value="`${c.firstName} ${c.lastName} ${c.email}`"
                        @select="selectCustomer(c.referenceId)"
                      >
                        <Check class="mr-2 h-4 w-4 shrink-0" :class="customerReferenceId === c.referenceId ? 'opacity-100' : 'opacity-0'" />
                        <div class="flex flex-col">
                          <span class="text-sm">{{ c.firstName }} {{ c.lastName }}</span>
                          <span class="text-xs text-muted-foreground">{{ c.email }}</span>
                        </div>
                      </CommandItem>
                    </CommandGroup>
                    <CommandSeparator v-if="customersWithSub.length > 0 && customersWithoutSub.length > 0" />
                    <CommandGroup v-if="customersWithoutSub.length > 0" heading="No active subscription">
                      <CommandItem
                        v-for="c in customersWithoutSub"
                        :key="c.referenceId"
                        :value="`${c.firstName} ${c.lastName} ${c.email}`"
                        @select="selectCustomer(c.referenceId)"
                      >
                        <Check class="mr-2 h-4 w-4 shrink-0" :class="customerReferenceId === c.referenceId ? 'opacity-100' : 'opacity-0'" />
                        <div class="flex flex-col">
                          <span class="text-sm text-muted-foreground">{{ c.firstName }} {{ c.lastName }}</span>
                          <span class="text-xs text-muted-foreground/60">{{ c.email }}</span>
                        </div>
                      </CommandItem>
                    </CommandGroup>
                  </CommandList>
                </Command>
              </PopoverContent>
            </Popover>
            <span v-if="hasAttemptedSubmit && errors.customerReferenceId" class="text-destructive text-xs">
              {{ errors.customerReferenceId }}
            </span>
            <p v-if="selectedCustomerHasNoSub" class="text-xs text-amber-600 flex items-center gap-1">
              <AlertTriangle class="h-3 w-3 shrink-0" />
              This customer has no active subscription. The event may be rejected.
            </p>
          </div>

          <div class="flex flex-col gap-2">
            <Label for="eventName">Event Name *</Label>
            <Input
              id="eventName"
              v-model="eventName"
              :class="{ 'border-destructive': hasAttemptedSubmit && errors.eventName }"
              placeholder="e.g., api_call"
            />
            <span v-if="hasAttemptedSubmit && errors.eventName" class="text-destructive text-xs">
              {{ errors.eventName }}
            </span>
          </div>

          <div class="flex flex-col gap-2">
            <Label for="featureKey">Feature *</Label>
            <Select
              :model-value="featureKey"
              @update:model-value="(val) => onFeatureSelected(val as string)"
            >
              <SelectTrigger :class="{ 'border-destructive': hasAttemptedSubmit && errors.featureKey }">
                <SelectValue placeholder="Select a feature" />
              </SelectTrigger>
              <SelectContent>
                <template v-if="featuresInPlan.length > 0">
                  <SelectGroup>
                    <SelectLabel>Included in {{ selectedPlanName }}</SelectLabel>
                    <SelectItem v-for="f in featuresInPlan" :key="f.key" :value="f.key">
                      {{ f.name }} ({{ f.key }})
                    </SelectItem>
                  </SelectGroup>
                  <SelectSeparator v-if="featuresNotInPlan.length > 0" />
                  <SelectGroup v-if="featuresNotInPlan.length > 0">
                    <SelectLabel>Not in plan</SelectLabel>
                    <SelectItem v-for="f in featuresNotInPlan" :key="f.key" :value="f.key" class="text-muted-foreground">
                      {{ f.name }} ({{ f.key }})
                    </SelectItem>
                  </SelectGroup>
                </template>
                <template v-else>
                  <SelectItem v-for="f in features" :key="f.key" :value="f.key">
                    {{ f.name }} ({{ f.key }})
                  </SelectItem>
                </template>
              </SelectContent>
            </Select>
            <span v-if="hasAttemptedSubmit && errors.featureKey" class="text-destructive text-xs">
              {{ errors.featureKey }}
            </span>
          </div>

          <div class="flex flex-col gap-2">
            <Label for="occurredAt">Occurred At *</Label>
            <Input
              id="occurredAt"
              v-model="occurredAt"
              type="datetime-local"
              step="1"
              :class="{ 'border-destructive': hasAttemptedSubmit && errors.occurredAt }"
            />
            <span v-if="hasAttemptedSubmit && errors.occurredAt" class="text-destructive text-xs">
              {{ errors.occurredAt }}
            </span>
          </div>

          <div class="flex flex-col gap-2">
            <Label for="usageUnits">Usage Units</Label>
            <Input
              id="usageUnits"
              v-model="usageUnits"
              type="number"
              step="any"
              :class="{ 'border-destructive': hasAttemptedSubmit && errors.usageUnits }"
              placeholder="e.g., 1"
            />
            <span v-if="hasAttemptedSubmit && errors.usageUnits" class="text-destructive text-xs">
              {{ errors.usageUnits }}
            </span>
          </div>

          <div class="flex flex-col gap-2">
            <Label for="costAmount">Cost Amount</Label>
            <Input
              id="costAmount"
              v-model="costAmount"
              type="number"
              step="any"
              placeholder="e.g., 0.05"
            />
          </div>

          <Collapsible v-model:open="costInputOpen" class="col-span-2">
            <CollapsibleTrigger as-child>
              <Button variant="ghost" size="sm" class="gap-1 px-0 text-muted-foreground">
                <ChevronRight class="h-4 w-4 transition-transform" :class="{ 'rotate-90': costInputOpen }" />
                AI Model Cost Input
              </Button>
            </CollapsibleTrigger>
            <CollapsibleContent>
              <div class="grid grid-cols-2 gap-4 pt-2">
                <div class="flex flex-col gap-2">
                  <Label for="costInputModel">Model</Label>
                  <Input
                    id="costInputModel"
                    v-model="costInputModel"
                    placeholder="e.g., gpt-4"
                  />
                </div>
                <div class="flex flex-col gap-2">
                  <Label for="costInputProvider">Provider</Label>
                  <Input
                    id="costInputProvider"
                    v-model="costInputProvider"
                    placeholder="e.g., openai"
                  />
                </div>
                <div class="flex flex-col gap-2">
                  <Label for="costInputInputTokens">Input Tokens</Label>
                  <Input
                    id="costInputInputTokens"
                    v-model="costInputInputTokens"
                    type="number"
                    step="any"
                    placeholder="e.g., 3000"
                  />
                </div>
                <div class="flex flex-col gap-2">
                  <Label for="costInputOutputTokens">Output Tokens</Label>
                  <Input
                    id="costInputOutputTokens"
                    v-model="costInputOutputTokens"
                    type="number"
                    step="any"
                    placeholder="e.g., 500"
                  />
                </div>
              </div>
            </CollapsibleContent>
          </Collapsible>

          <div class="flex flex-col gap-2 col-span-2">
            <Label for="meta">Metadata (JSON)</Label>
            <Textarea
              id="meta"
              v-model="meta"
              :class="{ 'border-destructive': hasAttemptedSubmit && errors.meta }"
              placeholder='e.g., {"source": "dashboard"}'
              rows="2"
            />
            <span v-if="hasAttemptedSubmit && errors.meta" class="text-destructive text-xs">
              {{ errors.meta }}
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
          Submit event
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { toast } from '@/components/ui/toast/use-toast'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectSeparator,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator
} from '@/components/ui/command'
import { useCustomersQuery } from '@/features/customers/queries'
import { useFeaturesQuery } from '@/features/features/queries'
import { useSubscriptionsQuery } from '@/features/subscriptions/queries'
import { usePlanFeaturesQuery } from '@/features/plans/queries'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { AlertCircle, AlertTriangle, ChevronRight, Info, Loader2, ChevronsUpDown, Check } from 'lucide-vue-next'
import { parseApiError } from '@/lib/parseApiError'
import { useTracking } from '@/lib/tracking'
import { createEventSchema } from '../schemas'
import { useCreateEventMutation } from '../mutations'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

function toLocalDatetimeString(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const router = useRouter()
const { track } = useTracking()
const { mutateAsync, isPending } = useCreateEventMutation()
const isSubmitting = computed(() => isPending.value)
const errorMessage = ref<string | null>(null)

const { data: customersData } = useCustomersQuery()
const { data: featuresData } = useFeaturesQuery()
const { data: subscriptionsData } = useSubscriptionsQuery()

// Form setup — must come before computed properties that reference form fields
const { defineField, handleSubmit, errors, resetForm, setFieldValue } = useForm({
  validationSchema: toTypedSchema(createEventSchema),
  initialValues: {
    eventIdempotencyKey: crypto.randomUUID(),
    occurredAt: toLocalDatetimeString(new Date())
  }
})

const [featureKey] = defineField('featureKey')
defineField('featureId')
const [eventName] = defineField('eventName')
const [customerReferenceId] = defineField('customerReferenceId')
defineField('eventIdempotencyKey')
const [occurredAt] = defineField('occurredAt')
const [usageUnits] = defineField('usageUnits')
const [costAmount] = defineField('costAmount')
const [costInputModel] = defineField('costInput.model')
const [costInputProvider] = defineField('costInput.modelProvider')
const [costInputInputTokens] = defineField('costInput.inputTokens')
const [costInputOutputTokens] = defineField('costInput.outputTokens')
const [meta] = defineField('meta')

const costInputOpen = ref(false)

const customerIdsWithActiveSub = computed(() => {
  const data = subscriptionsData.value?.data
  if (!data) return new Set<string>()
  const subs = Array.isArray(data) ? data : []
  return new Set(subs.filter((s) => s.isActive).map((s) => s.customer.id))
})

const allCustomers = computed(() => customersData.value?.data?.customers ?? [])

const customersWithSub = computed(() =>
  allCustomers.value.filter((c: { id: string }) => customerIdsWithActiveSub.value.has(c.id))
)

const customersWithoutSub = computed(() =>
  allCustomers.value.filter((c: { id: string }) => !customerIdsWithActiveSub.value.has(c.id))
)

const customerDropdownOpen = ref(false)

const selectedCustomerLabel = computed(() => {
  if (!customerReferenceId.value) return ''
  const c = allCustomers.value.find((c: { referenceId: string }) => c.referenceId === customerReferenceId.value)
  if (!c) return ''
  return `${c.firstName} ${c.lastName}`
})

const selectedCustomerHasNoSub = computed(() => {
  if (!customerReferenceId.value) return false
  const c = allCustomers.value.find((c: { referenceId: string }) => c.referenceId === customerReferenceId.value)
  if (!c) return false
  return !customerIdsWithActiveSub.value.has(c.id)
})

function selectCustomer(referenceId: string) {
  setFieldValue('customerReferenceId', referenceId)
  setFieldValue('featureKey', '')
  setFieldValue('featureId', undefined)
  customerDropdownOpen.value = false
}

const features = computed(() => featuresData.value?.data ?? [])

// Find the selected customer's active subscription and plan
const selectedCustomerActiveSub = computed(() => {
  if (!customerReferenceId.value) return null
  const customer = allCustomers.value.find((c: { referenceId: string }) => c.referenceId === customerReferenceId.value)
  if (!customer) return null
  const data = subscriptionsData.value?.data
  const subs = Array.isArray(data) ? data : []
  return subs.find((s) => s.isActive && s.customer.id === customer.id) ?? null
})

const selectedPlanId = computed(() => selectedCustomerActiveSub.value?.plan?.id ?? '')
const selectedPlanName = computed(() => selectedCustomerActiveSub.value?.plan?.name ?? '')

const { data: planFeaturesData } = usePlanFeaturesQuery(selectedPlanId)

const planFeatureKeys = computed(() => {
  const pf = planFeaturesData.value?.data?.features
  if (!pf) return new Set<string>()
  return new Set(pf.map((f) => f.key))
})

const featuresInPlan = computed(() =>
  features.value.filter((f) => planFeatureKeys.value.has(f.key))
)

const featuresNotInPlan = computed(() =>
  features.value.filter((f) => !planFeatureKeys.value.has(f.key))
)

function onFeatureSelected(key: string) {
  setFieldValue('featureKey', key)
  const feature = features.value.find((f) => f.key === key)
  setFieldValue('featureId', feature?.id)
}

// Re-generate defaults each time the modal opens
watch(
  () => props.visible,
  (isVisible) => {
    if (isVisible) {
      resetForm({
        values: {
          featureKey: '',
          eventName: '',
          customerReferenceId: '',
          eventIdempotencyKey: crypto.randomUUID(),
          occurredAt: toLocalDatetimeString(new Date()),
          usageUnits: undefined,
          costAmount: undefined,
          costInput: undefined,
          meta: ''
        }
      })
      errorMessage.value = null
      hasAttemptedSubmit.value = false
      costInputOpen.value = false
    }
  }
)

const hasAttemptedSubmit = ref(false)

const doSubmit = handleSubmit(async (values) => {
  try {
    errorMessage.value = null

    // Convert local datetime to ISO string for the API
    const occurredAtIso = new Date(values.occurredAt).toISOString()

    await mutateAsync({
      ...values,
      occurredAt: occurredAtIso
    })
    track('test_event_submitted')
    setFieldValue('eventIdempotencyKey', crypto.randomUUID())
    toast({
      title: 'Success',
      description: h('span', {}, [
        'Event created. ',
        h('a', {
          class: 'underline cursor-pointer font-medium',
          onClick: () => router.push('/events')
        }, 'View in Events \u2192')
      ])
    })
    close()
  } catch (error) {
    const parsedError = parseApiError(error)
    errorMessage.value = parsedError.message
    toast({ title: 'Error', description: parsedError.message, variant: 'destructive' })
  }
})

function onSubmit() {
  hasAttemptedSubmit.value = true
  doSubmit()
}

function close() {
  errorMessage.value = null
  emit('update:visible', false)
}
</script>
