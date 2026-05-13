<template>
  <AlertDialog :open="visible" @update:open="emit('update:visible', $event)">
    <AlertDialogContent class="sm:max-w-[500px]">
      <AlertDialogHeader>
        <AlertDialogTitle>Cancel Subscription</AlertDialogTitle>
        <AlertDialogDescription>
          This action will cancel the subscription for <span class="font-medium">{{ customerName }}</span> on the <span class="font-medium">{{ planName }}</span> plan.
        </AlertDialogDescription>
      </AlertDialogHeader>

      <div class="py-4 space-y-4">
        <div class="space-y-3">
          <Label>When should the cancellation take effect?</Label>
          <RadioGroup v-model="cancelMode" class="space-y-2">
            <div class="flex items-start gap-3">
              <RadioGroupItem value="END_OF_PERIOD" id="end_of_period" class="mt-1 shrink-0" />
              <div>
                <Label for="end_of_period" class="font-normal cursor-pointer">End of billing period</Label>
                <p class="text-xs text-muted-foreground">Access continues until {{ formatDate(currentPeriodEnd) }}</p>
              </div>
            </div>
            <div class="flex items-start gap-3">
              <RadioGroupItem value="IMMEDIATE" id="immediate" class="mt-1 shrink-0" />
              <div>
                <Label for="immediate" class="font-normal cursor-pointer">Immediately</Label>
                <p class="text-xs text-muted-foreground">Access revoked and entitlements removed now.</p>
              </div>
            </div>
          </RadioGroup>
        </div>
      </div>

      <AlertDialogFooter>
        <AlertDialogCancel :disabled="isPending">Keep Subscription</AlertDialogCancel>
        <Button variant="destructive" @click="handleConfirm" :disabled="isPending">
          <Loader2 v-if="isPending" class="h-4 w-4 mr-2 animate-spin" />
          Cancel Subscription
        </Button>
      </AlertDialogFooter>
    </AlertDialogContent>
  </AlertDialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Loader2 } from 'lucide-vue-next'
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { toast } from '@/components/ui/toast/use-toast'
import { parseApiError } from '@/lib/parseApiError'
import { useTracking } from '@/lib/tracking'
import { formatDate } from '@/lib/formatters'
import { useCancelSubscriptionMutation } from '../mutations'

const props = defineProps<{
  subscriptionId: string
  planName: string
  customerName: string
  currentPeriodEnd: string
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const { track } = useTracking()
const cancelMode = ref<'IMMEDIATE' | 'END_OF_PERIOD'>('END_OF_PERIOD')
const { mutateAsync, isPending } = useCancelSubscriptionMutation()

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      cancelMode.value = 'END_OF_PERIOD'
    }
  }
)

function handleClose() {
  emit('update:visible', false)
}

async function handleConfirm() {
  try {
    await mutateAsync({ id: props.subscriptionId, cancelMode: cancelMode.value })
    track('subscription_cancelled')
    toast({
      title: 'Subscription cancelled',
      description:
        cancelMode.value === 'IMMEDIATE'
          ? 'The subscription has been cancelled immediately.'
          : `The subscription will be cancelled at the end of the billing period.`
    })
    handleClose()
  } catch (error) {
    const parsedError = parseApiError(error)
    toast({ title: 'Error', description: parsedError.message, variant: 'destructive' })
  }
}
</script>
