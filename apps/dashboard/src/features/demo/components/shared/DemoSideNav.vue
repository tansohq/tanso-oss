<template>
  <Sidebar collapsible="icon">
    <SidebarHeader class="border-b border-sidebar-border p-0">
      <div class="flex items-center justify-between h-14 px-3">
        <div class="flex items-center gap-2 overflow-hidden">
          <AppLogo />
          <div class="flex flex-col gap-0.5 min-w-0 group-data-[collapsible=icon]:hidden">
            <div class="flex items-baseline gap-2">
              <span class="font-semibold truncate leading-tight">Tanso</span>
              <span class="rounded-full bg-muted px-1.5 py-0.5 text-xs font-medium text-zinc-600 dark:text-zinc-400 leading-none">{{ isObserveDemo ? 'Observe' : 'Platform' }}</span>
            </div>
            <span class="inline-flex items-center gap-1 text-xs font-medium leading-tight text-muted-foreground">
              <span class="inline-flex h-1.5 w-1.5 shrink-0 rounded-full bg-emerald-500" />
              Demo
            </span>
          </div>
        </div>
        <button
          class="flex h-7 w-7 shrink-0 items-center justify-center rounded-md hover:bg-sidebar-accent cursor-pointer transition-colors"
          aria-label="Toggle Sidebar"
          @click="toggleSidebar"
        >
          <PanelLeft class="h-4 w-4" />
        </button>
      </div>
    </SidebarHeader>

    <SidebarContent>
      <!-- Observe mode -->
      <SidebarGroup v-if="isObserveDemo">
        <SidebarMenu>
          <SidebarMenuItem v-for="item in observeItems" :key="item.title">
            <SidebarMenuButton as-child :is-active="isActive(item.to)" :tooltip="item.title">
              <router-link :to="{ path: item.to, query: { mode: 'observe' } }">
                <component :is="item.icon" />
                <span>{{ item.title }}</span>
              </router-link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarGroup>

      <!-- Platform mode -->
      <SidebarGroup v-else>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton as-child :is-active="isActive('/demo/getting-started')" tooltip="Getting Started">
              <router-link to="/demo/getting-started">
                <Rocket />
                <span>Getting Started</span>
              </router-link>
            </SidebarMenuButton>
          </SidebarMenuItem>
          <li class="h-px bg-sidebar-border mx-2 my-1" />
          <SidebarMenuItem v-for="item in setupItems" :key="item.title">
            <SidebarMenuButton as-child :is-active="isActive(item.to)" :tooltip="item.title">
              <router-link :to="item.to">
                <component :is="item.icon" />
                <span>{{ item.title }}</span>
              </router-link>
            </SidebarMenuButton>
          </SidebarMenuItem>
          <li class="h-px bg-sidebar-border mx-2" />
          <SidebarMenuItem v-for="item in operateItems" :key="item.title">
            <SidebarMenuButton as-child :is-active="isActive(item.to)" :tooltip="item.title">
              <router-link :to="item.to">
                <component :is="item.icon" />
                <span>{{ item.title }}</span>
              </router-link>
            </SidebarMenuButton>
          </SidebarMenuItem>
          <li class="h-px bg-sidebar-border mx-2" />
          <SidebarMenuItem v-for="item in platformObserveItems" :key="item.title">
            <SidebarMenuButton as-child :is-active="isActive(item.to)" :tooltip="item.title">
              <router-link :to="item.to">
                <component :is="item.icon" />
                <span>{{ item.title }}</span>
              </router-link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarGroup>
    </SidebarContent>

    <SidebarFooter>
      <div class="border-t border-sidebar-border pt-2">
        <SidebarMenu>
          <SidebarMenuItem v-for="item in bottomItems" :key="item.title">
            <SidebarMenuButton as-child :tooltip="item.title">
              <a :href="item.url" target="_blank" rel="noopener noreferrer">
                <component :is="item.icon" />
                <span>{{ item.title }}</span>
              </a>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </div>
    </SidebarFooter>
  </Sidebar>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar
} from '@/components/ui/sidebar'
import {
  List,
  Star,
  Users,
  RefreshCw,
  FileText,
  Activity,
  BookOpen,
  Map as MapIcon,
  MessageSquare,
  PanelLeft,
  BarChart3,
  Cpu,
  Coins,
  Rocket
} from 'lucide-vue-next'
import AppLogo from '@/shared/components/AppLogo.vue'

const route = useRoute()
const { toggleSidebar } = useSidebar()

const isObserveDemo = computed(() => route.query.mode === 'observe')

// Observe mode — matches real SideNav order
const observeItems = [
  { title: 'Getting Started', to: '/demo/sources', icon: Rocket },
  { title: 'Analytics', to: '/demo/analytics', icon: BarChart3 },
  { title: 'Events', to: '/demo/events', icon: Activity },
  { title: 'Models', to: '/demo/models', icon: Cpu },
]

// Platform mode — happy path order
const setupItems = [
  { title: 'Plans', to: '/demo/plans', icon: List },
  { title: 'Features', to: '/demo/features', icon: Star },
  { title: 'Credits', to: '/demo/credits', icon: Coins }
]

const operateItems = [
  { title: 'Customers', to: '/demo/customers', icon: Users },
  { title: 'Subscriptions', to: '/demo/subscriptions', icon: RefreshCw },
  { title: 'Invoices', to: '/demo/invoices', icon: FileText }
]

const platformObserveItems = [
  { title: 'Analytics', to: '/demo/analytics', icon: BarChart3 },
  { title: 'Events', to: '/demo/events', icon: Activity },
  { title: 'Models', to: '/demo/models', icon: Cpu }
]

const bottomItems = [
  { title: 'API Docs', url: 'https://tanso-core.readme.io/', icon: BookOpen },
  { title: 'Roadmap', url: 'https://github.com/tanso-io/tanso/issues', icon: MapIcon },
  { title: 'Give Feedback', url: 'https://github.com/tanso-io/tanso/issues', icon: MessageSquare }
]

function isActive(to: string) {
  if (to === '/demo') {
    return route.path === '/demo'
  }
  return route.path === to || route.path.startsWith(to + '/')
}
</script>
