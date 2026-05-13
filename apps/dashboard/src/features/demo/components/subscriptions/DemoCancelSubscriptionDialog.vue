<template>
  <Dialog :open="visible" @update:open="emit('update:visible', $event)">
    <DialogContent class="sm:max-w-[500px]">
      <DialogHeader>
        <DialogTitle>Cancel Subscription</DialogTitle>
        <DialogDescription>
          This action will cancel the subscription. Choose when the cancellation should take effect.
        </DialogDescription>
      </DialogHeader>

      <div v-if="subscription" class="py-4 space-y-4">
        <div class="flex items-center justify-between p-3 bg-muted rounded-lg">
          <div>
            <div class="font-medium">{{ subscription.customerName }}</div>
            <div class="text-sm text-muted-foreground">
              {{ subscription.planName }} - v{{ subscription.version }}
            </div>
          </div>
          <Badge :class="getStatusColor(subscription.status)" class="border-0 capitalize">
            {{ subscription.status }}
          </Badge>
        </div>

        <div class="space-y-3">
          <Label>Cancellation Timing</Label>
          <RadioGroup v-model="cancelTiming" class="space-y-2">
            <div class="flex items-center space-x-2">
              <RadioGroupItem value="immediate" id="immediate" />
              <Label for="immediate" class="font-normal cursor-pointer"> Cancel immediately </Label>
            </div>
            <div class="flex items-center space-x-2">
              <RadioGroupItem value="end_of_period" id="end_of_period" />
              <Label for="end_of_period" class="font-normal cursor-pointer">
                Cancel at end of billing period ({{ subscription.currentPeriodEnd }})
              </Label>
            </div>
          </RadioGroup>
        </div>

        <div class="flex items-start gap-2 p-3 bg-red-50 text-red-800 rounded-lg text-sm">
          <AlertTriangle class="h-4 w-4 mt-0.5 flex-shrink-0" />
          <span
            >This action cannot be undone. The customer will lose access to the subscription
            features.</span
          >
        </div>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="handleClose">Keep Subscription</Button>
        <Button variant="destructive" @click="handleConfirm">
          <XCircle class="h-4 w-4 mr-2" />
          Cancel Subscription
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { XCircle, AlertTriangle } from 'lucide-vue-next'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { useDemoState } from '../../composables/useDemoState'
import { toast } from '@/components/ui/toast/use-toast'

const props = defineProps<{
  visible: boolean
  subscriptionId: string | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const { subscriptionsData, cancelSubscription } = useDemoState()

const cancelTiming = ref<'immediate' | 'end_of_period'>('end_of_period')

const subscription = computed(() => {
  if (!props.subscriptionId) return null
  return subscriptionsData.value.find((s) => s.id === props.subscriptionId) ?? null
})

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      cancelTiming.value = 'end_of_period'
    }
  }
)

function getStatusColor(status: string): string {
  switch (status) {
    case 'active':
      return 'bg-green-100 text-green-700'
    case 'paused':
      return 'bg-yellow-100 text-yellow-700'
    case 'canceled':
      return 'bg-red-100 text-red-700'
    default:
      return 'bg-gray-100 text-gray-700'
  }
}

function handleClose() {
  emit('update:visible', false)
}

function handleConfirm() {
  if (!props.subscriptionId || !subscription.value) return

  const endDate =
    cancelTiming.value === 'immediate'
      ? new Date().toISOString().split('T')[0]
      : subscription.value.currentPeriodEnd

  cancelSubscription(props.subscriptionId, endDate)

  toast({
    title: 'Subscription canceled',
    description:
      cancelTiming.value === 'immediate'
        ? 'The subscription has been canceled immediately.'
        : `The subscription will be canceled on ${endDate}.`,
    variant: 'destructive'
  })

  handleClose()
}
</script>
