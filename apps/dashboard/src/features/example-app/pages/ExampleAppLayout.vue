<template>
  <div class="min-h-screen bg-gradient-to-b from-slate-50 to-white">
    <ExampleNav />
    <div class="bg-indigo-50 border-b border-indigo-200/60 px-6 py-2 text-center text-sm text-indigo-600 font-medium">
      Interactive preview powered by your live Tanso data
    </div>
    <RouterView />
    <ApiActivityPanel />
  </div>
</template>

<script setup lang="ts">
import ExampleNav from '../components/ExampleNav.vue'
import ApiActivityPanel from '../components/ApiActivityPanel.vue'
</script>
