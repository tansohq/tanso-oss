<template>
  <div
    class="inline-flex items-center gap-1.5 px-2 py-1 rounded-md text-sm cursor-move select-none"
    :class="chipClass"
    draggable="true"
    @dragstart="handleDragStart"
    @dragend="handleDragEnd"
  >
    <GripVertical class="h-3 w-3 opacity-50" />
    <span class="font-medium">{{ field?.name }}</span>
    <template v-if="showAggregation && aggregation">
      <span class="text-xs opacity-70">({{ aggregationLabel }})</span>
    </template>
    <button
      v-if="removable"
      class="ml-1 hover:bg-black/10 rounded p-0.5 transition-colors"
      @click.stop="emit('remove')"
    >
      <X class="h-3 w-3" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { GripVertical, X } from 'lucide-vue-next'
import type { PivotField, AggregationType } from '../../composables/usePivotState'

const props = defineProps<{
  field: PivotField | undefined
  zone?: 'rows' | 'columns' | 'values' | 'filters' | 'available'
  aggregation?: AggregationType
  showAggregation?: boolean
  removable?: boolean
}>()

const emit = defineEmits<{
  remove: []
  dragStart: [fieldId: string, zone: string]
  dragEnd: []
}>()

const chipClass = computed(() => {
  switch (props.zone) {
    case 'rows':
      return 'bg-blue-100 text-blue-800 border border-blue-200'
    case 'columns':
      return 'bg-purple-100 text-purple-800 border border-purple-200'
    case 'values':
      return 'bg-emerald-100 text-emerald-800 border border-emerald-200'
    case 'filters':
      return 'bg-amber-100 text-amber-800 border border-amber-200'
    default:
      return 'bg-slate-100 text-slate-700 border border-slate-200'
  }
})

const aggregationLabel = computed(() => {
  switch (props.aggregation) {
    case 'sum':
      return 'Sum'
    case 'avg':
      return 'Avg'
    case 'count':
      return 'Count'
    default:
      return ''
  }
})

function handleDragStart(event: DragEvent) {
  if (!props.field) return

  event.dataTransfer?.setData(
    'text/plain',
    JSON.stringify({
      fieldId: props.field.id,
      fromZone: props.zone ?? 'available'
    })
  )

  emit('dragStart', props.field.id, props.zone ?? 'available')
}

function handleDragEnd() {
  emit('dragEnd')
}
</script>
