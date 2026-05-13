<template>
  <Card class="border-dashed border-amber-200 bg-amber-50/30">
    <CardHeader class="pb-2">
      <CardTitle class="text-sm font-medium flex items-center gap-2">
        <Settings2 class="h-4 w-4 text-amber-600" />
        Relax one rule
      </CardTitle>
    </CardHeader>
    <CardContent class="space-y-4">
      <!-- Bottleneck rule -->
      <div class="text-sm">
        <span class="text-muted-foreground">Your bottleneck: </span>
        <code class="px-1.5 py-0.5 rounded bg-muted text-foreground font-medium">
          {{ suggestion.suggestedChange.attributeLabel }}
          {{ suggestion.suggestedChange.currentOperator }}
          {{ formatValue(suggestion.suggestedChange.currentValue) }}
        </code>
      </div>

      <!-- Suggested change -->
      <div class="text-sm">
        <span class="text-muted-foreground">Change to: </span>
        <code class="px-1.5 py-0.5 rounded bg-green-100 text-green-700 font-medium">
          {{ suggestion.suggestedChange.attributeLabel }}
          {{ suggestion.suggestedChange.suggestedOperator }}
          {{ formatValue(suggestion.suggestedChange.suggestedValue) }}
        </code>
      </div>

      <!-- Before/After transformation -->
      <div class="flex items-center gap-3 py-2">
        <Badge :class="currentReadinessClass">
          {{ suggestion.impact.currentCount }} accounts
        </Badge>
        <ArrowRight class="h-4 w-4 text-muted-foreground" />
        <Badge :class="newReadinessClass"> {{ suggestion.impact.newCount }} accounts </Badge>
      </div>

      <!-- Impact metrics -->
      <div class="text-xs text-muted-foreground space-y-1">
        <div class="flex items-center gap-4">
          <span> Avg ARR: {{ formatChange(suggestion.impact.avgArrChange) }} </span>
          <span> Avg Margin: {{ formatChange(suggestion.impact.avgMarginChange) }} </span>
        </div>
        <div>
          {{ Math.round(suggestion.impact.similarityScore * 100) }}% similar to original segment
        </div>
      </div>

      <!-- Preview of new accounts -->
      <div v-if="suggestion.newAccountsPreview.length > 0" class="pt-2 border-t">
        <p class="text-xs text-muted-foreground mb-2">New accounts that would be added:</p>
        <div class="space-y-1">
          <div
            v-for="account in suggestion.newAccountsPreview.slice(0, 3)"
            :key="account.name"
            class="flex items-center justify-between text-xs"
          >
            <span class="font-medium">{{ account.name }}</span>
            <span class="text-muted-foreground">
              ${{ formatCurrency(account.arr) }} ARR · {{ account.margin }}% margin
            </span>
          </div>
          <p v-if="suggestion.newAccountsPreview.length > 3" class="text-xs text-muted-foreground">
            +{{ suggestion.newAccountsPreview.length - 3 }} more accounts
          </p>
        </div>
      </div>

      <!-- Actions -->
      <div class="flex gap-2 pt-2">
        <Button size="sm" @click="$emit('apply')"> Apply This Change </Button>
        <Button size="sm" variant="outline" @click="$emit('preview')">
          See All New Accounts
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
import { Settings2, ArrowRight } from 'lucide-vue-next'
import type { RelaxRuleSuggestion } from '../../types'
import { getExperimentReadiness } from '../../types'

const props = defineProps<{
  suggestion: RelaxRuleSuggestion
}>()

defineEmits<{
  apply: []
  preview: []
}>()

const currentReadiness = computed(() =>
  getExperimentReadiness(props.suggestion.impact.currentCount)
)
const newReadiness = computed(() => getExperimentReadiness(props.suggestion.impact.newCount))

const currentReadinessClass = computed(() => {
  switch (currentReadiness.value.color) {
    case 'red':
      return 'bg-red-100 text-red-700 border-0'
    case 'yellow':
      return 'bg-amber-100 text-amber-700 border-0'
    case 'green':
      return 'bg-green-100 text-green-700 border-0'
    default:
      return ''
  }
})

const newReadinessClass = computed(() => {
  switch (newReadiness.value.color) {
    case 'red':
      return 'bg-red-100 text-red-700 border-0'
    case 'yellow':
      return 'bg-amber-100 text-amber-700 border-0'
    case 'green':
      return 'bg-green-100 text-green-700 border-0'
    default:
      return ''
  }
})

function formatValue(value: string | number): string {
  if (typeof value === 'number') {
    return value.toLocaleString()
  }
  return value
}

function formatChange(value: number): string {
  const sign = value >= 0 ? '+' : ''
  return `${sign}${Math.round(value * 100)}%`
}

function formatCurrency(value: number): string {
  if (value >= 1000000) return (value / 1000000).toFixed(1) + 'M'
  if (value >= 1000) return (value / 1000).toFixed(0) + 'K'
  return value.toLocaleString()
}
</script>
