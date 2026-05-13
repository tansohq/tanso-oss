<template>
  <Card class="h-full">
    <CardHeader class="pb-3">
      <CardTitle class="text-base">Fields</CardTitle>
      <CardDescription class="text-xs">Drag fields to build your pivot table</CardDescription>
    </CardHeader>
    <CardContent class="space-y-4">
      <!-- Dimensions -->
      <div>
        <div class="flex items-center gap-2 mb-2">
          <Layers class="h-4 w-4 text-muted-foreground" />
          <span class="text-sm font-medium text-muted-foreground">Dimensions</span>
        </div>
        <div class="flex flex-wrap gap-1.5">
          <FieldChip
            v-for="field in availableDimensions"
            :key="field.id"
            :field="field"
            zone="available"
          />
          <span
            v-if="availableDimensions.length === 0"
            class="text-xs text-muted-foreground italic"
          >
            All dimensions in use
          </span>
        </div>
      </div>

      <Separator />

      <!-- Metrics -->
      <div>
        <div class="flex items-center gap-2 mb-2">
          <Hash class="h-4 w-4 text-muted-foreground" />
          <span class="text-sm font-medium text-muted-foreground">Metrics</span>
        </div>
        <div class="flex flex-wrap gap-1.5">
          <FieldChip
            v-for="field in availableMetrics"
            :key="field.id"
            :field="field"
            zone="available"
          />
          <span v-if="availableMetrics.length === 0" class="text-xs text-muted-foreground italic">
            All metrics in use
          </span>
        </div>
      </div>
    </CardContent>
  </Card>
</template>

<script setup lang="ts">
import { Layers, Hash } from 'lucide-vue-next'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import FieldChip from './FieldChip.vue'
import { usePivotState } from '../../composables/usePivotState'

const { availableDimensions, availableMetrics } = usePivotState()
</script>
