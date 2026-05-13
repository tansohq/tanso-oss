<template>
  <DropdownMenu>
    <DropdownMenuTrigger as-child>
      <Button variant="ghost" size="sm" class="h-6 px-1.5 text-xs">
        <Filter class="h-3 w-3" />
      </Button>
    </DropdownMenuTrigger>
    <DropdownMenuContent class="w-56" align="start">
      <div class="p-2">
        <div class="flex items-center justify-between mb-2">
          <span class="text-sm font-medium">Filter: {{ field?.name }}</span>
          <Button
            v-if="hasValue"
            variant="ghost"
            size="sm"
            class="h-6 px-2 text-xs"
            @click="clearFilter"
          >
            Clear
          </Button>
        </div>

        <div class="space-y-1 max-h-48 overflow-y-auto">
          <div v-for="option in options" :key="option" class="flex items-center space-x-2 py-1">
            <Checkbox
              :id="`filter-${fieldId}-${option}`"
              :checked="isSelected(option)"
              @update:checked="(checked: boolean) => toggleOption(option, checked)"
            />
            <label :for="`filter-${fieldId}-${option}`" class="text-sm leading-none cursor-pointer">
              {{ formatOption(option) }}
            </label>
          </div>
        </div>
      </div>
    </DropdownMenuContent>
  </DropdownMenu>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Filter } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu'
import { usePivotState, type PivotFilter } from '../../composables/usePivotState'

const props = defineProps<{
  fieldId: string
  filter: PivotFilter
}>()

const emit = defineEmits<{
  update: [value: string[]]
}>()

const { getField, getFilterOptions } = usePivotState()

const field = computed(() => getField(props.fieldId))
const options = computed(() => getFilterOptions(props.fieldId))

const selectedValues = computed(() => {
  const val = props.filter.value
  return Array.isArray(val) ? val : val ? [val] : []
})

const hasValue = computed(() => selectedValues.value.length > 0)

function isSelected(option: string): boolean {
  return selectedValues.value.includes(option)
}

function toggleOption(option: string, checked: boolean) {
  let newValues: string[]
  if (checked) {
    newValues = [...selectedValues.value, option]
  } else {
    newValues = selectedValues.value.filter((v) => v !== option)
  }
  emit('update', newValues)
}

function clearFilter() {
  emit('update', [])
}

function formatOption(option: string): string {
  // Format margin status for display
  if (props.fieldId === 'marginStatus') {
    switch (option) {
      case 'healthy':
        return 'Healthy'
      case 'at_risk':
        return 'At Risk'
      case 'underwater':
        return 'Underwater'
    }
  }
  return option
}
</script>
