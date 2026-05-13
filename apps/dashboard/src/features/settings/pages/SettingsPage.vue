<template>
  <div class="p-6 pb-16 max-w-3xl">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight text-foreground">Settings</h1>
        <p class="text-sm text-muted-foreground mt-1">Configure your account preferences</p>
      </div>
    </div>

    <Tabs :model-value="activeTab" @update:model-value="handleTabChange">
      <TabsList>
        <TabsTrigger value="general">General</TabsTrigger>
        <TabsTrigger value="integrations">Integrations</TabsTrigger>
        <TabsTrigger value="data-import">Data Import</TabsTrigger>
        <TabsTrigger v-if="isDeveloperEnvironment" value="developer">Developer</TabsTrigger>
      </TabsList>

      <!-- General Tab -->
      <TabsContent value="general" class="space-y-6 mt-6">
        <div class="bg-card rounded-lg border p-6">
          <h3 class="text-base font-medium mb-4">Account</h3>
          <div class="grid grid-cols-2 gap-4">
            <div class="flex flex-col gap-0.5">
              <span class="text-xs text-muted-foreground">Email</span>
              <span class="text-sm font-medium">{{ authStore.userEmail || '-' }}</span>
            </div>
            <div class="flex flex-col gap-0.5">
              <span class="text-xs text-muted-foreground">Role</span>
              <span class="text-sm font-medium">{{ displayRole }}</span>
            </div>
          </div>

          <div class="border-t mt-4 pt-4">
            <h3 class="text-base font-medium mb-3">Current Plan</h3>
            <div
              v-if="isLoadingSubscription"
              class="flex items-center gap-2 text-sm text-muted-foreground"
            >
              <Loader2 class="w-4 h-4 animate-spin" />
              Loading plan information...
            </div>
            <div v-else-if="isMasterAccount" class="text-sm text-muted-foreground">
              Not applicable - platform operator account
            </div>
            <div v-else-if="subscriptionData?.data?.planName">
              <div class="grid grid-cols-2 gap-4">
                <div class="flex flex-col gap-0.5">
                  <span class="text-xs text-muted-foreground">Plan</span>
                  <span class="text-sm font-medium">{{ subscriptionData.data.planName }}</span>
                </div>
                <div class="flex flex-col gap-0.5">
                  <span class="text-xs text-muted-foreground">Price</span>
                  <span class="text-sm font-medium"
                    >${{ subscriptionData.data.planPriceAmount ?? 0 }}/month</span
                  >
                </div>
              </div>
            </div>
            <div v-else class="text-sm text-muted-foreground">No active plan</div>
          </div>
        </div>

        <div class="bg-card rounded-lg border p-6">
          <h3 class="text-base font-medium mb-1">API Key</h3>
          <p class="text-xs text-muted-foreground mb-3">
            Use this key to authenticate requests to the Tanso API.
          </p>
          <div v-if="isLoadingApiKey" class="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 class="w-4 h-4 animate-spin" />
            Loading API key...
          </div>
          <div v-else-if="apiKeyData?.data?.apiKey" class="flex items-center gap-2">
            <div
              class="flex-1 bg-muted px-3 py-1.5 rounded-md font-mono text-xs border flex items-center gap-2 min-w-0"
            >
              <KeyIcon class="w-3.5 h-3.5 text-muted-foreground shrink-0" />
              <span class="truncate">{{ showApiKey ? apiKeyData.data.apiKey : maskedApiKey }}</span>
            </div>
            <Button
              variant="ghost"
              size="icon"
              class="h-8 w-8 shrink-0"
              @click="showApiKey = !showApiKey"
              :aria-label="showApiKey ? 'Hide API key' : 'Show API key'"
            >
              <EyeOff v-if="showApiKey" class="w-3.5 h-3.5" />
              <Eye v-else class="w-3.5 h-3.5" />
            </Button>
            <CopyButton :value="apiKeyData.data.apiKey" label="API Key" />
          </div>
          <div v-else class="text-sm text-destructive">Failed to load API key</div>
        </div>

        <ChangePasswordForm />

        <div class="pt-1">
          <Button variant="outline" size="sm" @click="handleLogout">
            <LogOut class="w-3.5 h-3.5 mr-1.5" />
            Log out
          </Button>
        </div>
      </TabsContent>

      <!-- Integrations Tab (Platform only) -->
      <TabsContent value="integrations" class="space-y-6 mt-6">
        <div class="bg-card rounded-lg border shadow-sm p-6">
          <div class="flex items-center gap-3 mb-4">
            <CreditCard class="w-5 h-5 text-muted-foreground" />
            <h3 class="text-base font-medium">Stripe</h3>
          </div>

          <div
            v-if="isLoadingSettings"
            class="flex items-center gap-2 text-sm text-muted-foreground"
          >
            <Loader2 class="w-4 h-4 animate-spin" />
            Loading settings...
          </div>

          <!-- Connected -->
          <div v-else-if="isStripeConnected" class="space-y-6">
            <div class="flex items-center gap-3 text-sm">
              <div class="flex items-center gap-2 flex-1">
                <CheckCircle2 class="w-4 h-4 text-emerald-600" aria-hidden="true" />
                <span class="font-semibold text-emerald-600">Connected</span>
                <span class="font-mono text-xs text-muted-foreground">{{ maskedStripeKey }}</span>
              </div>
              <Button
                variant="outline"
                size="sm"
                @click="
                  showImportConfirmDialog = true
                  track('stripe_import_started')
                "
              >
                Import from Stripe
              </Button>
              <Button
                variant="outline"
                size="sm"
                class="text-destructive border-destructive/30 hover:text-destructive hover:bg-destructive/10"
                @click="showDisconnectDialog = true"
                :disabled="isDisconnecting"
              >
                <Loader2 v-if="isDisconnecting" class="w-3 h-3 mr-1 animate-spin" />
                Disconnect
              </Button>
            </div>

            <!-- Checkout Redirect URLs -->
            <div class="border-t pt-6 space-y-3">
              <h4 class="text-sm font-semibold">Checkout Redirect URLs</h4>
              <p class="text-xs text-muted-foreground">
                Where Stripe redirects your customers after checkout.
              </p>
              <div class="space-y-2">
                <div>
                  <div class="inline-flex items-center gap-1">
                    <Label for="checkout-success-url" class="text-xs text-muted-foreground"
                      >Success URL *</Label
                    >
                    <Tooltip>
                      <TooltipTrigger as-child>
                        <HelpCircle class="h-3.5 w-3.5 text-muted-foreground/50" />
                      </TooltipTrigger>
                      <TooltipContent side="top" class="max-w-xs text-xs">
                        Where Stripe sends your customer after a successful payment.
                      </TooltipContent>
                    </Tooltip>
                  </div>
                  <Input
                    id="checkout-success-url"
                    v-model="checkoutSuccessUrl"
                    type="url"
                    placeholder="https://example.com/success"
                    class="font-mono text-sm mt-1"
                    :disabled="isSavingUrls"
                  />
                  <p v-if="successUrlError" class="text-xs text-destructive mt-1">
                    {{ successUrlError }}
                  </p>
                </div>
                <div>
                  <div class="inline-flex items-center gap-1">
                    <Label for="checkout-cancel-url" class="text-xs text-muted-foreground"
                      >Cancel URL *</Label
                    >
                    <Tooltip>
                      <TooltipTrigger as-child>
                        <HelpCircle class="h-3.5 w-3.5 text-muted-foreground/50" />
                      </TooltipTrigger>
                      <TooltipContent side="top" class="max-w-xs text-xs">
                        Where Stripe sends your customer if they leave mid-checkout.
                      </TooltipContent>
                    </Tooltip>
                  </div>
                  <Input
                    id="checkout-cancel-url"
                    v-model="checkoutCancelUrl"
                    type="url"
                    placeholder="https://example.com/cancel"
                    class="font-mono text-sm mt-1"
                    :disabled="isSavingUrls"
                  />
                  <p v-if="cancelUrlError" class="text-xs text-destructive mt-1">
                    {{ cancelUrlError }}
                  </p>
                </div>
                <div class="pt-2">
                  <Button
                    size="sm"
                    @click="handleSaveUrls"
                    :disabled="isSavingUrls || !urlsChanged"
                  >
                    <Loader2 v-if="isSavingUrls" class="w-3 h-3 mr-1 animate-spin" />
                    Save URLs
                  </Button>
                </div>
              </div>
            </div>
          </div>

          <!-- Not connected -->
          <div v-else-if="accountSettings" class="space-y-4">
            <p class="text-sm text-muted-foreground">
              Connect Stripe to sync products, customers, and subscriptions.
            </p>
            <Button
              @click="
                showStripeModal = true
                track('stripe_connection_started')
              "
              >Connect Stripe</Button
            >
          </div>
        </div>

        <!-- Import confirmation dialog -->
        <AlertDialog
          :open="showImportConfirmDialog"
          @update:open="showImportConfirmDialog = $event"
        >
          <AlertDialogContent class="sm:max-w-[450px]">
            <AlertDialogHeader>
              <AlertDialogTitle>Import from Stripe</AlertDialogTitle>
              <AlertDialogDescription>
                This will scan your Stripe account and import any new products, customers, and
                subscriptions into Tanso. Existing data will not be overwritten or duplicated.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <Button
                @click="
                  showImportConfirmDialog = false
                  stripeModalSkipToDiscovery = true
                  showStripeModal = true
                "
              >
                Continue
              </Button>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>

        <!-- Disconnect confirmation dialog -->
        <AlertDialog :open="showDisconnectDialog" @update:open="showDisconnectDialog = $event">
          <AlertDialogContent class="sm:max-w-[450px]">
            <AlertDialogHeader>
              <AlertDialogTitle>Disconnect Stripe</AlertDialogTitle>
              <AlertDialogDescription>
                This will remove your Stripe connection. Existing data won't be deleted.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel :disabled="isDisconnecting">Cancel</AlertDialogCancel>
              <Button variant="destructive" @click="handleDisconnect" :disabled="isDisconnecting">
                <Loader2 v-if="isDisconnecting" class="h-4 w-4 mr-2 animate-spin" />
                Disconnect
              </Button>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>

        <StripeConnectionModal
          v-model:visible="showStripeModal"
          :skip-to-discovery="stripeModalSkipToDiscovery"
          @connected="handleStripeConnected"
        />
      </TabsContent>

      <!-- Data Import Tab -->
      <TabsContent value="data-import" class="space-y-6 mt-6">
        <div class="bg-card rounded-lg border shadow-sm p-6 space-y-5">
          <div>
            <h3 class="text-base font-medium mb-1">Import historic data</h3>
            <p class="text-sm text-muted-foreground">
              Upload past cost data to give AI Insights more context. See trends and get
              recommendations without waiting for live data to accumulate.
            </p>
          </div>

          <!-- Uploaded files -->
          <div v-if="csvUploads.length > 0" class="space-y-2">
            <h4 class="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2">
              Uploaded files
            </h4>
            <div
              v-for="upload in csvUploads"
              :key="upload.id"
              class="flex items-center justify-between rounded-lg border bg-card px-3 py-2"
            >
              <div class="flex items-center gap-2 min-w-0">
                <FileText class="h-4 w-4 text-muted-foreground shrink-0" />
                <span class="text-sm font-medium truncate">{{ upload.fileName }}</span>
                <span class="text-xs text-muted-foreground shrink-0"
                  >{{ upload.rowCount }} rows</span
                >
              </div>
              <Button
                variant="ghost"
                size="sm"
                class="h-7 w-7 p-0 shrink-0"
                :disabled="deletingId === upload.id"
                @click="deleteImportUpload(upload.id)"
              >
                <Loader2 v-if="deletingId === upload.id" class="h-3.5 w-3.5 animate-spin" />
                <X v-else class="h-3.5 w-3.5" />
              </Button>
            </div>
          </div>

          <!-- Upload area -->
          <div>
            <h4 class="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2">
              Upload CSV
            </h4>
            <div
              v-if="csvUploads.length < 10"
              class="relative rounded-lg border-2 border-dashed p-6 text-center transition-colors"
              :class="
                isImportDragging
                  ? 'border-primary bg-primary/5'
                  : 'border-muted-foreground/25 hover:border-muted-foreground/50'
              "
              @dragover.prevent="isImportDragging = true"
              @dragleave.prevent="isImportDragging = false"
              @drop.prevent="handleImportDrop"
            >
              <Upload class="h-8 w-8 text-muted-foreground/40 mx-auto mb-2" />
              <p class="text-sm font-medium mb-1">Drop a CSV file here</p>
              <p class="text-xs text-muted-foreground mb-3">or click to browse</p>
              <Button variant="outline" size="sm" @click="importFileInputRef?.click()">
                Choose file
              </Button>
              <input
                ref="importFileInputRef"
                type="file"
                accept=".csv"
                class="hidden"
                @change="handleImportFileSelect"
              />
            </div>
            <div v-else class="text-xs text-muted-foreground">
              Maximum of 10 files reached. Remove one to upload another.
            </div>

            <div
              v-if="importError"
              class="rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-xs text-destructive mt-3"
            >
              {{ importError }}
            </div>

            <div
              v-if="isImportUploading"
              class="flex items-center gap-2 text-sm text-muted-foreground mt-3"
            >
              <Loader2 class="h-4 w-4 animate-spin" />
              Uploading...
            </div>
          </div>

          <!-- What to upload -->
          <div>
            <h4 class="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-2">
              What to upload
            </h4>
            <p class="text-xs text-muted-foreground mb-3">
              Any CSV with cost or usage data. The more context you provide, the better the
              analytics insights.
            </p>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
              <div class="rounded-lg border bg-card p-3">
                <div class="font-medium mb-1">Provider invoices</div>
                <div class="text-muted-foreground font-mono text-[10px]">month, provider, cost</div>
              </div>
              <div class="rounded-lg border bg-card p-3">
                <div class="font-medium mb-1">Per-model costs</div>
                <div class="text-muted-foreground font-mono text-[10px]">
                  month, model, tokens, cost
                </div>
              </div>
              <div class="rounded-lg border bg-card p-3">
                <div class="font-medium mb-1">Per-customer spend</div>
                <div class="text-muted-foreground font-mono text-[10px]">
                  month, customer, cost, revenue
                </div>
              </div>
              <div class="rounded-lg border bg-card p-3">
                <div class="font-medium mb-1">Usage logs</div>
                <div class="text-muted-foreground font-mono text-[10px]">
                  date, feature, model, tokens, cost
                </div>
              </div>
            </div>
          </div>

          <p class="text-[11px] text-muted-foreground">
            This data is used by AI Insights to provide better recommendations. Max 10,000 rows per
            upload.
          </p>
        </div>
      </TabsContent>

      <!-- Developer Tab (sandbox, staging, local) -->
      <TabsContent v-if="isDeveloperEnvironment" value="developer" class="space-y-6 mt-6">
        <div class="bg-card rounded-lg border shadow-sm p-6">
          <h3 class="text-base font-medium mb-2">Submit Test Event</h3>
          <p class="text-sm text-muted-foreground mb-4">
            Inject a raw event to test entitlement behavior and usage state transitions.
          </p>
          <Button @click="showCreateModal = true">
            <Plus class="h-4 w-4 mr-2" />
            Submit test event
          </Button>
        </div>

        <CreateEventModal v-model:visible="showCreateModal" />
      </TabsContent>
    </Tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from '@/components/ui/alert-dialog'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import {
  LogOut,
  Loader2,
  Key as KeyIcon,
  Eye,
  EyeOff,
  CreditCard,
  Plus,
  CheckCircle2,
  HelpCircle,
  Upload,
  FileText,
  X
} from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import CopyButton from '@/components/CopyButton.vue'
import { useAccountApiKeyQuery, useSubscriptionStatusQuery } from '@/features/account/queries'
import { uploadCsv, fetchCsvUploads, deleteCsvUpload } from '@/features/account/api'
import type { CsvUploadInfo } from '@/features/account/api'
import { useAccountSettingsQuery, useStripeKeysQuery } from '@/features/integrations/queries'
import {
  useUpdateAccountSettingsMutation,
  useDeleteStripeKeysMutation
} from '@/features/integrations/mutations'
import { toast } from '@/components/ui/toast/use-toast'
import { parseApiError } from '@/lib/parseApiError'
import ChangePasswordForm from '../components/ChangePasswordForm.vue'
import CreateEventModal from '@/features/events/components/CreateEventModal.vue'
import StripeConnectionModal from '@/features/integrations/components/StripeConnectionModal.vue'
import { useEnvironmentStore } from '@/stores/environment'
import { useTracking } from '@/lib/tracking'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const { track } = useTracking()
const environmentStore = useEnvironmentStore()
const { data: apiKeyData, isLoading: isLoadingApiKey } = useAccountApiKeyQuery()
const { data: subscriptionData, isLoading: isLoadingSubscription } = useSubscriptionStatusQuery()
const { data: settingsData, isLoading: isLoadingSettings } = useAccountSettingsQuery()

