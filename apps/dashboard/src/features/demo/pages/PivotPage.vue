<template>
  <div class="p-6 pb-16">
    <div class="mb-6">
      <h1 class="text-2xl font-semibold text-foreground">Analyze</h1>
      <p class="text-muted-foreground mt-1">Explore your data with flexible pivot tables</p>
    </div>

    <PivotBuilder />
  </div>
</template>

<script setup lang="ts">
import PivotBuilder from '../components/pivot/PivotBuilder.vue'
</script>
