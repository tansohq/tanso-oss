<template>
  <div class="fixed top-0 left-0 right-0 z-50 bg-zinc-900">
    <div class="flex items-center justify-center gap-2 px-4 py-2">
      <Sparkles class="h-3.5 w-3.5 text-zinc-400" />
      <p class="text-sm text-zinc-300">
        <span class="font-medium">Demo Mode</span>
        <span class="mx-2 text-zinc-600">·</span>
        <span class="text-zinc-400">Explore upcoming features</span>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Sparkles } from 'lucide-vue-next'
</script>
