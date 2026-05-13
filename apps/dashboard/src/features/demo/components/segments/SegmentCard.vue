<template>
  <Card
    class="cursor-pointer transition-colors hover:bg-muted/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
    @click="$emit('click')"
  >
    <CardHeader class="pb-3">
      <div class="flex items-center justify-between">
        <CardTitle class="text-base font-semibold tracking-tight">{{ segment.name }}</CardTitle>
        <Badge :variant="marginVariant" class="font-medium tabular-nums">
          {{ segment.grossMarginPct !== null ? `${segment.grossMarginPct}%` : '—' }}
        </Badge>
      </div>
      <CardDescription class="mt-1 tabular-nums">
        {{ segment.customerCount.toLocaleString() }} accounts · ${{
          formatCurrency(segment.mrr)
        }}
        MRR
      </CardDescription>
    </CardHeader>
  </Card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { formatCurrency } from '@/lib/utils'
import type { Segment } from '../../types'

const props = defineProps<{
  segment: Segment
}>()

defineEmits<{
  click: []
}>()

const marginVariant = computed(() => {
  if (props.segment.grossMarginPct === null) return 'secondary'
  if (props.segment.grossMarginPct < 0) return 'destructive'
  if (props.segment.grossMarginPct < 50) return 'outline'
  return 'default'
})
</script>