const isMasterAccount = computed(
  () =>
    subscriptionData.value?.data?.hasActiveSubscription === true &&
    subscriptionData.value?.data?.planName == null
)

const showApiKey = ref(false)
const maskedApiKey = computed(() => {
  const key = apiKeyData.value?.data?.apiKey
  if (!key) return ''
  const prefix = key.startsWith('sk_live_')
    ? 'sk_live_'
    : key.startsWith('sk_test_')
      ? 'sk_test_'
      : ''
  const suffix = key.slice(-4)
  return `${prefix}${'••••••••'}${suffix}`
})
const showCreateModal = ref(false)

const isDeveloperEnvironment = computed(() => environmentStore.isDeveloperEnvironment)

// Support deep-linking to tabs via query param: /settings?tab=integrations
const activeTab = computed(() => {
  const tab = route.query.tab as string | undefined
  if (tab === 'data-import') return tab
  if (tab === 'integrations' || tab === 'developer') return tab
  return 'general'
})

function handleTabChange(value: string | number) {
  track('settings_tab_changed', { tab: value })
  router.replace({ query: value === 'general' ? {} : { tab: value as string } })
}

const accountSettings = computed(() => settingsData.value?.data)

// Data import state
const importFileInputRef = ref<HTMLInputElement | null>(null)
const isImportDragging = ref(false)
const isImportUploading = ref(false)
const importError = ref<string | null>(null)
const csvUploads = ref<CsvUploadInfo[]>([])
const deletingId = ref<string | null>(null)

