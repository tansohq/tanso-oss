<template>
  <Dialog :open="visible" @update:open="emit('update:visible', $event)">
    <DialogContent class="sm:max-w-[450px]">
      <DialogHeader>
        <DialogTitle>Resume Subscription</DialogTitle>
        <DialogDescription>
          Are you sure you want to resume this subscription? Billing will restart from the next
          billing cycle.
        </DialogDescription>
      </DialogHeader>

      <div v-if="subscription" class="py-4">
        <div class="flex items-center justify-between p-3 bg-muted rounded-lg">
          <div>
            <div class="font-medium">{{ subscription.customerName }}</div>
            <div class="text-sm text-muted-foreground">
              {{ subscription.planName }} - v{{ subscription.version }}
            </div>
          </div>
          <Badge class="bg-yellow-100 text-yellow-700 border-0">Paused</Badge>
        </div>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="handleClose">Cancel</Button>
        <Button @click="handleConfirm">
          <Play class="h-4 w-4 mr-2" />
          Resume Subscription
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Play } from 'lucide-vue-next'
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
import { useDemoState } from '../../composables/useDemoState'
import { toast } from '@/components/ui/toast/use-toast'

const props = defineProps<{
  visible: boolean
  subscriptionId: string | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const { subscriptionsData, resumeSubscription } = useDemoState()

const subscription = computed(() => {
  if (!props.subscriptionId) return null
  return subscriptionsData.value.find((s) => s.id === props.subscriptionId) ?? null
})

function handleClose() {
  emit('update:visible', false)
}

function handleConfirm() {
  if (!props.subscriptionId) return

  resumeSubscription(props.subscriptionId)

  toast({
    title: 'Subscription resumed',
    description: 'Billing will continue from the next billing cycle.'
  })

  handleClose()
}
</script>
