<template>
  <div :class="gridClass">
    <Card v-for="i in count" :key="i" class="overflow-hidden">
      <CardHeader>
        <Skeleton class="h-5 w-32 mb-2" />
        <Skeleton class="h-8 w-24" />
      </CardHeader>
      <CardContent>
        <Skeleton class="h-4 w-full mb-4" />
        <div class="flex gap-2 pt-3 border-t">
          <Skeleton class="h-5 w-16 rounded-full" />
          <Skeleton class="h-5 w-20 rounded-full" />
        </div>
      </CardContent>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Card, CardHeader, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

const props = withDefaults(
  defineProps<{
    count?: number
    columns?: 1 | 2 | 3 | 4
  }>(),
  {
    count: 6,
    columns: 3
  }
)

const gridClass = computed(() => {
  const colClasses: Record<number, string> = {
    1: 'grid grid-cols-1 gap-4',
    2: 'grid grid-cols-1 md:grid-cols-2 gap-4',
    3: 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4',
    4: 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4'
  }
  return colClasses[props.columns]
})
</script>
