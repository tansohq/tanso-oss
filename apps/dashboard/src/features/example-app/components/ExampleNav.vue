<template>
  <header class="border-b bg-white/80 backdrop-blur-sm sticky top-0 z-50">
    <div class="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between">
      <div class="flex items-center gap-6">
        <span class="text-lg font-bold tracking-tight">Acme App</span>
        <nav v-if="isSignedUp" class="flex items-center gap-4">
          <router-link
            :to="{ name: 'example-pricing' }"
            class="text-sm font-medium transition-colors"
            :class="isActive('example-pricing') ? 'text-foreground' : 'text-muted-foreground hover:text-foreground'"
          >
            Pricing
          </router-link>
          <router-link
            :to="{ name: 'example-dashboard' }"
            class="text-sm font-medium transition-colors"
            :class="isActive('example-dashboard') ? 'text-foreground' : 'text-muted-foreground hover:text-foreground'"
          >
            Dashboard
          </router-link>
          <router-link
            :to="{ name: 'example-invoices' }"
            class="text-sm font-medium transition-colors"
            :class="isActive('example-invoices') ? 'text-foreground' : 'text-muted-foreground hover:text-foreground'"
          >
            Invoices
          </router-link>
        </nav>
      </div>
      <div class="flex items-center gap-4">
        <span v-if="customerEmail" class="text-sm text-muted-foreground hidden md:block">
          {{ customerEmail }}
        </span>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useExampleAppState } from '../composables/useExampleAppState'

const route = useRoute()
const { isSignedUp, customerEmail } = useExampleAppState()

function isActive(name: string): boolean {
  return route.name === name
}
</script>
