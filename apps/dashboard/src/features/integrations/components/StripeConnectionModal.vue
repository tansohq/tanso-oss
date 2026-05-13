<template>
  <Dialog :open="visible" @update:open="emit('update:visible', $event)">
    <component
      :is="
        step === 'MAPPING' ||
        step === 'IMPORTING' ||
        step === 'IMPORT_COMPLETE' ||
        step === 'IMPORT_FAILED'
          ? DialogScrollContent
          : DialogContent
      "
      :class="
        step === 'MAPPING' ||
        step === 'IMPORTING' ||
        step === 'IMPORT_COMPLETE' ||
        step === 'IMPORT_FAILED'
          ? 'sm:max-w-2xl'
          : 'sm:max-w-[480px]'
      "
    >
      <!-- ENTER_KEY -->
      <template v-if="step === 'ENTER_KEY'">
        <DialogHeader>
          <DialogTitle>Connect to Stripe</DialogTitle>
          <DialogDescription>Paste your secret key to connect to Stripe.</DialogDescription>
        </DialogHeader>

        <div class="flex flex-col gap-4">
          <a
            :href="stripeKeysUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="inline-flex items-center gap-1 text-sm text-primary hover:underline w-fit"
          >
            Go to Stripe keys
            <ExternalLink class="w-3.5 h-3.5" />
          </a>

          <Input
            v-model="stripeApiKeyInput"
            type="password"
            class="font-mono text-sm"
            :placeholder="placeholder"
          />

          <Button class="w-full" :disabled="!stripeApiKeyInput.trim()" @click="handleConnect">
            Connect
          </Button>
        </div>
      </template>

      <!-- CONNECTING -->
      <template v-if="step === 'CONNECTING'">
        <DialogHeader>
          <DialogTitle>Connecting to Stripe</DialogTitle>
          <DialogDescription>Registering your key and setting up webhooks...</DialogDescription>
        </DialogHeader>
        <div class="flex items-center justify-center py-8">
          <Loader2 class="w-8 h-8 animate-spin text-primary" />
        </div>
      </template>

      <!-- DISCOVERING -->
      <template v-if="step === 'DISCOVERING'">
        <DialogHeader>
          <DialogTitle>Scanning Stripe</DialogTitle>
          <DialogDescription
            >Looking for products, customers, and subscriptions...</DialogDescription
          >
        </DialogHeader>
        <div class="flex items-center justify-center py-8">
          <Loader2 class="w-8 h-8 animate-spin text-primary" />
        </div>
      </template>

      <!-- DISCOVERY_EMPTY -->
      <template v-if="step === 'DISCOVERY_EMPTY'">
        <DialogHeader>
          <DialogTitle>Stripe Connected</DialogTitle>
          <DialogDescription>Your key has been saved successfully.</DialogDescription>
        </DialogHeader>
        <div class="flex flex-col items-center py-6 gap-3">
          <CheckCircle2 class="w-10 h-10 text-emerald-600" />
          <p class="text-sm text-muted-foreground">0 items found in Stripe.</p>
        </div>
        <DialogFooter>
          <Button @click="handleDone">Done</Button>
        </DialogFooter>
      </template>

      <!-- MAPPING -->
      <template v-if="step === 'MAPPING'">
        <DialogHeader>
          <DialogTitle>Import from Stripe</DialogTitle>
          <DialogDescription
            >Map your Stripe data to Tanso, then start the import.</DialogDescription
          >
        </DialogHeader>

        <div class="space-y-6">
          <!-- Discovery summary -->
          <div class="flex items-center justify-between px-1">
            <div class="flex gap-4 text-sm">
              <span
                ><span class="font-semibold text-foreground">{{ discovery!.products.length }}</span>
                <span class="text-muted-foreground">products</span></span
              >
              <span
                ><span class="font-semibold text-foreground">{{
                  discovery!.customers.length
                }}</span>
                <span class="text-muted-foreground">customers</span></span
              >
              <span
                ><span class="font-semibold text-foreground">{{
                  discovery!.subscriptions.length
                }}</span>
                <span class="text-muted-foreground">subscriptions</span></span
              >
            </div>
            <Button
              variant="ghost"
              size="sm"
              @click="handleRescan"
              :disabled="discoverMutation.isPending.value"
            >
              <Search class="w-3 h-3 mr-1.5" />
              Re-scan
            </Button>
          </div>

          <!-- Product Mapping -->
          <div>
            <h3 class="text-sm font-semibold mb-1">Products</h3>
            <p class="text-xs text-muted-foreground mb-3">
              Each product will be imported as a new plan, or you can map it to an existing one.
            </p>

            <div class="space-y-2">
              <div
                v-for="product in discovery!.products"
                :key="product.stripeProductId"
                class="flex items-center justify-between p-3 rounded-md border text-sm"
                :class="{
                  'border-amber-300 bg-amber-50/50':
                    showUnmappedWarning &&
                    !product.alreadyMapped &&
                    !isMapped(product.stripeProductId)
                }"
              >
                <div>
                  <span class="font-medium">{{ product.name }}</span>
                  <span class="text-muted-foreground ml-2 font-mono text-xs">{{
                    product.stripeProductId
                  }}</span>
                </div>
                <Badge
                  v-if="product.alreadyMapped"
                  variant="outline"
                  class="text-emerald-600 border-emerald-200 bg-emerald-50 text-xs"
                  >Already in Tanso</Badge
                >
                <div v-else-if="plansError" class="text-xs text-destructive">
                  Failed to load plans
                </div>
                <Select v-else v-model="productMappings[product.stripeProductId]">
                  <SelectTrigger class="w-[200px]">
                    <SelectValue :placeholder="plansLoading ? 'Loading plans...' : 'Create new'" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__create__">Create new</SelectItem>
                    <SelectItem v-for="plan in plans" :key="plan.id" :value="plan.id">
                      {{ plan.name }}
                    </SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>

          <!-- Customers -->
          <div>
            <div class="flex items-center justify-between mb-1">
              <h3 class="text-sm font-semibold">Customers ({{ discovery!.customers.length }})</h3>
              <button
                v-if="discovery!.customers.length > 3"
                @click="showCustomers = !showCustomers"
                class="text-xs text-muted-foreground hover:text-foreground"
              >
                {{ showCustomers ? 'Collapse' : 'Show all' }}
              </button>
            </div>
            <p class="text-xs text-muted-foreground mb-2">
              Customers linked to imported subscriptions will be created automatically.
            </p>

            <div v-if="showCustomers || discovery!.customers.length <= 3" class="relative">
              <div class="space-y-1.5 max-h-48 overflow-y-auto">
                <div
                  v-for="customer in discovery!.customers"
                  :key="customer.stripeCustomerId"
                  class="flex items-center justify-between p-2.5 rounded-md border text-sm"
                >
                  <div>
                    <span class="font-medium">{{
                      customer.name || customer.email || 'Unnamed'
                    }}</span>
                    <span class="text-muted-foreground ml-2 font-mono text-xs">{{
                      customer.stripeCustomerId
                    }}</span>
                  </div>
                  <Badge
                    v-if="customer.alreadyMapped"
                    variant="outline"
                    class="text-emerald-600 border-emerald-200 bg-emerald-50 text-xs"
                    >In Tanso</Badge
                  >
                  <Badge v-else variant="outline" class="text-muted-foreground text-xs"
                    >Will be created</Badge
                  >
                </div>
              </div>
            </div>
            <p v-else class="text-xs text-muted-foreground">
              {{ discovery!.customers.filter((c) => !c.alreadyMapped).length }} to create,
              {{ discovery!.customers.filter((c) => c.alreadyMapped).length }} already in Tanso
            </p>
          </div>

          <!-- Subscriptions -->
          <div>
            <div class="flex items-center justify-between mb-1">
              <h3 class="text-sm font-semibold">
                Subscriptions ({{ discovery!.subscriptions.length }})
              </h3>
              <button
                v-if="discovery!.subscriptions.length > 3"
                @click="showSubscriptions = !showSubscriptions"
                class="text-xs text-muted-foreground hover:text-foreground"
              >
                {{ showSubscriptions ? 'Collapse' : 'Show all' }}
              </button>
            </div>
            <p class="text-xs text-muted-foreground mb-2">
              Subscriptions on imported products will be brought in with their customer.
            </p>

            <div v-if="showSubscriptions || discovery!.subscriptions.length <= 3" class="relative">
              <div class="space-y-1.5 max-h-48 overflow-y-auto">
                <div
                  v-for="sub in discovery!.subscriptions"
                  :key="sub.stripeSubscriptionId"
                  class="flex items-center justify-between p-2.5 rounded-md border text-sm"
                >
                  <div class="min-w-0 flex-1">
                    <span class="font-mono text-xs">{{ sub.stripeSubscriptionId }}</span>
                    <Badge
                      variant="outline"
                      class="ml-2 text-xs"
                      :class="
                        sub.status === 'active'
                          ? 'text-emerald-600 border-emerald-200 bg-emerald-50'
                          : ''
                      "
                    >
                      {{ sub.status }}
                    </Badge>
                    <div class="text-xs text-muted-foreground mt-1">
                      <span>{{
                        resolveCustomerName(sub.stripeCustomerId) ?? sub.stripeCustomerId
                      }}</span>
                      <span v-if="sub.stripeProductId" class="ml-2"
                        >· {{ resolveProductName(sub.stripeProductId) }}</span
                      >
                    </div>
                  </div>
                  <Badge
                    v-if="sub.alreadyMapped"
                    variant="outline"
                    class="text-emerald-600 border-emerald-200 bg-emerald-50 ml-2 shrink-0 text-xs"
                    >In Tanso</Badge
                  >
                </div>
              </div>
            </div>
            <p v-else class="text-xs text-muted-foreground">
              {{ discovery!.subscriptions.filter((s) => s.status === 'active').length }} active,
              {{ discovery!.subscriptions.filter((s) => s.alreadyMapped).length }} already in Tanso
            </p>
          </div>
        </div>

        <DialogFooter>
          <p class="text-xs text-muted-foreground mr-auto">
            All products, customers, and subscriptions will be imported.
          </p>
          <Button
            @click="handleStartImport"
            :disabled="importMutation.isPending.value || !canStartImport"
          >
            <Upload class="w-4 h-4 mr-2" />
            Start Import
          </Button>
        </DialogFooter>
      </template>

      <!-- IMPORTING -->
      <template v-if="step === 'IMPORTING'">
        <DialogHeader>
          <DialogTitle>Importing</DialogTitle>
          <DialogDescription>Your Stripe data is being imported into Tanso.</DialogDescription>
        </DialogHeader>
        <div class="space-y-3 py-4">
          <div class="w-full bg-muted rounded-full h-2">
            <div
              class="bg-primary h-2 rounded-full transition-all"
              :style="{ width: progressPercent + '%' }"
            />
          </div>
          <div class="flex justify-between text-sm text-muted-foreground">
            <span
              >{{ importStatus?.processedItems ?? 0 }} / {{ importStatus?.totalItems ?? 0 }} items
              processed</span
            >
            <span v-if="importStatus && importStatus.failedItems > 0" class="text-destructive"
              >{{ importStatus.failedItems }} failed</span
            >
          </div>
        </div>
      </template>

      <!-- IMPORT_COMPLETE -->
      <template v-if="step === 'IMPORT_COMPLETE'">
        <DialogHeader>
          <DialogTitle>Import Complete</DialogTitle>
          <DialogDescription>Your Stripe data has been imported successfully.</DialogDescription>
        </DialogHeader>
        <div class="flex flex-col items-center py-6 gap-3">
          <CheckCircle2 class="w-10 h-10 text-emerald-600" />
          <p class="text-sm text-muted-foreground">
            {{ importStatus?.processedItems ?? 0 }} items imported.
            <span v-if="importStatus && importStatus.failedItems > 0" class="text-destructive"
              >{{ importStatus.failedItems }} failed.</span
            >
          </p>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="handleReimport">Re-import</Button>
          <Button @click="handleDone">Done</Button>
        </DialogFooter>
      </template>

      <!-- IMPORT_FAILED -->
      <template v-if="step === 'IMPORT_FAILED'">
        <DialogHeader>
          <DialogTitle>Import Failed</DialogTitle>
          <DialogDescription>Something went wrong during the import.</DialogDescription>
        </DialogHeader>
        <div class="py-4 space-y-3">
          <div class="text-sm text-destructive p-3 rounded-md bg-destructive/10">
            {{ importStatus?.errorDetails ?? 'An unknown error occurred.' }}
          </div>
          <div class="flex justify-between text-sm text-muted-foreground">
            <span
              >{{ importStatus?.processedItems ?? 0 }} / {{ importStatus?.totalItems ?? 0 }} items
              processed</span
            >
            <span v-if="importStatus && importStatus.failedItems > 0" class="text-destructive"
              >{{ importStatus.failedItems }} failed</span
            >
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="handleReimport">Retry</Button>
          <Button @click="handleDone">Close</Button>
        </DialogFooter>
      </template>
    </component>
  </Dialog>

  <!-- Re-discovery confirmation -->
  <AlertDialog v-model:open="showRediscoverDialog">
    <AlertDialogContent>
      <AlertDialogHeader>
        <AlertDialogTitle>Re-scan Stripe?</AlertDialogTitle>
        <AlertDialogDescription>
          This will clear your current product mappings. Continue?
        </AlertDialogDescription>
      </AlertDialogHeader>
      <AlertDialogFooter>
        <AlertDialogCancel>Cancel</AlertDialogCancel>
        <AlertDialogAction @click="executeDiscover">Continue</AlertDialogAction>
      </AlertDialogFooter>
    </AlertDialogContent>
  </AlertDialog>

  <!-- Import confirmation -->
  <AlertDialog v-model:open="showImportDialog">
    <AlertDialogContent>
      <AlertDialogHeader>
        <AlertDialogTitle>Start Import?</AlertDialogTitle>
        <AlertDialogDescription>
          This will create the following in Tanso:
          <ul class="mt-2 list-disc list-inside text-sm">
            <li v-if="mappedProductCount > 0">
              {{ mappedProductCount }} product(s) mapped to existing plans
            </li>
            <li v-if="unmappedProductCount > 0">
              {{ unmappedProductCount }} product(s) will create new plans
            </li>
            <li>{{ unmappedCustomerCount }} customer(s) to be created</li>
            <li>{{ unmappedSubscriptionCount }} subscription(s)</li>
          </ul>
        </AlertDialogDescription>
      </AlertDialogHeader>
      <AlertDialogFooter>
        <AlertDialogCancel>Cancel</AlertDialogCancel>
        <AlertDialogAction @click="handleStartImport">Start Import</AlertDialogAction>
      </AlertDialogFooter>
    </AlertDialogContent>
  </AlertDialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import {
  Dialog,
  DialogContent,
  DialogScrollContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from '@/components/ui/alert-dialog'
import { ExternalLink, Loader2, CheckCircle2, Search, Upload } from 'lucide-vue-next'
import { useEnvironmentStore } from '@/stores/environment'
import {
  useRegisterStripeApiKeyMutation,
  useRegisterStripeWebhookMutation,
  useUpdateAccountSettingsMutation,
  useDiscoverStripeMutation,
  useStartAutoCreateStripeImportMutation
} from '../mutations'
import { useStripeImportStatusQuery } from '../queries'
import { usePlansQuery } from '@/features/plans/queries'
import { toast } from '@/components/ui/toast/use-toast'
import { parseApiError } from '@/lib/parseApiError'
import { useTracking } from '@/lib/tracking'
import type { StripeDiscoveryResponse } from '../api'

type Step =
  | 'ENTER_KEY'
  | 'CONNECTING'
  | 'DISCOVERING'
  | 'DISCOVERY_EMPTY'
  | 'MAPPING'
  | 'IMPORTING'
  | 'IMPORT_COMPLETE'
  | 'IMPORT_FAILED'

const props = defineProps<{
  visible: boolean
  skipToDiscovery?: boolean
}>()
const emit = defineEmits<{
  'update:visible': [value: boolean]
  connected: []
}>()

const { track } = useTracking()
const queryClient = useQueryClient()
const environmentStore = useEnvironmentStore()
const registerKeyMutation = useRegisterStripeApiKeyMutation()
const registerWebhookMutation = useRegisterStripeWebhookMutation()
const updateSettingsMutation = useUpdateAccountSettingsMutation()
const discoverMutation = useDiscoverStripeMutation()
const importMutation = useStartAutoCreateStripeImportMutation()
const { data: plansData, isLoading: plansLoading, isError: plansError } = usePlansQuery()

const plans = computed(() => plansData.value?.data ?? [])

const step = ref<Step>('ENTER_KEY')
const stripeApiKeyInput = ref('')
const discovery = ref<StripeDiscoveryResponse | null>(null)
const productMappings = ref<Record<string, string>>({})
const importJobId = ref<string | null>(null)
const pollCount = ref(0)
const MAX_POLL_COUNT = 300
const showRediscoverDialog = ref(false)
const showImportDialog = ref(false)
const showUnmappedWarning = ref(false)
const showCustomers = ref(false)
const showSubscriptions = ref(false)

// Reset state when modal opens
watch(
  () => props.visible,
  (isVisible) => {
    if (isVisible) {
      if (props.skipToDiscovery) {
        step.value = 'DISCOVERING'
        executeDiscover()
      } else {
        step.value = 'ENTER_KEY'
        stripeApiKeyInput.value = ''
      }
      discovery.value = null
      productMappings.value = {}
      importJobId.value = null
      pollCount.value = 0
      showUnmappedWarning.value = false
      showCustomers.value = false
      showSubscriptions.value = false
    }
  }
)

// Import status polling
const shouldPollStatus = computed(() => {
  if (!importJobId.value) return false
  if (pollCount.value >= MAX_POLL_COUNT) return false
  const status = importStatusData.value?.data?.status
  if (status === 'COMPLETED' || status === 'FAILED') return false
  return true
})

const { data: importStatusData } = useStripeImportStatusQuery(importJobId, shouldPollStatus)
const importStatus = computed(() => importStatusData.value?.data ?? null)

watch(importStatusData, () => {
  if (shouldPollStatus.value) pollCount.value++
})

// Auto-advance from IMPORTING when status changes
watch(importStatus, (status) => {
  if (!status || step.value !== 'IMPORTING') return
  if (status.status === 'COMPLETED') step.value = 'IMPORT_COMPLETE'
  else if (status.status === 'FAILED') step.value = 'IMPORT_FAILED'
})

const progressPercent = computed(() => {
  if (!importStatus.value || importStatus.value.totalItems === 0) return 0
  return Math.round((importStatus.value.processedItems / importStatus.value.totalItems) * 100)
})

const stripeKeysUrl = computed(() =>
  environmentStore.isSandbox
    ? 'https://dashboard.stripe.com/test/apikeys'
    : 'https://dashboard.stripe.com/apikeys'
)

const placeholder = computed(() => (environmentStore.isSandbox ? 'sk_test_...' : 'sk_live_...'))

const discoveryIsEmpty = computed(() => {
  if (!discovery.value) return false
  return (
    discovery.value.products.length === 0 &&
    discovery.value.customers.length === 0 &&
    discovery.value.subscriptions.length === 0
  )
})

function isMapped(stripeProductId: string): boolean {
  const val = productMappings.value[stripeProductId]
  return !!val && val !== '__none__'
}

const mappedProductCount = computed(
  () => Object.values(productMappings.value).filter((v) => v && v !== '__none__').length
)

const unmappedProductCount = computed(
  () =>
    discovery.value?.products.filter((p) => !p.alreadyMapped && !isMapped(p.stripeProductId))
      .length ?? 0
)

const unmappedCustomerCount = computed(
  () => discovery.value?.customers.filter((c) => !c.alreadyMapped).length ?? 0
)

const unmappedSubscriptionCount = computed(
  () => discovery.value?.subscriptions.filter((s) => !s.alreadyMapped).length ?? 0
)

const canStartImport = computed(() => {
  if (!discovery.value) return false
  return discovery.value.products.length > 0
})

watch(canStartImport, (canImport) => {
  if (canImport) showUnmappedWarning.value = false
})

function resolveCustomerName(stripeCustomerId: string): string | null {
  const customer = discovery.value?.customers.find((c) => c.stripeCustomerId === stripeCustomerId)
  if (!customer) return null
  return customer.name || customer.email || null
}

function resolveProductName(stripeProductId: string): string {
  const product = discovery.value?.products.find((p) => p.stripeProductId === stripeProductId)
  return product?.name ?? stripeProductId
}

async function handleConnect() {
  if (!stripeApiKeyInput.value.trim()) return
  step.value = 'CONNECTING'
  try {
    await registerKeyMutation.mutateAsync(stripeApiKeyInput.value.trim())
    await registerWebhookMutation.mutateAsync()
    await updateSettingsMutation.mutateAsync({ stripeMode: 'STRIPE_INTEGRATION' })
    stripeApiKeyInput.value = ''
    track('stripe_connected')
    emit('connected')

    // Auto-discover after connecting
    step.value = 'DISCOVERING'
    await executeDiscover()
  } catch (error) {
    const parsed = parseApiError(error)
    toast({ title: 'Connection failed', description: parsed.message, variant: 'destructive' })
    step.value = 'ENTER_KEY'
  }
}

async function executeDiscover() {
  showRediscoverDialog.value = false
  step.value = 'DISCOVERING'
  try {
    const result = await discoverMutation.mutateAsync({
      includeProducts: true,
      includeCustomers: true,
      includeSubscriptions: true
    })
    discovery.value = result.data
    productMappings.value = {}
    importJobId.value = null
    pollCount.value = 0
    showUnmappedWarning.value = false
    showCustomers.value = false
    showSubscriptions.value = false

    if (discoveryIsEmpty.value) {
      step.value = 'DISCOVERY_EMPTY'
    } else {
      step.value = 'MAPPING'
    }
  } catch (error) {
    const parsed = parseApiError(error)
    toast({ title: 'Discovery failed', description: parsed.message, variant: 'destructive' })
    // Go back to enter key if we were in the connect flow, otherwise close
    if (props.skipToDiscovery) {
      handleDone()
    } else {
      step.value = 'ENTER_KEY'
    }
  }
}

function handleRescan() {
  const hasMappings = Object.values(productMappings.value).some((v) => v && v !== '__none__')
  if (hasMappings) {
    showRediscoverDialog.value = true
    return
  }
  executeDiscover()
}

async function handleStartImport() {
  showImportDialog.value = false

  try {
    const result = await importMutation.mutateAsync()
    pollCount.value = 0
    importJobId.value = result.data.jobId
    step.value = 'IMPORTING'
    toast({ title: 'Import started', description: 'Your Stripe data is being imported.' })
  } catch (error) {
    const parsed = parseApiError(error)
    toast({ title: 'Import failed', description: parsed.message, variant: 'destructive' })
  }
}

async function handleReimport() {
  importJobId.value = null
  await executeDiscover()
}

function handleDone() {
  queryClient.invalidateQueries({ queryKey: ['account-settings'] })
  queryClient.invalidateQueries({ queryKey: ['stripe-keys'] })
  emit('update:visible', false)
}
</script>
