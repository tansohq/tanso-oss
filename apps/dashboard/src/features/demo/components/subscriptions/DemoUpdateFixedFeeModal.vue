<template>
  <Dialog :open="visible" @update:open="emit('update:visible', $event)">
    <DialogContent class="sm:max-w-[500px]">
      <DialogHeader>
        <DialogTitle>{{ editingFee ? 'Edit' : 'Update' }} Fixed Fee Quantity</DialogTitle>
        <DialogDescription>
          {{ editingFee ? 'Modify' : 'Set' }} a custom quantity for this fixed fee component.
        </DialogDescription>
      </DialogHeader>

      <div class="flex flex-col gap-4 py-4">
        <!-- Fee Selection (only for new) -->
        <div v-if="!editingFee" class="space-y-2">
          <Label>Fixed Fee Component</Label>
          <Select v-model="selectedFeeId">
            <SelectTrigger>
              <SelectValue placeholder="Select a component" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem v-for="fee in availableFees" :key="fee.id" :value="fee.id">
                {{ fee.component }}
              </SelectItem>
            </SelectContent>
          </Select>
        </div>

        <!-- Fee Info (for editing) -->
        <div v-if="editingFee" class="p-3 bg-muted rounded-lg">
          <div class="text-sm text-muted-foreground mb-1">Component</div>
          <div class="font-medium">{{ editingFee.component }}</div>
        </div>

        <!-- Original Quantity -->
        <div class="space-y-2">
          <Label>Plan Default Quantity</Label>
          <div class="flex items-center gap-2">
            <span class="text-lg font-medium text-muted-foreground">{{ originalQuantity }}</span>
            <span class="text-sm text-muted-foreground">units</span>
          </div>
        </div>

        <!-- New Quantity -->
        <div class="space-y-2">
          <Label>Custom Quantity</Label>
          <Input type="number" min="0" v-model.number="newQuantity" placeholder="0" />
          <div
            v-if="quantityDiff !== 0"
            class="text-sm"
            :class="quantityDiff > 0 ? 'text-blue-600' : 'text-amber-600'"
          >
            {{ quantityDiff > 0 ? '+' : '' }}{{ quantityDiff }} from plan default
          </div>
        </div>

        <!-- Price Preview -->
        <div v-if="selectedFee" class="p-3 bg-blue-50 rounded-lg">
          <div class="text-sm text-muted-foreground mb-1">Monthly Cost</div>
          <div class="flex items-center gap-2">
            <span class="text-lg font-medium"
              >${{ (selectedFee.amount * newQuantity).toFixed(2) }}</span
            >
            <span class="text-sm text-muted-foreground"
              >(${{ selectedFee.amount }} x {{ newQuantity }})</span
            >
          </div>
        </div>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="handleClose">Cancel</Button>
        <Button @click="handleSave" :disabled="!canSave">
          {{ editingFee ? 'Save Changes' : 'Update Quantity' }}
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
import type { FixedFeeQuantity } from '../../types'

const props = defineProps<{
  visible: boolean
  subscriptionId: string | null
  editingFee?: FixedFeeQuantity | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const { subscriptionsData, pricingPlansData, updateFixedFeeQuantity } = useDemoState()

const selectedFeeId = ref<string>('')
const newQuantity = ref<number>(0)

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

// Get fixed fees from the plan version
const availableFees = computed(() => {
  if (!subscriptionVersion.value) return []

  const existingFeeIds = subscription.value?.fixedFeeQuantities?.map((fq) => fq.feeId) || []

  return subscriptionVersion.value.fixedFees
    .filter((fee) => !existingFeeIds.includes(fee.id))
    .map((fee) => ({
      id: fee.id,
      component: fee.component,
      amount: fee.amount,
      quantity: fee.quantity
    }))
})

const selectedFee = computed(() => {
  if (props.editingFee) {
    const planFee = subscriptionVersion.value?.fixedFees.find(
      (f) => f.id === props.editingFee!.feeId
    )
    return planFee
      ? {
          id: planFee.id,
          component: planFee.component,
          amount: planFee.amount,
          quantity: planFee.quantity
        }
      : null
  }
  if (!selectedFeeId.value) return null
  return availableFees.value.find((f) => f.id === selectedFeeId.value) ?? null
})

const originalQuantity = computed(() => {
  if (props.editingFee) return props.editingFee.originalQuantity
  return selectedFee.value?.quantity || 0
})

const quantityDiff = computed(() => {
  return newQuantity.value - originalQuantity.value
})

const canSave = computed(() => {
  if (props.editingFee) {
    return newQuantity.value >= 0
  }
  return selectedFeeId.value && newQuantity.value >= 0
})

// Reset form when modal opens
watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      if (props.editingFee) {
        newQuantity.value = props.editingFee.quantity
      } else {
        selectedFeeId.value = ''
        newQuantity.value = 0
      }
    }
  }
)

// Set quantity to original when fee selected
watch(selectedFeeId, (newId) => {
  if (newId && !props.editingFee) {
    const fee = availableFees.value.find((f) => f.id === newId)
    if (fee) {
      newQuantity.value = fee.quantity
    }
  }
})

function handleClose() {
  emit('update:visible', false)
}

function handleSave() {
  if (!props.subscriptionId || !canSave.value) return

  const fee = selectedFee.value
  if (!fee) return

  const feeQuantity: FixedFeeQuantity = {
    feeId: props.editingFee?.feeId || selectedFeeId.value,
    component: fee.component,
    originalQuantity: originalQuantity.value,
    quantity: newQuantity.value
  }

  updateFixedFeeQuantity(props.subscriptionId, feeQuantity)

  toast({
    title: props.editingFee ? 'Quantity updated' : 'Fixed fee quantity set',
    description: `${fee.component} quantity set to ${newQuantity.value} units.`
  })

  handleClose()
}
</script>
