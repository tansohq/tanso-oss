<template>
  <Sheet :open="visible" @update:open="handleClose">
    <SheetContent class="w-full sm:max-w-lg flex flex-col h-full">
      <SheetHeader class="shrink-0">
        <SheetTitle>Feature Details</SheetTitle>
        <SheetDescription v-if="feature">
          {{ feature.name }}
        </SheetDescription>
      </SheetHeader>

      <!-- View Mode -->
      <div v-if="feature && !isEditing" class="flex-1 overflow-y-auto space-y-6 mt-6 min-h-0 pb-4">
        <!-- Basic Info -->
        <div class="grid grid-cols-2 gap-4">
          <div class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Name</Label>
            <p class="text-sm font-medium">{{ feature.name }}</p>
          </div>

          <div class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Key</Label>
            <div class="flex items-center gap-2">
              <p class="text-sm font-mono bg-muted px-2 py-1 rounded truncate flex-1">{{ feature.key }}</p>
              <CopyButton :value="feature.key" label="Key" />
            </div>
          </div>

          <div class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Status</Label>
            <Badge :class="feature.isEnabled ? 'bg-emerald-50 text-emerald-700 border border-emerald-200/50' : 'bg-gray-50 text-gray-600 border border-gray-200/50'" class="w-fit shadow-none">
              {{ feature.isEnabled ? 'Enabled' : 'Disabled' }}
            </Badge>
          </div>

          <div class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Created At</Label>
            <p class="text-sm tabular-nums">{{ formatDateTime(feature.createdAt) }}</p>
          </div>

          <div class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Modified At</Label>
            <p class="text-sm tabular-nums">{{ formatDateTime(feature.modifiedAt) }}</p>
          </div>
        </div>

        <!-- Feature ID -->
        <div class="space-y-3">
          <div class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Feature ID</Label>
            <div class="flex items-center gap-2">
              <p class="text-sm font-mono bg-muted px-2 py-1 rounded truncate flex-1">{{ feature.id }}</p>
              <CopyButton :value="feature.id" label="Feature ID" />
            </div>
          </div>
        </div>

        <!-- Description -->
        <div class="flex flex-col gap-1">
          <Label class="text-muted-foreground text-xs">Description</Label>
          <p class="text-sm text-muted-foreground">{{ feature.description || 'No description' }}</p>
        </div>

        <!-- Metadata -->
        <div v-if="feature.metadata && Object.keys(feature.metadata).length > 0" class="flex flex-col gap-1">
          <Label class="text-muted-foreground text-xs">Custom Fields</Label>
          <div class="bg-muted rounded-md p-3 space-y-1">
            <div v-for="(value, key) in feature.metadata" :key="key" class="flex justify-between text-sm">
              <span class="text-muted-foreground">{{ key }}</span>
              <span class="font-medium">{{ value }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- View Mode Footer -->
      <SheetFooter v-if="feature && !isEditing" class="mt-6 shrink-0">
        <Button variant="outline" @click="startEditing">
          <Pencil class="w-4 h-4 mr-2" />
          Edit
        </Button>
      </SheetFooter>

      <!-- Edit Mode -->
      <form v-else-if="feature && isEditing" class="flex-1 overflow-y-auto mt-6 min-h-0 pb-4 -mx-1 px-1" @submit.prevent="onSubmit">
        <div class="space-y-6">
        <!-- Editable Fields -->
        <div class="flex flex-col gap-4">
          <div class="flex flex-col gap-2">
            <Label for="name" class="text-muted-foreground text-xs">Name</Label>
            <Input
              id="name"
              v-model="name"
              :class="{ 'border-destructive': errors.name }"
              placeholder="Feature name"
            />
            <span v-if="errors.name" class="text-destructive text-xs">{{ errors.name }}</span>
          </div>

          <div class="flex flex-col gap-2">
            <Label for="description" class="text-muted-foreground text-xs">Description</Label>
            <Textarea
              id="description"
              v-model="description"
              :class="{ 'border-destructive': errors.description }"
              placeholder="Feature description"
              rows="3"
            />
            <span v-if="errors.description" class="text-destructive text-xs">{{ errors.description }}</span>
          </div>

          <!-- Metadata Editor -->
          <MetadataEditor v-model="metadata" />
        </div>

        <!-- Read-only Fields -->
        <div class="grid grid-cols-2 gap-4">
          <div class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Key</Label>
            <div class="flex items-center gap-2">
              <p class="text-sm font-mono bg-muted px-2 py-1 rounded truncate flex-1">{{ feature.key }}</p>
              <CopyButton :value="feature.key" label="Key" />
            </div>
          </div>

          <div class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Status</Label>
            <Badge :class="feature.isEnabled ? 'bg-emerald-50 text-emerald-700 border border-emerald-200/50' : 'bg-gray-50 text-gray-600 border border-gray-200/50'" class="w-fit shadow-none">
              {{ feature.isEnabled ? 'Enabled' : 'Disabled' }}
            </Badge>
          </div>

          <div class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Created At</Label>
            <p class="text-sm tabular-nums">{{ formatDateTime(feature.createdAt) }}</p>
          </div>

          <div class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Modified At</Label>
            <p class="text-sm tabular-nums">{{ formatDateTime(feature.modifiedAt) }}</p>
          </div>
        </div>

        <!-- Feature ID -->
        <div class="space-y-3">
          <div class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Feature ID</Label>
            <div class="flex items-center gap-2">
              <p class="text-sm font-mono bg-muted px-2 py-1 rounded truncate flex-1">{{ feature.id }}</p>
              <CopyButton :value="feature.id" label="Feature ID" />
            </div>
          </div>
        </div>

        <!-- Error Message -->
        <Alert v-if="errorMessage" variant="destructive">
          <AlertCircle class="h-4 w-4" />
          <AlertDescription>{{ errorMessage }}</AlertDescription>
        </Alert>
        </div>
      </form>

      <!-- Edit Mode Footer -->
      <SheetFooter v-if="feature && isEditing" class="mt-6 shrink-0">
        <Button type="button" variant="outline" :disabled="isSubmitting" @click="cancelEditing">
          Cancel
        </Button>
        <Button :disabled="isSubmitting" @click="onSubmit">
          <Loader2 v-if="isSubmitting" class="mr-2 h-4 w-4 animate-spin" />
          Save Changes
        </Button>
      </SheetFooter>
    </SheetContent>
  </Sheet>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { z } from 'zod'
import { toast } from '@/components/ui/toast/use-toast'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle
} from '@/components/ui/sheet'
import { Pencil, Loader2, AlertCircle } from 'lucide-vue-next'
import { formatDateTime } from '@/lib/formatters'
import CopyButton from '@/components/CopyButton.vue'
import MetadataEditor from '@/shared/components/MetadataEditor.vue'
import { updateFeature } from '../api'
import type { Feature } from '../types'

