<template>
  <Dialog :open="visible" @update:open="emit('update:visible', $event)">
    <DialogContent class="sm:max-w-[450px]">
      <DialogHeader>
        <DialogTitle>Edit Billing Information</DialogTitle>
        <DialogDescription> Update billing settings for this subscription. </DialogDescription>
      </DialogHeader>

      <div v-if="subscription" class="flex flex-col gap-4 py-4">
        <!-- Current Info -->
        <div class="p-3 bg-muted rounded-lg">
          <div class="text-sm text-muted-foreground mb-1">Subscription</div>
          <div class="font-medium">
            {{ subscription.customerName }} - {{ subscription.planName }}
          </div>
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

        <!-- Net Terms -->
        <div class="space-y-2">
          <Label>Net Terms</Label>
          <Select v-model="netTerms">
            <SelectTrigger>
              <SelectValue placeholder="Select net terms" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="due_on_issue">Due on Issue</SelectItem>
              <SelectItem value="net_15">Net 15</SelectItem>
              <SelectItem value="net_30">Net 30</SelectItem>
              <SelectItem value="net_60">Net 60</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="handleClose">Cancel</Button>
        <Button @click="handleSave"> Save Changes </Button>
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
  subscriptionId: string | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const { subscriptionsData, updateSubscriptionBilling } = useDemoState()

const billingCycle = ref<BillingCycle>('monthly')
const netTerms = ref<string>('net_30')

const subscription = computed(() => {
  if (!props.subscriptionId) return null
  return subscriptionsData.value.find((s) => s.id === props.subscriptionId) ?? null
})

// Load current values when modal opens
watch(
  () => props.visible,
  (visible) => {
    if (visible && subscription.value) {
      billingCycle.value = subscription.value.billingCycle
      netTerms.value = 'net_30' // Default since not stored on subscription
    }
  }
)

function handleClose() {
  emit('update:visible', false)
}

function handleSave() {
  if (!props.subscriptionId) return

  updateSubscriptionBilling(props.subscriptionId, {
    billingCycle: billingCycle.value,
    netTerms: netTerms.value
  })

  toast({
    title: 'Billing updated',
    description: 'Subscription billing information has been updated.'
  })

  handleClose()
}
</script>
