<template>
  <div class="flex flex-col gap-3">
    <div class="flex items-center justify-between">
      <Label>{{ label }}</Label>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        class="h-7 text-xs"
        @click="addField"
      >
        <Plus class="w-3 h-3 mr-1" />
        Add field
      </Button>
    </div>

    <div v-if="fields.length === 0" class="text-sm text-muted-foreground py-2">
      No custom fields. Click "Add field" to add key-value pairs.
    </div>

    <div v-else class="flex flex-col gap-2">
      <div
        v-for="(field, index) in fields"
        :key="field.id"
        class="flex items-start gap-2"
      >
        <div class="flex-1">
          <Input
            :model-value="field.key"
            placeholder="Key"
            :class="{ 'border-destructive': field.keyError }"
            class="h-9 text-sm"
            @update:model-value="updateFieldKey(index, String($event))"
          />
          <span v-if="field.keyError" class="text-destructive text-xs">{{ field.keyError }}</span>
        </div>
        <div class="flex-1">
          <Input
            :model-value="field.value"
            placeholder="Value"
            class="h-9 text-sm"
            @update:model-value="updateFieldValue(index, String($event))"
          />
        </div>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          class="h-9 w-9 p-0 text-muted-foreground hover:text-destructive"
          @click="removeField(index)"
        >
          <X class="w-4 h-4" />
        </Button>
      </div>
    </div>

    <p v-if="error" class="text-destructive text-xs">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed, onMounted } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Plus, X } from 'lucide-vue-next'

interface Field {
  id: number
  key: string
  value: string
  keyError?: string
}

const props = withDefaults(
  defineProps<{
    modelValue: Record<string, unknown> | null | undefined
    label?: string
  }>(),
  {
    label: 'Custom Fields'
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown> | null]
}>()

const fields = ref<Field[]>([])
let nextId = 0
let isInternalUpdate = false

function generateId() {
  return nextId++
}

// Initialize fields from modelValue only on mount or external changes
function initializeFromModelValue(value: Record<string, unknown> | null | undefined) {
  if (value && typeof value === 'object' && Object.keys(value).length > 0) {
    fields.value = Object.entries(value).map(([key, val]) => ({
      id: generateId(),
      key,
      value: typeof val === 'string' ? val : JSON.stringify(val)
    }))
  } else {
    fields.value = []
  }
}

onMounted(() => {
  initializeFromModelValue(props.modelValue)
})

// Watch for external changes to modelValue (e.g., when editing existing data)
watch(
  () => props.modelValue,
  (newValue) => {
    // Skip if this change was triggered by our own emit
    if (isInternalUpdate) {
      isInternalUpdate = false
      return
    }
    initializeFromModelValue(newValue)
  }
)

const error = computed(() => {
  const keys = fields.value.map((f) => f.key.trim()).filter((k) => k)
  const duplicates = keys.filter((key, index) => keys.indexOf(key) !== index)
  if (duplicates.length > 0) {
    return `Duplicate key: "${duplicates[0]}"`
  }
  return null
})

function addField() {
  fields.value = [...fields.value, { id: generateId(), key: '', value: '' }]
}

function removeField(index: number) {
  fields.value = fields.value.filter((_, i) => i !== index)
  emitChanges()
}

function updateFieldKey(index: number, value: string) {
  const field = fields.value[index]
  if (field) {
    field.key = value
    // Validate key
    field.keyError = undefined
    if (value.trim() && !/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(value.trim())) {
      field.keyError = 'Invalid key format'
    }
    emitChanges()
  }
}

function updateFieldValue(index: number, value: string) {
  const field = fields.value[index]
  if (field) {
    field.value = value
    emitChanges()
  }
}

function emitChanges() {
  // Build metadata object from valid fields
  const metadata: Record<string, unknown> = {}
  let hasValidFields = false

  for (const field of fields.value) {
    const key = field.key.trim()
    if (key && !field.keyError) {
      hasValidFields = true
      // Try to parse value as JSON, otherwise keep as string
      const value = field.value
      if (value === '') {
        metadata[key] = ''
      } else if (value === 'true') {
        metadata[key] = true
      } else if (value === 'false') {
        metadata[key] = false
      } else if (!isNaN(Number(value)) && value.trim() !== '') {
        metadata[key] = Number(value)
      } else {
        metadata[key] = value
      }
    }
  }

  isInternalUpdate = true
  emit('update:modelValue', hasValidFields ? metadata : null)
}
</script>
