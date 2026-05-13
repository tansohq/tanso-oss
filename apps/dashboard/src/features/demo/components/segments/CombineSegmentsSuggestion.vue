<template>
  <Card class="border-dashed border-blue-200 bg-blue-50/30">
    <CardHeader class="pb-2">
      <CardTitle class="text-sm font-medium flex items-center gap-2">
        <Merge class="h-4 w-4 text-blue-600" />
        Combine with similar segment
      </CardTitle>
    </CardHeader>
    <CardContent class="space-y-4">
      <div v-for="suggestion in suggestions" :key="suggestion.segmentId" class="space-y-3">
        <!-- Similar segment info -->
        <div>
          <p class="text-sm font-medium">{{ suggestion.segmentName }}</p>
          <p class="text-xs text-muted-foreground">
            {{ suggestion.segmentCount }} accounts ·
            {{ Math.round(suggestion.similarityScore * 100) }}% similar
          </p>
        </div>

        <!-- Metric comparison -->
        <div class="grid grid-cols-3 gap-2 text-xs">
          <div v-for="comp in suggestion.comparison" :key="comp.metric" class="space-y-0.5">
            <p class="text-muted-foreground">{{ comp.metric }}</p>
            <p class="font-medium">
              <span>{{ comp.thisValue }}</span>
              <span class="text-muted-foreground"> vs </span>
              <span>{{ comp.otherValue }}</span>
            </p>
          </div>
        </div>

        <!-- Combined result -->
        <div class="flex items-center gap-3 py-2">
          <span class="text-sm text-muted-foreground">Combined:</span>
          <Badge :class="combinedReadinessClass(suggestion)">
            {{ suggestion.combinedCount }} accounts
          </Badge>
          <Badge
            v-if="suggestion.combinedReadiness === 'ready'"
            class="bg-green-100 text-green-700 border-0"
          >
            Ready for experiments
          </Badge>
        </div>

        <!-- Actions -->
        <div class="flex gap-2">
          <Button size="sm" @click="$emit('combine', suggestion.segmentId)"> Combine Now </Button>
          <Button size="sm" variant="outline" @click="$emit('preview', suggestion.segmentId)">
            Preview Combined
          </Button>
        </div>
      </div>
    </CardContent>
  </Card>
</template>

<script setup lang="ts">
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Merge } from 'lucide-vue-next'
import type { CombineSegmentsSuggestion } from '../../types'

defineProps<{
  suggestions: CombineSegmentsSuggestion[]
}>()

defineEmits<{
  combine: [segmentId: string]
  preview: [segmentId: string]
}>()

function combinedReadinessClass(suggestion: CombineSegmentsSuggestion): string {
  switch (suggestion.combinedReadiness) {
    case 'ready':
      return 'bg-green-100 text-green-700 border-0'
    case 'small_sample':
      return 'bg-amber-100 text-amber-700 border-0'
    case 'too_small':
      return 'bg-red-100 text-red-700 border-0'
  }
}
</script>
