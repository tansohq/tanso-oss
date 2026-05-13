<template>
  <DropdownMenu>
    <DropdownMenuTrigger as-child>
      <Button variant="ghost" size="sm" class="h-6 px-1.5 text-xs">
        <ChevronDown class="h-3 w-3" />
      </Button>
    </DropdownMenuTrigger>
    <DropdownMenuContent align="start">
      <DropdownMenuLabel class="text-xs">Aggregation</DropdownMenuLabel>
      <DropdownMenuSeparator />
      <DropdownMenuItem
        v-for="agg in availableAggregations"
        :key="agg.value"
        @click="emit('update', agg.value)"
      >
        <Check
          class="mr-2 h-4 w-4"
          :class="currentAggregation === agg.value ? 'opacity-100' : 'opacity-0'"
        />
        {{ agg.label }}
      </DropdownMenuItem>
    </DropdownMenuContent>
  </DropdownMenu>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ChevronDown, Check } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu'
import type { PivotField, AggregationType } from '../../composables/usePivotState'

const props = defineProps<{
  field: PivotField
  currentAggregation: AggregationType
}>()

const emit = defineEmits<{
  update: [aggregation: AggregationType]
}>()

const allAggregations: { value: AggregationType; label: string }[] = [
  { value: 'sum', label: 'Sum' },
  { value: 'avg', label: 'Average' },
  { value: 'count', label: 'Count' }
]

const availableAggregations = computed(() =>
  allAggregations.filter((agg) => props.field.aggregations?.includes(agg.value))
)
</script>
