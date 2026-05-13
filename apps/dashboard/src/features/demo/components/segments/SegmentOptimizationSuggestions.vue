<template>
  <div v-if="suggestions" class="space-y-4">
    <!-- Header -->
    <div class="flex items-center gap-2">
      <Lightbulb class="h-4 w-4 text-amber-500" />
      <h4 class="text-sm font-medium">Ways to Reach Experiment-Ready</h4>
    </div>

    <!-- Suggestion cards -->
    <div class="space-y-3">
      <RelaxRuleSuggestion
        v-if="suggestions.relaxRule"
        :suggestion="suggestions.relaxRule"
        @apply="handleApplyRelaxRule"
        @preview="handlePreviewNewAccounts"
      />

      <CombineSegmentsSuggestion
        v-if="suggestions.combineSegments?.length"
        :suggestions="suggestions.combineSegments"
        @combine="handleCombine"
        @preview="handlePreviewCombine"
      />

      <WaitForGrowthSuggestion
        v-if="suggestions.waitForGrowth"
        :suggestion="suggestions.waitForGrowth"
        @notify="handleNotify"
      />
    </div>

    <!-- Fallback when no good suggestions -->
    <Card v-if="!hasAnySuggestion" class="border-dashed">
      <CardContent class="py-6 text-center">
        <p class="text-sm text-muted-foreground mb-3">
          We couldn't find a good way to expand this segment while keeping it meaningful.
        </p>
        <div class="flex justify-center gap-2">
          <Button size="sm" variant="outline" @click="$emit('run-switchback')">
            Run Switchback Test
          </Button>
          <Button size="sm" variant="outline" @click="$emit('edit-rules')">
            Edit Segment Rules
          </Button>
        </div>
      </CardContent>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Lightbulb } from 'lucide-vue-next'
import { useToast } from '@/components/ui/toast'
import type { SegmentOptimizationSuggestions } from '../../types'
import RelaxRuleSuggestion from './RelaxRuleSuggestion.vue'
import CombineSegmentsSuggestion from './CombineSegmentsSuggestion.vue'
import WaitForGrowthSuggestion from './WaitForGrowthSuggestion.vue'

const props = defineProps<{
  suggestions: SegmentOptimizationSuggestions | null
}>()

defineEmits<{
  'run-switchback': []
  'edit-rules': []
}>()

const { toast } = useToast()

const hasAnySuggestion = computed(() => {
  if (!props.suggestions) return false
  return !!(
    props.suggestions.relaxRule ||
    (props.suggestions.combineSegments && props.suggestions.combineSegments.length > 0) ||
    props.suggestions.waitForGrowth
  )
})

function handleApplyRelaxRule() {
  toast({
    title: 'Apply Rule Change',
    description: 'This would update the segment rules. Coming soon!'
  })
}

function handlePreviewNewAccounts() {
  toast({
    title: 'Preview New Accounts',
    description: 'This would show all accounts that would be added. Coming soon!'
  })
}

function handleCombine(segmentId: string) {
  toast({
    title: 'Combine Segments',
    description: `This would combine with segment ${segmentId}. Coming soon!`
  })
}

function handlePreviewCombine(segmentId: string) {
  toast({
    title: 'Preview Combined Segment',
    description: `This would preview the combined segment with ${segmentId}. Coming soon!`
  })
}

function handleNotify() {
  toast({
    title: 'Notification Set',
    description: "We'll notify you when this segment reaches experiment-ready size."
  })
}
</script>
