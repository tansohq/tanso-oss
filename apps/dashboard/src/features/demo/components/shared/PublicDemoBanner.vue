<template>
  <div
    class="fixed bottom-0 left-0 right-0 z-[100] bg-foreground px-6 py-3 flex items-center justify-between"
  >
    <div>
      <span class="text-sm font-medium text-background">Sample Data</span>
      <span class="text-xs text-background/70 ml-2">Preview with demo data</span>
    </div>
    <div class="flex items-center gap-3">
      <a
        href="https://docs.tanso.dev"
        target="_blank"
        rel="noopener noreferrer"
        class="rounded-md border border-background/50 bg-transparent px-4 py-1.5 text-sm font-medium text-background hover:bg-background/10 transition-colors"
      >
        Learn More
      </a>
      <a
        v-if="isAuthenticated"
        href="https://github.com/tansohq/tanso-oss/issues"
        target="_blank"
        rel="noopener noreferrer"
        class="rounded-md bg-background px-4 py-1.5 text-sm font-medium text-foreground hover:bg-background/90 transition-colors"
      >
        Contact Us
      </a>
      <a
        v-else
        href="/signup"
        class="rounded-md bg-background px-4 py-1.5 text-sm font-medium text-foreground hover:bg-background/90 transition-colors"
      >
        Sign Up
      </a>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const isAuthenticated = computed(() => authStore.isAuthenticated)
</script>
