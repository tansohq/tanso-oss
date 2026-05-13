<template>
  <svg :viewBox="`0 0 ${width} ${height}`" class="w-full h-full">
    <polyline
      :points="points"
      fill="none"
      :stroke="strokeColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { CustomerDatapoint, TrendType } from '../../types'

const props = withDefaults(
  defineProps<{
    data: CustomerDatapoint[]
    trend?: TrendType
    width?: number
    height?: number
  }>(),
  {
    trend: 'stable',
    width: 64,
    height: 32
  }
)

const strokeColor = computed(() => {
  switch (props.trend) {
    case 'improving':
      return '#10b981' // emerald-500
    case 'declining':
      return '#ef4444' // red-500
    default:
      return '#9ca3af' // gray-400
  }
})

const points = computed(() => {
  if (!props.data || props.data.length === 0) return ''

  const values = props.data.map((d) => d.value)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1

  const padding = 2
  const usableWidth = props.width - padding * 2
  const usableHeight = props.height - padding * 2

  return props.data
    .map((d, i) => {
      const x = padding + (i / (props.data.length - 1)) * usableWidth
      const y = padding + usableHeight - ((d.value - min) / range) * usableHeight
      return `${x},${y}`
    })
    .join(' ')
})
</script>
