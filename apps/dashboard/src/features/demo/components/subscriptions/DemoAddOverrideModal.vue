<template>
  <Dialog :open="visible" @update:open="emit('update:visible', $event)">
    <DialogContent class="sm:max-w-[500px]">
      <DialogHeader>
        <DialogTitle>{{ editingOverride ? 'Edit' : 'Add' }} Pricing Override</DialogTitle>
        <DialogDescription>
          {{ editingOverride ? 'Update' : 'Create' }} a custom price for this subscription.
        </DialogDescription>
      </DialogHeader>

      <div class="flex flex-col gap-4 py-4">
        <!-- Feature Selection (only for new overrides) -->
        <div v-if="!editingOverride" class="space-y-2">
          <Label>Feature</Label>
          <Select v-model="selectedFeatureId">
            <SelectTrigger>
              <SelectValue placeholder="Select a feature" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem
                v-for="feature in availableFeatures"
                :key="feature.id"
                :value="feature.id"
              >
                {{ feature.featureName }}
              </SelectItem>
            </SelectContent>
          </Select>
        </div>

        <!-- Feature Info (for editing) -->
        <div v-if="editingOverride" class="p-3 bg-muted rounded-lg">
          <div class="text-sm text-muted-foreground mb-1">Feature</div>
          <div class="font-medium">{{ editingOverride.featureName }}</div>
        </div>

        <!-- Original Price -->
        <div class="space-y-2">
          <Label>Original Price</Label>
          <div class="flex items-center gap-2">
            <span class="text-lg font-medium text-muted-foreground"
              >${{ originalPrice.toFixed(4) }}</span
            >
            <span class="text-sm text-muted-foreground">per unit</span>
          </div>
        </div>

        <!-- Override Price -->
        <div class="space-y-2">
          <Label>Override Price</Label>
          <div class="relative">
            <span class="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">$</span>
            <Input
              type="number"
              step="0.0001"
              min="0"
              v-model.number="overridePrice"
              class="pl-7"
              placeholder="0.00"
            />
          </div>
          <div
            v-if="discountPercent !== 0"
            class="text-sm"
            :class="discountPercent > 0 ? 'text-green-600' : 'text-red-600'"
          >
            {{ discountPercent > 0 ? '-' : '+' }}{{ Math.abs(discountPercent).toFixed(1) }}% from
            original
          </div>
        </div>

        <!-- Reason -->
        <div class="space-y-2">
          <Label>Reason (optional)</Label>
          <Input v-model="reason" placeholder="e.g., Volume discount, Strategic account" />
        </div>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="handleClose">Cancel</Button>
        <Button @click="handleSave" :disabled="!canSave">
          {{ editingOverride ? 'Save Changes' : 'Add Override' }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
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
import type { SubscriptionOverride } from '../../types'

const props = defineProps<{
  visible: boolean
  subscriptionId: string | null
  editingOverride?: SubscriptionOverride | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const { subscriptionsData, pricingPlansData, addSubscriptionOverride, updateSubscriptionOverride } =
  useDemoState()

const selectedFeatureId = ref<string>('')
const overridePrice = ref<number>(0)
const reason = ref<string>('')

const subscription = computed(() => {
  if (!props.subscriptionId) return null
  return subscriptionsData.value.find((s) => s.id === props.subscriptionId) ?? null
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

// Get features from the plan's usage-based fees
const availableFeatures = computed(() => {
  if (!subscriptionVersion.value) return []

  const existingOverrideIds = subscription.value?.overrides?.map((o) => o.featureId) || []

  return subscriptionVersion.value.usageBasedFees
    .filter((fee) => !existingOverrideIds.includes(fee.featureId))
    .map((fee) => ({
      id: fee.featureId,
      featureName: fee.featureName,
      unitPrice: fee.unitPrice || fee.defaultUnitPrice || 0
    }))
})

const selectedFeature = computed(() => {
  if (props.editingOverride) {
    return {
      id: props.editingOverride.featureId,
      featureName: props.editingOverride.featureName,
      unitPrice: props.editingOverride.originalPrice
    }
  }
  if (!selectedFeatureId.value) return null
  return availableFeatures.value.find((f) => f.id === selectedFeatureId.value) ?? null
})

const originalPrice = computed(() => {
  if (props.editingOverride) return props.editingOverride.originalPrice
  return selectedFeature.value?.unitPrice || 0
})

const discountPercent = computed(() => {
  if (originalPrice.value === 0) return 0
  return ((originalPrice.value - overridePrice.value) / originalPrice.value) * 100
})

const canSave = computed(() => {
  if (props.editingOverride) {
    return overridePrice.value >= 0
  }
  return selectedFeatureId.value && overridePrice.value >= 0
})

// Reset form when modal opens
watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      if (props.editingOverride) {
        overridePrice.value = props.editingOverride.overridePrice
        reason.value = props.editingOverride.reason || ''
      } else {
        selectedFeatureId.value = ''
        overridePrice.value = 0
        reason.value = ''
      }
    }
  }
)

// Set override price to original when feature selected
watch(selectedFeatureId, (newId) => {
  if (newId && !props.editingOverride) {
    const feature = availableFeatures.value.find((f) => f.id === newId)
    if (feature) {
      overridePrice.value = feature.unitPrice
    }
  }
})

function handleClose() {
  emit('update:visible', false)
}

function handleSave() {
  if (!props.subscriptionId || !canSave.value) return

  if (props.editingOverride) {
    updateSubscriptionOverride(props.subscriptionId, props.editingOverride.featureId, {
      overridePrice: overridePrice.value,
      reason: reason.value || undefined
    })

    toast({
      title: 'Override updated',
      description: `Pricing override for ${props.editingOverride.featureName} has been updated.`
    })
  } else {
    const feature = selectedFeature.value
    if (!feature) return

    addSubscriptionOverride(props.subscriptionId, {
      featureId: feature.id,
      featureName: feature.featureName,
      originalPrice: feature.unitPrice,
      overridePrice: overridePrice.value,
      reason: reason.value || undefined
    })

    toast({
      title: 'Override added',
      description: `Custom pricing for ${feature.featureName} has been added.`
    })
  }

  handleClose()
}
</script>
