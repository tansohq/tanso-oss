<template>
  <div
    class="min-h-[60px] rounded-lg border-2 border-dashed p-2 transition-colors"
    :class="[
      isDragOver ? 'border-primary bg-primary/5' : 'border-muted-foreground/25',
      !canAcceptDrop && isDragOver ? 'border-destructive/50 bg-destructive/5' : ''
    ]"
    @dragover="handleDragOver"
    @dragleave="handleDragLeave"
    @drop="handleDrop"
  >
    <div class="flex items-center gap-1 mb-2">
      <component :is="icon" class="h-4 w-4 text-muted-foreground" />
      <span class="text-sm font-medium text-muted-foreground">{{ label }}</span>
    </div>

    <div v-if="fields.length > 0" class="flex flex-wrap gap-1.5">
      <div v-for="field in fields" :key="field.fieldId" class="flex items-center gap-1">
        <FieldChip
          :field="getField(field.fieldId)"
          :zone="zone"
          :aggregation="field.aggregation"
          :show-aggregation="zone === 'values'"
          removable
          @remove="emit('remove', field.fieldId)"
        />
        <AggregationPicker
          v-if="zone === 'values' && getField(field.fieldId)?.aggregations"
          :field="getField(field.fieldId)!"
          :current-aggregation="field.aggregation ?? 'sum'"
          @update="(agg) => emit('updateAggregation', field.fieldId, agg)"
        />
      </div>
    </div>

    <div v-else class="text-xs text-muted-foreground italic">
      {{ emptyText }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { Component } from 'vue'
import FieldChip from './FieldChip.vue'
import AggregationPicker from './AggregationPicker.vue'
import {
  usePivotState,
  type PivotFieldConfig,
  type AggregationType
} from '../../composables/usePivotState'

const props = defineProps<{
  zone: 'rows' | 'columns' | 'values' | 'filters'
  label: string
  icon: Component
  fields: PivotFieldConfig[]
  emptyText: string
  acceptTypes: ('dimension' | 'metric')[]
}>()

const emit = defineEmits<{
  drop: [payload: { fieldId: string; fromZone: string }]
  remove: [fieldId: string]
  updateAggregation: [fieldId: string, aggregation: AggregationType]
}>()

const { getField } = usePivotState()

const isDragOver = ref(false)
const canAcceptDrop = ref(true)
const draggedFieldId = ref<string | null>(null)

function handleDragOver(event: DragEvent) {
  event.preventDefault()
  isDragOver.value = true

  try {
    const data = event.dataTransfer?.getData('text/plain')
    if (data) {
      const { fieldId } = JSON.parse(data)
      draggedFieldId.value = fieldId
      const field = getField(fieldId)
      canAcceptDrop.value = field ? props.acceptTypes.includes(field.type) : false
    }
  } catch {
    // Data might not be available during dragover in some browsers
    canAcceptDrop.value = true
  }
}

function handleDragLeave() {
  isDragOver.value = false
  canAcceptDrop.value = true
}

function handleDrop(event: DragEvent) {
  event.preventDefault()
  isDragOver.value = false

  try {
    const data = event.dataTransfer?.getData('text/plain')
    if (data) {
      const { fieldId, fromZone } = JSON.parse(data)
      const field = getField(fieldId)

      if (field && props.acceptTypes.includes(field.type)) {
        emit('drop', { fieldId, fromZone })
      }
    }
  } catch (e) {
    console.error('Failed to parse drop data:', e)
  }
}
</script>
