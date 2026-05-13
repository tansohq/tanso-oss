<template>
  <Badge
    v-if="isNative !== null && isNative !== undefined"
    :class="isNative
      ? 'bg-emerald-50 text-emerald-700 border border-emerald-200/50'
      : 'bg-gray-50 text-gray-500 border border-gray-200/50'"
    class="shrink-0 text-[10px] shadow-none"
  >
    {{ isNative ? 'Tanso' : 'External' }}
  </Badge>
</template>

<script setup lang="ts">
import { Badge } from '@/components/ui/badge'

defineProps<{
  isNative: boolean | null | undefined
}>()
</script>
