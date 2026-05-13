<template>
  <Badge :class="badgeClass">
    {{ formattedMargin }}
  </Badge>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Badge } from '@/components/ui/badge'

const props = defineProps<{
  margin: number | null
}>()

const badgeClass = computed(() => {
  if (props.margin === null) return 'bg-gray-100 text-gray-600 border-gray-200'
  if (props.margin < 0) return 'bg-red-100 text-red-700 border-red-200'
  if (props.margin < 30) return 'bg-red-50 text-red-600 border-red-100'
  if (props.margin < 50) return 'bg-amber-100 text-amber-700 border-amber-200'
  if (props.margin < 70) return 'bg-yellow-100 text-yellow-700 border-yellow-200'
  return 'bg-green-100 text-green-700 border-green-200'
})

const formattedMargin = computed(() => {
  if (props.margin === null) return '—'
  return `${props.margin}%`
})
</script>
