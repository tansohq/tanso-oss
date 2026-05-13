<template>
  <div class="grid grid-cols-[240px_1fr] gap-4">
    <!-- Left sidebar: Field list -->
    <FieldList />

    <!-- Main content area -->
    <div class="space-y-4 min-w-0">
      <!-- Drop zones -->
      <Card>
        <CardContent class="p-4">
          <div class="flex items-center justify-between mb-4">
            <h3 class="text-sm font-medium">Pivot Configuration</h3>
            <Button v-if="hasConfiguration" variant="outline" size="sm" @click="resetPivot">
              <RotateCcw class="h-4 w-4 mr-1" />
              Reset
            </Button>
          </div>

          <div class="grid grid-cols-2 gap-4">
            <!-- Rows zone -->
            <DropZone
              zone="rows"
              label="Rows"
              :icon="Rows3"
              :fields="rowFields"
              empty-text="Drop dimensions here for row grouping"
              :accept-types="['dimension']"
              @drop="handleDrop('rows', $event)"
              @remove="removeFieldFromZone($event, 'rows')"
            />

            <!-- Columns zone -->
            <DropZone
              zone="columns"
              label="Columns"
              :icon="Columns3"
              :fields="columnFields"
              empty-text="Drop dimensions here for column breakdown"
              :accept-types="['dimension']"
              @drop="handleDrop('columns', $event)"
              @remove="removeFieldFromZone($event, 'columns')"
            />

            <!-- Values zone -->
            <DropZone
              zone="values"
              label="Values"
              :icon="Hash"
              :fields="valueFields"
              empty-text="Drop metrics here to aggregate"
              :accept-types="['metric']"
              @drop="handleDrop('values', $event)"
              @remove="removeFieldFromZone($event, 'values')"
              @update-aggregation="updateAggregation"
            />

            <!-- Filters zone -->
            <div class="space-y-2">
              <DropZone
                zone="filters"
                label="Filters"
                :icon="Filter"
                :fields="filterFieldConfigs"
                empty-text="Drop dimensions here to filter"
                :accept-types="['dimension']"
                @drop="handleDrop('filters', $event)"
                @remove="removeFieldFromZone($event, 'filters')"
              />
              <!-- Filter editors -->
              <div v-if="filters.length > 0" class="flex flex-wrap gap-2 pt-2">
                <div
                  v-for="filter in filters"
                  :key="filter.fieldId"
                  class="flex items-center gap-1"
                >
                  <span class="text-xs text-muted-foreground"
                    >{{ getField(filter.fieldId)?.name }}:</span
                  >
                  <FilterEditor
                    :field-id="filter.fieldId"
                    :filter="filter"
                    @update="updateFilter(filter.fieldId, $event)"
                  />
                  <span
                    v-if="getFilterDisplay(filter)"
                    class="text-xs bg-amber-100 text-amber-800 px-1.5 py-0.5 rounded"
                  >
                    {{ getFilterDisplay(filter) }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      <!-- Results table -->
      <PivotResultsTable :results="pivotResults" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Rows3, Columns3, Hash, Filter, RotateCcw } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import FieldList from './FieldList.vue'
import DropZone from './DropZone.vue'
import FilterEditor from './FilterEditor.vue'
import PivotResultsTable from './PivotResultsTable.vue'
import {
  usePivotState,
  type PivotFilter,
  type PivotFieldConfig
} from '../../composables/usePivotState'

const {
  rowFields,
  columnFields,
  valueFields,
  filters,
  getField,
  addFieldToZone,
  removeFieldFromZone,
  moveField,
  updateAggregation,
  updateFilter,
  resetPivot,
  pivotResults
} = usePivotState()

// Convert filters to field configs for display in drop zone
const filterFieldConfigs = computed<PivotFieldConfig[]>(() =>
  filters.value.map((f) => ({ fieldId: f.fieldId }))
)

// Check if there's any configuration
const hasConfiguration = computed(
  () =>
    rowFields.value.length > 0 ||
    columnFields.value.length > 0 ||
    valueFields.value.length > 0 ||
    filters.value.length > 0
)

// Handle drop events
function handleDrop(
  zone: 'rows' | 'columns' | 'values' | 'filters',
  { fieldId, fromZone }: { fieldId: string; fromZone: string }
) {
  if (fromZone === 'available') {
    addFieldToZone(fieldId, zone)
  } else {
    moveField(fieldId, fromZone as 'rows' | 'columns' | 'values' | 'filters', zone)
  }
}

// Get display text for active filter
function getFilterDisplay(filter: PivotFilter): string {
  const values = Array.isArray(filter.value) ? filter.value : [filter.value]
  if (values.length === 0) return ''
  if (values.length === 1) return values[0]
  return `${values.length} selected`
}
</script>
