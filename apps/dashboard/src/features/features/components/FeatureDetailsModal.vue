<template>
  <Dialog :open="visible" @update:open="handleClose">
    <DialogContent class="sm:max-w-[500px]">
      <DialogHeader>
        <DialogTitle>{{ isEditing ? 'Edit Feature' : (feature?.name || 'Feature Details') }}</DialogTitle>
      </DialogHeader>

      <!-- View Mode -->
      <div v-if="feature && !isEditing" class="flex flex-col gap-6">
        <div class="grid grid-cols-2 gap-5">
          <div class="flex flex-col gap-2">
            <Label>Name</Label>
            <p class="text-sm">{{ feature.name }}</p>
          </div>

          <div class="flex flex-col gap-2">
            <Label>Key</Label>
            <div class="flex items-center gap-2">
              <p class="text-sm font-mono bg-muted px-2 py-1 rounded">{{ feature.key }}</p>
              <CopyButton :value="feature.key" label="Key" />
            </div>
          </div>

          <div class="flex flex-col gap-2 col-span-2">
            <Label>Description</Label>
            <p class="text-sm text-muted-foreground">{{ feature.description || 'No description' }}</p>
          </div>

          <div class="flex flex-col gap-2">
            <Label>Status</Label>
            <div>
              <Badge :class="feature.isEnabled ? 'bg-emerald-50 text-emerald-700 border border-emerald-200/50' : 'bg-gray-50 text-gray-600 border border-gray-200/50'" class="shadow-none">
                {{ feature.isEnabled ? 'Enabled' : 'Disabled' }}
              </Badge>
            </div>
          </div>

          <div class="flex flex-col gap-2">
            <Label>Created</Label>
            <p class="text-sm text-muted-foreground">{{ formatDate(feature.createdAt) }}</p>
          </div>

          <div v-if="feature.metadata && Object.keys(feature.metadata).length > 0" class="flex flex-col gap-2 col-span-2">
            <Label>Metadata</Label>
            <pre class="text-xs bg-muted p-3 rounded overflow-auto max-h-32">{{ JSON.stringify(feature.metadata, null, 2) }}</pre>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" @click="handleClose">Close</Button>
          <Button @click="startEditing">
            <Pencil class="w-4 h-4 mr-2" />
            Edit Feature
          </Button>
        </DialogFooter>
      </div>

      <!-- Edit Mode -->
      <form v-else-if="isEditing" @submit.prevent="onSubmit" class="flex flex-col gap-6">
        <div class="grid grid-cols-2 gap-5">
          <div class="flex flex-col gap-2">
            <Label for="name">Name *</Label>
            <Input
              id="name"
              v-model="name"
              :class="{ 'border-destructive': errors.name }"
              placeholder="e.g., API Access"
            />
            <span v-if="errors.name" class="text-destructive text-xs">{{ errors.name }}</span>
          </div>

          <div class="flex flex-col gap-2">
            <Label for="key">Key *</Label>
            <Input
              id="key"
              v-model="key"
              disabled
              class="bg-muted"
            />
            <span class="text-muted-foreground text-xs">Key cannot be changed</span>
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
            <span v-if="errors.description" class="text-destructive text-xs">{{ errors.description }}</span>
          </div>

          <div class="col-span-2">
            <MetadataEditor v-model="metadata" />
          </div>

        </div>

        <Alert v-if="errorMessage" variant="destructive">
          <AlertCircle class="h-4 w-4" />
          <AlertDescription>{{ errorMessage }}</AlertDescription>
        </Alert>

        <DialogFooter>
          <Button type="button" variant="outline" @click="cancelEditing" :disabled="isSubmitting">
            Cancel
          </Button>
          <Button type="submit" :disabled="isSubmitting">
            <Loader2 v-if="isSubmitting" class="mr-2 h-4 w-4 animate-spin" />
            Save Changes
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { toast } from '@/components/ui/toast/use-toast'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { Pencil, Loader2, AlertCircle } from 'lucide-vue-next'
import CopyButton from '@/components/CopyButton.vue'
import MetadataEditor from '@/shared/components/MetadataEditor.vue'
import { updateFeatureSchema } from '../schemas'
import { updateFeature } from '../api'
import type { Feature, UpdateFeature } from '../types'

const props = defineProps<{
  visible: boolean
  feature: Feature | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const queryClient = useQueryClient()
const isEditing = ref(false)
const errorMessage = ref<string | null>(null)
const metadata = ref<Record<string, unknown> | null>(null)

const { mutateAsync, isPending } = useMutation({
  mutationFn: (data: { featureId: string; featureData: UpdateFeature }) =>
    updateFeature(data.featureId, data.featureData),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['features'] })
  }
})

const isSubmitting = computed(() => isPending.value)

const { defineField, handleSubmit, errors, setValues } = useForm({
  validationSchema: toTypedSchema(updateFeatureSchema)
})

const [name] = defineField('name')
const [key] = defineField('key')
const [description] = defineField('description')

watch(
  () => props.feature,
  (newFeature) => {
    if (newFeature) {
      setValues({
        name: newFeature.name,
        key: newFeature.key,
        description: newFeature.description || ''
      })
      metadata.value = newFeature.metadata && Object.keys(newFeature.metadata).length > 0
        ? { ...newFeature.metadata }
        : null
    }
  },
  { immediate: true }
)

watch(
  () => props.visible,
  (visible) => {
    if (!visible) {
      isEditing.value = false
      errorMessage.value = null
    }
  }
)

function startEditing() {
  if (props.feature) {
    setValues({
      name: props.feature.name,
      key: props.feature.key,
      description: props.feature.description || ''
    })
    metadata.value = props.feature.metadata && Object.keys(props.feature.metadata).length > 0
      ? { ...props.feature.metadata }
      : null
  }
  isEditing.value = true
}

function cancelEditing() {
  isEditing.value = false
  errorMessage.value = null
  if (props.feature) {
    setValues({
      name: props.feature.name,
      key: props.feature.key,
      description: props.feature.description || ''
    })
    metadata.value = props.feature.metadata && Object.keys(props.feature.metadata).length > 0
      ? { ...props.feature.metadata }
      : null
  }
}

const onSubmit = handleSubmit(async (values) => {
  if (!props.feature?.id) {
    errorMessage.value = 'No feature selected'
    return
  }

  try {
    errorMessage.value = null
    await mutateAsync({ featureId: props.feature.id, featureData: { ...values, metadata: metadata.value } })
    toast({ title: 'Success', description: 'Feature updated successfully' })
    isEditing.value = false
    emit('update:visible', false)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to update feature'
    toast({ title: 'Error', description: errorMessage.value, variant: 'destructive' })
  }
})

function handleClose() {
  isEditing.value = false
  errorMessage.value = null
  metadata.value = null
  emit('update:visible', false)
}

function formatDate(dateString: string | undefined): string {
  if (!dateString) return '—'
  try {
    const date = new Date(dateString)
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    })
  } catch {
    return dateString
  }
}
</script>