onMounted(async () => {
  try {
    const res = await fetchCsvUploads()
    csvUploads.value = res.data
  } catch (e) {
    importError.value = 'Failed to load uploaded files.'
    console.error('Failed to load CSV uploads:', e)
  }
})

function handleImportDrop(e: DragEvent) {
  isImportDragging.value = false
  const file = e.dataTransfer?.files[0]
  if (file) processImportFile(file)
}

function handleImportFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) processImportFile(file)
  input.value = ''
}

async function processImportFile(file: File) {
  importError.value = null
  if (!file.name.toLowerCase().endsWith('.csv')) {
    importError.value = 'Please upload a CSV file.'
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    importError.value = `File too large (${(file.size / 1024 / 1024).toFixed(1)}MB). Maximum size is 10MB.`
    return
  }
  isImportUploading.value = true
  try {
    await uploadCsv(file)
    const res = await fetchCsvUploads()
    csvUploads.value = res.data
  } catch (e) {
    importError.value = e instanceof Error ? e.message : 'Failed to upload file.'
  } finally {
    isImportUploading.value = false
  }
}

async function deleteImportUpload(id: string) {
  deletingId.value = id
  try {
    await deleteCsvUpload(id)
    csvUploads.value = csvUploads.value.filter((u) => u.id !== id)
  } catch (e) {
    importError.value = e instanceof Error ? e.message : 'Failed to delete file.'
  } finally {
    deletingId.value = null
  }
}

