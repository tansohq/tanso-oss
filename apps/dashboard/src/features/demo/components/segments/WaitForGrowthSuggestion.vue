<template>
  <Card class="border-dashed border-green-200 bg-green-50/30">
    <CardHeader class="pb-2">
      <CardTitle class="text-sm font-medium flex items-center gap-2">
        <Clock class="h-4 w-4 text-green-600" />
        Wait for growth
      </CardTitle>
    </CardHeader>
    <CardContent class="space-y-3">
      <!-- Growth rate info -->
      <div class="text-sm">
        <p>
          This segment is growing
          <span class="font-medium"
            >~{{ suggestion.currentGrowthRate.toFixed(1) }} accounts/month</span
          >.
        </p>
        <p class="text-muted-foreground">
          At this rate, you'll reach 40 accounts in
          <span class="font-medium text-foreground">~{{ suggestion.weeksUntilReady }} weeks</span>.
        </p>
      </div>

      <!-- Projected date -->
      <div class="flex items-center gap-2 text-sm">
        <CalendarDays class="h-4 w-4 text-muted-foreground" />
        <span class="text-muted-foreground">Projected ready date:</span>
        <span class="font-medium">{{ formatDate(suggestion.projectedDate) }}</span>
        <Badge :class="confidenceClass" class="ml-1">
          {{ suggestion.confidence }} confidence
        </Badge>
      </div>

      <!-- Action -->
      <div class="pt-2">
        <Button size="sm" variant="outline" @click="$emit('notify')">
          <Bell class="h-3.5 w-3.5 mr-1.5" />
          Notify Me When Ready
        </Button>
      </div>
    </CardContent>
  </Card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Clock, CalendarDays, Bell } from 'lucide-vue-next'
import type { WaitForGrowthSuggestion } from '../../types'

const props = defineProps<{
  suggestion: WaitForGrowthSuggestion
}>()

defineEmits<{
  notify: []
}>()

const confidenceClass = computed(() => {
  switch (props.suggestion.confidence) {
    case 'high':
      return 'bg-green-100 text-green-700 border-0'
    case 'medium':
      return 'bg-amber-100 text-amber-700 border-0'
    case 'low':
      return 'bg-red-100 text-red-700 border-0'
    default:
      return ''
  }
})

function formatDate(isoDate: string): string {
  const date = new Date(isoDate)
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}
</script>
