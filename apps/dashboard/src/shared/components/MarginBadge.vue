<template>
  <span
    v-if="margin !== null && margin !== undefined"
    class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium tabular-nums"
    :class="
      margin >= 0.55
        ? 'text-success'
        : margin >= 0.25
          ? 'text-warning'
          : 'text-destructive'
    "
  >
    {{ (margin * 100).toFixed(0) }}%
  </span>
  <span v-else class="text-muted-foreground text-sm">&mdash;</span>
</template>

<script setup lang="ts">
defineProps<{
  margin: number | null | undefined
}>()
</script>