// Stripe integration state
const showDisconnectDialog = ref(false)
const showImportConfirmDialog = ref(false)
const showStripeModal = ref(false)
const stripeModalSkipToDiscovery = ref(false)
const isDisconnecting = ref(false)

// Reset skipToDiscovery when modal closes
watch(showStripeModal, (visible) => {
  if (!visible) stripeModalSkipToDiscovery.value = false
})

// Checkout redirect URLs
const checkoutSuccessUrl = ref('')
const checkoutCancelUrl = ref('')
const isSavingUrls = ref(false)
const urlsSubmitted = ref(false)

watch(
  accountSettings,
  (settings) => {
    if (settings) {
      checkoutSuccessUrl.value = settings.stripeCheckoutSuccessUrl ?? ''
      checkoutCancelUrl.value = settings.stripeCheckoutCancelUrl ?? ''
    }
  },
  { immediate: true }
)

const urlsChanged = computed(() => {
  const s = accountSettings.value
  if (!s) return false
  return (
    checkoutSuccessUrl.value !== (s.stripeCheckoutSuccessUrl ?? '') ||
    checkoutCancelUrl.value !== (s.stripeCheckoutCancelUrl ?? '')
  )
})

// Stripe keys query - fetch whenever account settings are loaded
const shouldFetchStripeKeys = computed(() => !!accountSettings.value)
const { data: stripeKeysData } = useStripeKeysQuery(shouldFetchStripeKeys)

