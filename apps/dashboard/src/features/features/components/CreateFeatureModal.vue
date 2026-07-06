<template>
  <Dialog :open="visible" @update:open="emit('update:visible', $event)">
    <DialogContent class="sm:max-w-[600px]">
      <DialogHeader>
        <DialogTitle>Create New Feature</DialogTitle>
        <DialogDescription>Define a feature you can meter or gate in your app.</DialogDescription>
      </DialogHeader>

      <form @submit.prevent="onSubmit" class="flex flex-col gap-6">
        <div class="grid grid-cols-2 gap-5">
          <div class="flex flex-col gap-2">
            <Label for="name">Name *</Label>
            <Input
              id="name"
              v-model="name"
              :class="{ 'border-destructive': errors.name }"
              placeholder="e.g., Analytics Dashboard"
            />
            <span v-if="errors.name" class="text-destructive text-xs">{{ errors.name }}</span>
          </div>

          <div class="flex flex-col gap-2">
            <Label for="key">Key *</Label>
            <Input
              id="key"
              v-model="key"
              :class="{ 'border-destructive': errors.key }"
              placeholder="e.g., feature_analytics"
              @input="keyManuallyEdited = true"
            />
            <span v-if="errors.key" class="text-destructive text-xs">{{ errors.key }}</span>
            <span v-else class="text-muted-foreground text-xs"
              >Locked once the feature is created.</span
            >
          </div>

          <div class="flex flex-col gap-2 col-span-2">
            <Label for="description">Description</Label>
            <Textarea
              id="description"
              v-model="description"
              :class="{ 'border-destructive': errors.description }"
              placeholder="Feature description"
              rows="3"
            />
            <span v-if="errors.description" class="text-destructive text-xs">{{
              errors.description
            }}</span>
          </div>

          <div class="col-span-2">
            <MetadataEditor v-model="metadata" />
          </div>
        </div>

        <Alert v-if="errorMessage" variant="destructive">
          <AlertCircle class="h-4 w-4" />
          <AlertDescription>{{ errorMessage }}</AlertDescription>
        </Alert>
      </form>

      <DialogFooter>
        <Button variant="outline" @click="close" :disabled="isSubmitting"> Cancel </Button>
        <Button @click="onSubmit" :disabled="isSubmitting">
          <Loader2 v-if="isSubmitting" class="mr-2 h-4 w-4 animate-spin" />
          {{ isSubmitting ? 'Saving...' : 'Create Feature' }}
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
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { AlertCircle, Loader2 } from 'lucide-vue-next'
import { parseApiError } from '@/lib/parseApiError'
import { useTracking } from '@/lib/tracking'
import { createFeatureSchema } from '../schemas'
import { useCreateFeatureMutation } from '../mutations'
import MetadataEditor from '@/shared/components/MetadataEditor.vue'

defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const { track } = useTracking()
const { mutateAsync, isPending } = useCreateFeatureMutation()
const isSubmitting = computed(() => isPending.value)
const errorMessage = ref<string | null>(null)
const keyManuallyEdited = ref(false)
const metadata = ref<Record<string, unknown> | null>(null)

const { defineField, handleSubmit, errors, resetForm, setFieldError } = useForm({
  validationSchema: toTypedSchema(createFeatureSchema),
  initialValues: {
    isEnabled: true
  }
})

const [key] = defineField('key')
const [name] = defineField('name')
const [description] = defineField('description')

const onSubmit = handleSubmit(async (values) => {
  try {
    errorMessage.value = null
    await mutateAsync({ ...values, metadata: metadata.value })
    track('feature_created')
    toast({ title: 'Success', description: 'Feature created successfully' })
    resetForm()
    close()
  } catch (error) {
    const parsedError = parseApiError(error)
    errorMessage.value = parsedError.message
    toast({ title: 'Error', description: parsedError.message, variant: 'destructive' })

    // Highlight the key field for duplicate errors
    if (parsedError.type === 'duplicate') {
      setFieldError('key', parsedError.message)
    }
  }
})

function close() {
  resetForm()
  errorMessage.value = null
  keyManuallyEdited.value = false
  metadata.value = null
  emit('update:visible', false)
}
</script>
