<template>
  <Dialog :open="visible" @update:open="emit('update:visible', $event)">
    <DialogContent class="sm:max-w-[400px]">
      <DialogHeader>
        <DialogTitle>Edit Base Price</DialogTitle>
        <DialogDescription>Set the plan's recurring base fee and when it's charged.</DialogDescription>
      </DialogHeader>

      <form @submit.prevent="onSubmit" class="flex flex-col gap-6">
        <div class="grid grid-cols-2 gap-4">
          <div class="flex flex-col gap-2">
            <Label for="priceAmount">Amount *</Label>
            <div class="relative">
              <span class="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">$</span>
              <Input
                id="priceAmount"
                v-model.number="priceAmount"
                type="number"
                step="0.01"
                :class="['pl-7', { 'border-destructive': errors.priceAmount }]"
                :min="0"
                placeholder="0.00"
              />
            </div>
            <span v-if="errors.priceAmount" class="text-destructive text-xs">{{ errors.priceAmount }}</span>
          </div>

          <div class="flex flex-col gap-2">
            <Label for="billingTiming">Billing Timing *</Label>
            <Select v-model="billingTiming">
              <SelectTrigger :class="{ 'border-destructive': errors.billingTiming }">
                <SelectValue placeholder="Select when to charge" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="IN_ADVANCE">In advance</SelectItem>
                <SelectItem value="IN_ARREARS">In arrears</SelectItem>
              </SelectContent>
            </Select>
            <span v-if="errors.billingTiming" class="text-destructive text-xs">{{ errors.billingTiming }}</span>
          </div>
        </div>

        <Alert v-if="errorMessage" variant="destructive">
          <AlertCircle class="h-4 w-4" />
          <AlertDescription>{{ errorMessage }}</AlertDescription>
        </Alert>
      </form>

      <DialogFooter>
        <Button variant="outline" @click="close" :disabled="isSubmitting">
          Cancel
        </Button>
        <Button @click="onSubmit" :disabled="isSubmitting">
          <Loader2 v-if="isSubmitting" class="mr-2 h-4 w-4 animate-spin" />
          {{ isSubmitting ? 'Saving...' : 'Save Pricing' }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { toast } from '@/components/ui/toast/use-toast'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { AlertCircle, Loader2 } from 'lucide-vue-next'
import { parseApiError } from '@/lib/parseApiError'
import { useUpdateBasePriceMutation } from '../mutations'
import { basePriceSchema } from '../schemas'
import type { Plan, UpdatePlan } from '../types'

const props = defineProps<{
  visible: boolean
  plan: Plan | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'update:success': []
}>()

const { mutateAsync, isPending: isSubmitting } = useUpdateBasePriceMutation()

const errorMessage = ref<string | null>(null)

const { defineField, handleSubmit, errors, setValues, resetForm } = useForm({
  validationSchema: toTypedSchema(basePriceSchema)
})

const [priceAmount] = defineField('priceAmount')
const [billingTiming] = defineField('billingTiming')

watch(
  [() => props.visible, () => props.plan],
  ([visible, newPlan]) => {
    if (visible && newPlan) {
      setValues({
        priceAmount: newPlan.priceAmount ?? 0,
        billingTiming: newPlan.billingTiming ?? 'IN_ADVANCE'
      })
      errorMessage.value = null
    }
  },
  { immediate: true }
)

const onSubmit = handleSubmit(async (values) => {
  if (!props.plan?.id) {
    errorMessage.value = 'No plan selected'
    return
  }

  try {
    errorMessage.value = null
    const planData = {
      priceAmount: values.priceAmount,
      billingTiming: values.billingTiming
    }
    await mutateAsync({ uuid: props.plan.id, data: planData as UpdatePlan })
    toast({ title: 'Success', description: 'Pricing updated successfully' })
    emit('update:success')
    close()
  } catch (error) {
    const parsedError = parseApiError(error)
    errorMessage.value = parsedError.message
    toast({ title: 'Error', description: parsedError.message, variant: 'destructive' })
  }
})

function close() {
  resetForm()
  errorMessage.value = null
  emit('update:visible', false)
}
</script>
