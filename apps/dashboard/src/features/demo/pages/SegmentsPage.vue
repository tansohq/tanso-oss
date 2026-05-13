<template>
  <div class="p-6 pb-16">
    <div class="mb-6">
      <h1 class="text-2xl font-semibold text-foreground">Segments</h1>
      <p class="text-muted-foreground mt-1">Group customers by behavior and attributes</p>
    </div>

    <SegmentListView />

    <SegmentBuilderDialog v-model:open="showSegmentBuilder" :segment-id="editingSegmentId" />
  </div>
</template>

<script setup lang="ts">
import { useDemoState } from '../composables/useDemoState'
import SegmentListView from '../components/segments/SegmentListView.vue'
import SegmentBuilderDialog from '../components/segments/SegmentBuilderDialog.vue'

const { showSegmentBuilder, editingSegmentId } = useDemoState()
</script>
