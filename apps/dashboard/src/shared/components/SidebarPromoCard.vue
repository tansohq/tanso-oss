<template>
  <div
    class="mx-3 my-2 p-3 rounded-lg bg-gradient-to-br from-primary/10 to-primary/5 border border-primary/20"
  >
    <div class="flex items-center gap-1.5 mb-1">
      <Sparkles class="h-3.5 w-3.5 text-primary" />
      <span class="font-semibold text-xs">Sneak Peek</span>
    </div>
    <p class="text-xs text-muted-foreground mb-3">Get a peek at the next features</p>
    <a
      href="/demo"
      target="_blank"
      class="inline-flex items-center justify-center w-full px-3 py-1.5 text-xs font-medium rounded-md bg-primary text-primary-foreground hover:bg-primary/90 transition-colors"
    >
      View Demo
      <ExternalLink class="ml-1.5 h-3 w-3" />
    </a>
  </div>
</template>

<script setup lang="ts">
import { Sparkles, ExternalLink } from 'lucide-vue-next'
</script>