const props = defineProps<{
  visible: boolean
  feature: Feature | null
  initialEditMode?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const queryClient = useQueryClient()
const isEditing = ref(false)
const errorMessage = ref<string | null>(null)
const metadata = ref<Record<string, unknown> | null>(null)

// Schema for editing (only name and description)
const editSchema = z.object({
  name: z.string().min(1, 'Name is required'),
  description: z.string()
})

const { defineField, handleSubmit, errors, setValues } = useForm({
  validationSchema: toTypedSchema(editSchema)
})

const [name] = defineField('name')
const [description] = defineField('description')

const { mutateAsync, isPending } = useMutation({
  mutationFn: (data: { uuid: string; featureData: { name: string; description: string; key: string; isEnabled: boolean; metadata?: Record<string, unknown> | null } }) =>
    updateFeature(data.uuid, data.featureData),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['features'] })
  }
})

const isSubmitting = computed(() => isPending.value)

watch(
  () => props.feature,
  (newFeature) => {
    if (newFeature) {
      setValues({
        name: newFeature.name,
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
    if (visible && props.initialEditMode) {
      isEditing.value = true
    }
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
    await mutateAsync({
      uuid: props.feature.id,
      featureData: {
        name: values.name,
        description: values.description,
        key: props.feature.key,
        isEnabled: props.feature.isEnabled,
        metadata: metadata.value
      }
    })
    toast({ title: 'Success', description: 'Feature updated successfully' })
    isEditing.value = false
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Failed to update feature'
    toast({ title: 'Error', description: errorMessage.value, variant: 'destructive' })
  }
})

function handleClose(open: boolean) {
  if (!open) {
    isEditing.value = false
    errorMessage.value = null
  }
  emit('update:visible', open)
}

</script>