const isStripeConnected = computed(() => {
  return !!stripeKeysData.value?.data?.stripeApiKey
})

const maskedStripeKey = computed(() => {
  const key = stripeKeysData.value?.data?.stripeApiKey
  if (!key) return '-'
  const prefix = key.startsWith('sk_live_')
    ? 'sk_live_'
    : key.startsWith('sk_test_')
      ? 'sk_test_'
      : ''
  const suffix = key.slice(-4)
  return `${prefix}${'••••••••'}${suffix}`
})

const displayRole = computed(() => {
  const role = authStore.userRole
  if (!role) return '-'
  const roleMap: Record<string, string> = {
    ROLE_TANSO_UI: 'Admin',
    ROLE_TANSO_CUSTOMER: 'Customer'
  }
  return roleMap[role] ?? role
})

function validateUrl(url: string): string | null {
  if (!url.trim()) return 'Required'
  if (
    url.startsWith('https://') ||
    url === 'http://localhost' ||
    url.startsWith('http://localhost:') ||
    url.startsWith('http://localhost/')
  ) {
    try {
      new URL(url)
      return null
    } catch {
      return 'Invalid URL format'
    }
  }
  return 'Must start with https:// (or http://localhost for development)'
}

const successUrlError = computed(() =>
  urlsSubmitted.value ? validateUrl(checkoutSuccessUrl.value) : null
)
const cancelUrlError = computed(() =>
  urlsSubmitted.value ? validateUrl(checkoutCancelUrl.value) : null
)
const hasUrlValidationErrors = computed(
  () => !!(validateUrl(checkoutSuccessUrl.value) || validateUrl(checkoutCancelUrl.value))
)

