<template>
  <Dialog :open="visible" @update:open="emit('update:visible', $event)">
    <DialogContent class="sm:max-w-[500px]">
      <DialogHeader>
        <DialogTitle>Create Credit Model</DialogTitle>
      </DialogHeader>

      <form @submit.prevent="onSubmit" class="flex flex-col gap-5">
        <div class="flex flex-col gap-2">
          <Label for="name">Name *</Label>
          <Input
            id="name"
            v-model="name"
            :class="{ 'border-destructive': errors.name }"
            placeholder="e.g., AI Credits"
          />
          <span v-if="errors.name" class="text-destructive text-xs">{{ errors.name }}</span>
        </div>

        <div class="flex flex-col gap-2">
          <Label for="denomination">Denomination *</Label>
          <Input
            id="denomination"
            v-model="denomination"
            :class="{ 'border-destructive': errors.denomination }"
            placeholder="e.g., credits, tokens, units"
          />
          <span v-if="errors.denomination" class="text-destructive text-xs">{{ errors.denomination }}</span>
          <span v-else class="text-muted-foreground text-xs">The unit of measurement for this credit type</span>
        </div>

        <div class="flex flex-col gap-2">
          <Label for="description">Description</Label>
          <Textarea
            id="description"
            v-model="description"
            placeholder="Optional description"
            rows="2"
          />
        </div>

        <Alert v-if="errorMessage" variant="destructive">
          <AlertCircle class="h-4 w-4" />
          <AlertDescription>{{ errorMessage }}</AlertDescription>
        </Alert>
      </form>

      <DialogFooter>
        <Button variant="outline" @click="close" :disabled="isSubmitting">Cancel</Button>
        <Button @click="onSubmit" :disabled="isSubmitting">
          <Loader2 v-if="isSubmitting" class="mr-2 h-4 w-4 animate-spin" />
          {{ isSubmitting ? 'Creating...' : 'Create' }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { toast } from '@/components/ui/toast/use-toast'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { AlertCircle, Loader2 } from 'lucide-vue-next'
import { parseApiError } from '@/lib/parseApiError'
import { useTracking } from '@/lib/tracking'
import { createCreditModelSchema } from '../schemas'
import { useCreateCreditModelMutation } from '../mutations'

defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const { track } = useTracking()
const { mutateAsync, isPending } = useCreateCreditModelMutation()
const isSubmitting = computed(() => isPending.value)
const errorMessage = ref<string | null>(null)

const { defineField, handleSubmit, errors, resetForm } = useForm({
  validationSchema: toTypedSchema(createCreditModelSchema)
})

const [name] = defineField('name')
const [denomination] = defineField('denomination')
const [description] = defineField('description')

const onSubmit = handleSubmit(async (values) => {
  try {
    errorMessage.value = null
    await mutateAsync(values)
    track('credit_model_created')
    toast({ title: 'Success', description: 'Credit model created successfully' })
    resetForm()
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
