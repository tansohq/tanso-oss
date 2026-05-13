<template>
  <div class="space-y-6">
    <!-- Step indicator (only show if Stripe connected and billing step visible) -->
    <div v-if="totalSteps > 1" class="flex items-center gap-2 text-sm text-muted-foreground">
      <span v-for="s in totalSteps" :key="s" class="flex items-center gap-1">
        <span
          class="flex h-6 w-6 items-center justify-center rounded-full text-xs font-medium"
          :class="s === step
            ? 'bg-primary text-primary-foreground'
            : s < step
              ? 'bg-primary/20 text-primary'
              : 'bg-muted text-muted-foreground'"
        >
          <Check v-if="s < step" class="w-3 h-3" />
          <span v-else>{{ s }}</span>
        </span>
        <span v-if="s < totalSteps" class="w-6 h-px bg-border" />
      </span>
    </div>

    <!-- Step 1: Connect Stripe -->
    <div v-if="step === 1">
      <h2 class="text-xl font-semibold mb-1">Connect Stripe</h2>
      <p class="text-muted-foreground text-sm mb-6">
        Link your Stripe account to enable billing, payment collection, and data sync.
      </p>

      <div class="space-y-4 max-w-md">
        <div class="space-y-2">
          <Label for="stripe-key">Stripe Secret Key</Label>
          <Input
            id="stripe-key"
            v-model="stripeApiKeyInput"
            type="password"
            placeholder="sk_test_... or sk_live_..."
            class="font-mono text-sm"
            :disabled="isConnecting"
          />
          <p class="text-xs text-muted-foreground">
            Find this in your
            <a href="https://dashboard.stripe.com/apikeys" target="_blank" rel="noopener noreferrer" class="text-primary hover:underline">Stripe Dashboard</a>
            under Developers &rarr; API keys.
          </p>
        </div>

        <Alert v-if="connectionError" variant="destructive">
          <AlertCircle class="h-4 w-4" />
          <AlertDescription>{{ connectionError }}</AlertDescription>
        </Alert>

        <div class="flex justify-between pt-2">
          <Button variant="ghost" @click="emit('cancel')">Cancel</Button>
          <Button @click="handleConnectStripe" :disabled="!stripeApiKeyInput.trim() || isConnecting">
            <Loader2 v-if="isConnecting" class="w-4 h-4 mr-2 animate-spin" />
            Connect Stripe
          </Button>
        </div>
      </div>
    </div>

    <!-- Step 2: Billing setup (only if Stripe was connected) -->
    <div v-if="step === 2">
      <h2 class="text-xl font-semibold mb-1">Who handles billing?</h2>
      <p class="text-muted-foreground text-sm mb-6">
        You can change this anytime in Settings. Everything else — entitlements, usage tracking, analytics — works the same either way.
      </p>

      <div class="grid gap-3">
        <button
          class="flex items-start gap-4 p-4 rounded-lg border text-left transition-colors hover:bg-muted/50"
          :class="selectedMode === 'STRIPE_INTEGRATION' ? 'border-primary bg-primary/5' : ''"
          @click="selectedMode = 'STRIPE_INTEGRATION'"
        >
          <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-blue-100 text-blue-600">
            <Layers class="w-5 h-5" />
          </div>
          <div class="flex-1">
            <span class="font-medium">Stripe drives billing</span>
            <p class="text-sm text-muted-foreground mt-0.5">
              Stripe drives billing. Tanso becomes your management plane for entitlements, usage tracking, and revenue analytics.
            </p>
          </div>
        </button>

        <button
          class="flex items-start gap-4 p-4 rounded-lg border text-left transition-colors hover:bg-muted/50"
          :class="selectedMode === 'PAYMENT_PASS_THROUGH' ? 'border-primary bg-primary/5' : ''"
          @click="selectedMode = 'PAYMENT_PASS_THROUGH'"
        >
          <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-emerald-100 text-emerald-600">
            <Zap class="w-5 h-5" />
          </div>
          <div class="flex-1">
            <span class="font-medium">Tanso handles billing</span>
            <p class="text-sm text-muted-foreground mt-0.5">
              Tanso manages subscriptions, pricing, and invoices. Stripe collects payments via Checkout.
            </p>
          </div>
        </button>
      </div>

      <div class="flex justify-between mt-6">
        <div class="flex gap-2">
          <Button variant="ghost" @click="emit('cancel')">Cancel</Button>
          <Button v-if="!props.stripeAlreadyConnected" variant="ghost" @click="step = 1">
            <ArrowLeft class="w-4 h-4 mr-2" />
            Back
          </Button>
        </div>
        <Button @click="handleFinishWithMode" :disabled="!selectedMode">
          Get Started
          <ArrowRight class="w-4 h-4 ml-2" />
        </Button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Check, ArrowRight, ArrowLeft,
  Layers, Zap, Loader2, AlertCircle
} from 'lucide-vue-next'
import { useUpdateAccountSettingsMutation, useRegisterStripeApiKeyMutation, useRegisterStripeWebhookMutation } from '@/features/integrations/mutations'
import { parseApiError } from '@/lib/parseApiError'
import { useToast } from '@/components/ui/toast/use-toast'
import type { StripeMode } from '@/features/integrations/schemas'

const props = defineProps<{
  stripeAlreadyConnected?: boolean
}>()

const { toast } = useToast()
const emit = defineEmits<{
  complete: [mode: StripeMode, stripeConnected: boolean]
  cancel: []
}>()

const stripeConnected = ref(false)
const totalSteps = computed(() => stripeConnected.value ? 2 : 1)
const step = ref(1)
const selectedMode = ref<StripeMode | null>(null)
const stripeApiKeyInput = ref('')
const isConnecting = ref(false)
const connectionError = ref<string | null>(null)

const updateSettingsMutation = useUpdateAccountSettingsMutation()
const registerKeyMutation = useRegisterStripeApiKeyMutation()
const registerWebhookMutation = useRegisterStripeWebhookMutation()

onMounted(() => {
  if (props.stripeAlreadyConnected) {
    stripeConnected.value = true
    step.value = 2
  }
})

async function handleConnectStripe() {
  if (!stripeApiKeyInput.value.trim()) return
  isConnecting.value = true
  connectionError.value = null

  try {
    await registerKeyMutation.mutateAsync(stripeApiKeyInput.value.trim())
    await registerWebhookMutation.mutateAsync()
    stripeConnected.value = true
    step.value = 2
  } catch (error) {
    const parsed = parseApiError(error)
    connectionError.value = parsed.message
  } finally {
    isConnecting.value = false
  }
}

async function handleFinishWithMode() {
  if (!selectedMode.value) return
  try {
    await updateSettingsMutation.mutateAsync({ stripeMode: selectedMode.value })
    emit('complete', selectedMode.value, stripeConnected.value)
  } catch {
    toast({ title: 'Failed to save billing mode', description: 'Please try again or configure in Settings.', variant: 'destructive' })
  }
}

</script>