// Mutations
const deleteKeysMutation = useDeleteStripeKeysMutation()
const updateSettingsMutation = useUpdateAccountSettingsMutation()

function handleStripeConnected() {
  // Queries are invalidated by the modal on close
}

async function handleDisconnect() {
  track('stripe_disconnected')
  isDisconnecting.value = true
  try {
    await deleteKeysMutation.mutateAsync()
    await updateSettingsMutation.mutateAsync({ stripeMode: 'NONE' })
    showDisconnectDialog.value = false
    toast({
      title: 'Stripe disconnected',
      description: 'Your Stripe integration has been removed.'
    })
  } catch (error) {
    const parsed = parseApiError(error)
    toast({ title: 'Disconnect failed', description: parsed.message, variant: 'destructive' })
  } finally {
    isDisconnecting.value = false
  }
}

async function handleSaveUrls() {
  urlsSubmitted.value = true
  if (hasUrlValidationErrors.value) return
  track('checkout_urls_saved')
  isSavingUrls.value = true
  try {
    await updateSettingsMutation.mutateAsync({
      stripeCheckoutSuccessUrl: checkoutSuccessUrl.value || null,
      stripeCheckoutCancelUrl: checkoutCancelUrl.value || null
    })
    toast({ title: 'URLs saved', description: 'Checkout redirect URLs have been updated.' })
  } catch (error) {
    const parsed = parseApiError(error)
    toast({ title: 'Failed to save URLs', description: parsed.message, variant: 'destructive' })
  } finally {
    isSavingUrls.value = false
  }
}

function handleLogout() {
  track('user_logged_out')
  authStore.logout()
  router.push('/login')
}
</script>
