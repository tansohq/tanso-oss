<template>
  <!-- Locked state -->
  <Card v-if="isLocked || notEntitled" class="p-8 relative overflow-hidden min-h-[280px] flex flex-col">
    <div class="opacity-50 pointer-events-none flex-1">
      <div class="flex items-center gap-4 mb-4">
        <Zap class="w-8 h-8 text-primary" />
        <h3 class="text-2xl font-semibold">{{ feature.name }}</h3>
      </div>
    </div>
    <div class="absolute inset-0 flex items-center justify-center bg-background/80 backdrop-blur-sm">
      <div class="text-center space-y-5 px-6">
        <Lock class="w-16 h-16 mx-auto text-muted-foreground" />
        <div>
          <p class="font-semibold text-lg mb-3">{{ feature.name }}</p>
          <p class="text-base text-muted-foreground mb-6">
            Subscribe to unlock this feature
          </p>
          <Button variant="outline" size="lg" class="min-w-[180px]" @click="emit('upgrade')">
            <Lock class="w-4 h-4 mr-2" />
            Change Plan
          </Button>
        </div>
      </div>
    </div>
  </Card>

  <!-- Unlocked state -->
  <Card v-else class="p-8 min-h-[280px] flex flex-col items-center justify-center text-center transition-shadow hover:shadow-md">
    <div class="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-indigo-50 mb-6">
      <Zap class="w-8 h-8 text-indigo-600" />
    </div>
    <p class="font-semibold text-lg mb-3">{{ feature.name }}</p>

    <!-- Usage display for metered features (usage_based / graduated) -->
    <div v-if="isMetered && entitlement?.usage" class="w-full max-w-[220px] mb-4 space-y-1.5">
      <p class="text-sm tabular-nums text-muted-foreground">
        {{ entitlement.usage.used.toLocaleString() }} / {{ entitlement.usage.limit != null ? entitlement.usage.limit.toLocaleString() : '\u221E' }}{{ unitLabel ? ` ${unitLabel}` : '' }}
      </p>
      <Progress
        v-if="entitlement.usage.limit != null"
        :model-value="Math.min((entitlement.usage.used / entitlement.usage.limit) * 100, 100)"
        class="h-2"
      />
      <p v-if="entitlement && !entitlement.allowed" class="text-xs text-destructive font-medium">
        {{ entitlement.meta?.reason?.description ?? 'Usage limit reached' }}
      </p>
    </div>

    <!-- Fallback local usage display for metered features before entitlement loads -->
    <div v-else-if="isMetered && includedAmount != null" class="w-full max-w-[220px] mb-4 space-y-1.5">
      <p class="text-sm tabular-nums text-muted-foreground">
        {{ currentUsage.toLocaleString() }} / {{ includedAmount.toLocaleString() }}{{ unitLabel ? ` ${unitLabel}` : '' }}
      </p>
      <Progress v-if="usagePercent != null" :model-value="Math.min(usagePercent, 100)" class="h-2" />
    </div>

    <!-- Included feature: simple entitled/not-entitled status -->
    <p v-else-if="!isMetered && entitlement" class="text-sm text-muted-foreground mb-4">
      <span v-if="entitlement.allowed" class="text-green-600">Entitled</span>
      <span v-else class="text-destructive">{{ entitlement.meta?.reason?.description ?? 'Not entitled' }}</span>
    </p>

    <p v-if="accessError" class="text-xs text-destructive mb-2">{{ accessError }}</p>

    <Button size="lg" class="min-w-[180px] bg-indigo-600 hover:bg-indigo-700 text-white" :disabled="isAccessing" @click="handleAccess">
      <Loader2 v-if="isAccessing" class="mr-2 h-4 w-4 animate-spin" />
      Access Feature
    </Button>
  </Card>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { Zap, Lock, Loader2 } from 'lucide-vue-next'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Progress } from '@/components/ui/progress'
import { toast } from '@/components/ui/toast/use-toast'
import { isSimplePrepaidGraduated } from '../lib/examplePricing'
import { useExampleAppState } from '../composables/useExampleAppState'
import { checkEntitlement, trackEvent } from '../api'
import type { EntitlementData } from '../api'
import type { EnrichedFeature } from '../types'

const props = defineProps<{
  feature: EnrichedFeature
  isLocked: boolean
}>()

const emit = defineEmits<{
  upgrade: []
}>()

const { customerId, subscriptionId, setUsage, getUsage } = useExampleAppState()

/** True for features that have metered usage (usage_based or graduated) */
const isMetered = computed(() => props.feature.pricingType !== 'included')

const unitLabel = computed(() => props.feature.unitLabel || '')
const prepaid = computed(() => isSimplePrepaidGraduated(props.feature.tiers ?? null))
const includedAmount = computed(() => {
  if (prepaid.value) return prepaid.value.includedAmount
  return props.feature.maxUsage ?? null
})
const currentUsage = computed(() => getUsage(props.feature.key))
const usagePercent = computed(() => {
  if (includedAmount.value == null) return null
  return (currentUsage.value / Number(includedAmount.value)) * 100
})

const entitlement = ref<EntitlementData | null>(null)
const notEntitled = ref(false)
const isAccessing = ref(false)
const accessError = ref<string | null>(null)

// Fetch entitlement on mount to show current usage (metered) or status (included)
onMounted(async () => {
  if (props.isLocked || !customerId.value) return
  try {
    entitlement.value = await checkEntitlement(customerId.value, props.feature.key, false)
    if (entitlement.value.usage) {
      setUsage(props.feature.key, entitlement.value.usage.used)
    }
    if (!entitlement.value.allowed) {
      notEntitled.value = true
    }
  } catch {
    // Entitlement check failed — fall back to local state
  }
})

async function handleAccess() {
  if (!customerId.value) return

  isAccessing.value = true
  accessError.value = null

  try {
    // 1. Check entitlement
    const ent = await checkEntitlement(customerId.value, props.feature.key)
    entitlement.value = ent

    if (!ent.allowed) {
      const reason = ent.meta?.reason?.description ?? 'Access denied'
      toast({ title: 'Access denied', description: reason, variant: 'destructive' })
      return
    }

    // 2. For included features: entitlement check is all we need
    if (!isMetered.value) {
      toast({
        title: 'Entitlement confirmed',
        description: `You have access to ${props.feature.name}.`,
      })
      return
    }

    // 3. For metered features: track a usage event
    const result = await trackEvent({
      featureKey: props.feature.key,
      customerReferenceId: customerId.value,
      eventName: `${props.feature.name} access`,
      subscriptionId: subscriptionId.value ?? undefined,
    })

    // 4. Re-check entitlement to get updated usage counters
    const updated = await checkEntitlement(customerId.value, props.feature.key)
    entitlement.value = updated
    if (updated.usage) {
      setUsage(props.feature.key, updated.usage.used)
    }

    if (result.usageLimitExceeded) {
      toast({
        title: 'Usage limit exceeded',
        description: `You've exceeded your included usage for ${props.feature.name}. Overage charges may apply.`,
      })
    } else {
      toast({
        title: 'Usage tracked',
        description: `1 ${unitLabel.value || 'unit'} recorded for ${props.feature.name}.`,
      })
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Failed to access feature'
    accessError.value = message
    toast({ title: 'Error', description: message, variant: 'destructive' })
  } finally {
    isAccessing.value = false
  }
}
</script>
