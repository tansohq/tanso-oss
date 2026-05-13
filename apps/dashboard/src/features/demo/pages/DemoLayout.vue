<template>
  <div class="w-full min-h-svh flex flex-col">
    <SidebarProvider :default-open="true" class="demo-shell flex-1">
      <DemoSideNav />
      <SidebarInset>
        <header class="flex h-14 items-center gap-2 px-3 border-b md:hidden bg-sidebar text-sidebar-foreground">
          <SidebarTrigger />
        </header>
        <RouterView />
      </SidebarInset>
    </SidebarProvider>
    <PublicDemoBanner />
  </div>
</template>

<script setup lang="ts">
import { onUnmounted } from 'vue'
import { RouterView } from 'vue-router'
import { useQueryClient } from '@tanstack/vue-query'
import { SidebarProvider, SidebarInset, SidebarTrigger } from '@/components/ui/sidebar'
import DemoSideNav from '../components/shared/DemoSideNav.vue'
import PublicDemoBanner from '../components/shared/PublicDemoBanner.vue'
import { useDemoDataSeeder } from '../composables/useDemoDataSeeder'

useDemoDataSeeder()

const queryClient = useQueryClient()
onUnmounted(() => {
  queryClient.clear()
  queryClient.setQueryDefaults(['events'], {})
  queryClient.setDefaultOptions({
    queries: {
      staleTime: 0,
      retry: 3,
      refetchOnWindowFocus: true,
      refetchOnMount: true,
      refetchOnReconnect: true
    }
  })
})
</script>

<style>
:root {
  --demo-banner-h: 52px;
}

/* Fixed sidebar panel — stop above the bottom banner */
.demo-shell :has(> [data-sidebar="sidebar"]:not([data-mobile="true"])) {
  bottom: var(--demo-banner-h) !important;
  height: auto !important;
}

/* Inner sidebar content */
.demo-shell [data-sidebar="sidebar"]:not([data-mobile="true"]) {
  height: 100% !important;
}

/* Sidebar spacer div */
.demo-shell .peer:not([data-mobile="true"]) > div:first-child {
  height: calc(100svh - var(--demo-banner-h)) !important;
}

/* Content area — pad bottom so content isn't hidden behind banner */
.demo-shell > main {
  padding-bottom: var(--demo-banner-h);
}
</style>
