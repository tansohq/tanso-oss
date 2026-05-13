<template>
  <div class="p-6 pb-16">
    <div class="mb-6">
      <h1 class="text-2xl font-semibold text-foreground">Automations</h1>
      <p class="text-muted-foreground mt-1">Automate actions based on customer events</p>
    </div>

    <AutomationListView />

    <AutomationBuilderDialog
      v-model:open="showAutomationBuilder"
      :automation-id="editingAutomationId"
    />
  </div>
</template>

<script setup lang="ts">
import { useDemoState } from '../composables/useDemoState'
import AutomationListView from '../components/automations/AutomationListView.vue'
import AutomationBuilderDialog from '../components/automations/AutomationBuilderDialog.vue'

const { showAutomationBuilder, editingAutomationId } = useDemoState()
</script>
