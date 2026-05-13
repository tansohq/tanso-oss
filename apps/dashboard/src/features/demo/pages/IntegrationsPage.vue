<template>
  <div class="p-6 pb-16">
    <div class="mb-6">
      <h1 class="text-2xl font-semibold text-foreground">Integrations</h1>
      <p class="text-muted-foreground mt-1">Connect cost data and external services</p>
    </div>

    <CostIntegrationsView />
  </div>
</template>

<script setup lang="ts">
import CostIntegrationsView from '../components/settings/CostIntegrationsView.vue'
</script>
