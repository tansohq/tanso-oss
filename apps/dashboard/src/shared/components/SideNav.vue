<template>
  <Sidebar collapsible="icon">
    <SidebarHeader class="border-b border-sidebar-border p-0">
      <div class="flex items-center justify-between h-14 px-2">
        <div class="flex items-center gap-2 overflow-hidden">
          <AppLogo class="group-data-[collapsible=icon]:hidden" />
          <div class="flex flex-col gap-0.5 min-w-0 group-data-[collapsible=icon]:hidden">
            <div class="flex items-baseline gap-2">
              <span class="font-semibold truncate leading-tight">Tanso</span>
            </div>
            <!-- Environment toggle: only in production/sandbox builds -->
            <Popover v-if="showEnvironmentToggle" v-model:open="envMenuOpen">
              <PopoverTrigger as-child>
                <button
                  class="inline-flex items-center gap-1 text-xs font-medium leading-tight text-muted-foreground hover:text-foreground transition-colors cursor-pointer"
                >
                  <span
                    class="inline-flex h-1.5 w-1.5 shrink-0 rounded-full"
                    :class="isSandbox ? 'bg-amber-500' : 'bg-emerald-500'"
                  />
                  {{ isSandbox ? 'Sandbox' : 'Production' }}
                  <ChevronDown class="w-3 h-3" />
                </button>
              </PopoverTrigger>
              <PopoverContent align="start" :side-offset="8" class="w-52 p-1">
                <button
                  class="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm hover:bg-accent transition-colors cursor-pointer"
                  :class="{ 'bg-accent': !isSandbox }"
                  :disabled="environmentStore.isSwitching"
                  @click="switchEnvironment('production')"
                >
                  <span class="inline-flex h-2 w-2 shrink-0 rounded-full bg-emerald-500" />
                  <span class="flex-1 text-left">Production</span>
                  <Check v-if="!isSandbox" class="w-4 h-4 text-foreground" />
                </button>
                <button
                  class="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm hover:bg-accent transition-colors cursor-pointer"
                  :class="{ 'bg-accent': isSandbox }"
                  :disabled="environmentStore.isSwitching"
                  @click="switchEnvironment('sandbox')"
                >
                  <Loader2
                    v-if="environmentStore.isSwitching"
                    class="h-2 w-2 shrink-0 animate-spin"
                  />
                  <span v-else class="inline-flex h-2 w-2 shrink-0 rounded-full bg-amber-500" />
                  <span class="flex-1 text-left">Sandbox</span>
                  <Check v-if="isSandbox" class="w-4 h-4 text-foreground" />
                </button>
              </PopoverContent>
            </Popover>
            <!-- Static label for non-production builds (staging, local) -->
            <span
              v-else
              class="inline-flex items-center gap-1 text-xs font-medium leading-tight text-muted-foreground"
            >
              <span class="inline-flex h-1.5 w-1.5 shrink-0 rounded-full bg-blue-500" />
              {{ environmentLabel }}
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
      <SidebarGroup>
        <SidebarMenu>
          <!-- Getting Started -->
          <SidebarMenuItem>
            <SidebarMenuButton as-child :is-active="isActive('/')" tooltip="Getting Started">
              <router-link to="/">
                <Rocket />
                <span>Getting Started</span>
              </router-link>
            </SidebarMenuButton>
          </SidebarMenuItem>
          <li class="h-px bg-sidebar-border mx-2 my-1" />

          <!-- Events -->
          <SidebarMenuItem>
            <SidebarMenuButton as-child :is-active="isActive('/events')" tooltip="Events">
              <router-link to="/events">
                <Activity />
                <span>Events</span>
              </router-link>
            </SidebarMenuButton>
          </SidebarMenuItem>

          <!-- Setup: Plans, Features, Credits -->
          <li class="h-px bg-sidebar-border mx-2 my-1" />
          <SidebarMenuItem v-for="item in setupItems" :key="item.title">
            <SidebarMenuButton as-child :is-active="isActive(item.to)" :tooltip="item.title">
              <router-link :to="item.to">
                <component :is="item.icon" />
                <span>{{ item.title }}</span>
              </router-link>
            </SidebarMenuButton>
          </SidebarMenuItem>
          <li class="h-px bg-sidebar-border mx-2 my-1" />
          <SidebarMenuItem v-for="item in operateItems" :key="item.title">
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
      <div>
        <SidebarMenu>
          <SidebarMenuItem>
            <Popover v-model:open="demoMenuOpen">
              <PopoverTrigger as-child>
                <SidebarMenuButton tooltip="Demo" class="cursor-pointer">
                  <Play />
                  <span>Demo</span>
                </SidebarMenuButton>
              </PopoverTrigger>
              <PopoverContent align="start" side="top" :side-offset="8" class="w-44 p-1">
                <a
                  href="/demo"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm hover:bg-accent transition-colors"
                  @click="demoMenuOpen = false"
                >
                  <Play class="w-4 h-4" />
                  Try Demo
                </a>
              </PopoverContent>
            </Popover>
          </SidebarMenuItem>
        </SidebarMenu>
        <div class="border-t border-sidebar-border my-1" />
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
        <div class="border-t border-sidebar-border mt-2 pt-2">
          <Popover v-model:open="userMenuOpen">
            <PopoverTrigger as-child>
              <button
                class="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-sidebar-accent transition-colors cursor-pointer group-data-[collapsible=icon]:justify-center"
              >
                <div
                  class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-slate-200 text-xs font-medium text-slate-700"
                >
                  {{ userInitial }}
                </div>
                <div class="flex-1 text-left min-w-0 group-data-[collapsible=icon]:hidden">
                  <div class="text-sm font-medium truncate">{{ userEmail }}</div>
                </div>
                <ChevronDown
                  class="w-3 h-3 text-muted-foreground group-data-[collapsible=icon]:hidden"
                />
              </button>
            </PopoverTrigger>
            <PopoverContent align="start" side="top" :side-offset="8" class="w-52 p-1">
              <div class="px-2 py-1.5 text-xs font-medium text-muted-foreground">Settings</div>
              <router-link
                to="/settings?tab=general"
                class="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm hover:bg-accent transition-colors cursor-pointer"
                @click="userMenuOpen = false"
              >
                <Settings class="w-4 h-4" />
                General
              </router-link>
              <router-link
                to="/settings?tab=integrations"
                class="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm hover:bg-accent transition-colors cursor-pointer"
                @click="userMenuOpen = false"
              >
                <Plug class="w-4 h-4" />
                Integrations
              </router-link>
              <router-link
                to="/settings?tab=data-import"
                class="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm hover:bg-accent transition-colors cursor-pointer"
                @click="userMenuOpen = false"
              >
                <Upload class="w-4 h-4" />
                Data Import
              </router-link>
              <router-link
                v-if="isDeveloperEnvironment"
                to="/settings?tab=developer"
                class="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm hover:bg-accent transition-colors cursor-pointer"
                @click="userMenuOpen = false"
              >
                <Code class="w-4 h-4" />
                Developer
              </router-link>
              <div class="h-px bg-border my-1" />
              <button
                class="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm hover:bg-accent transition-colors cursor-pointer text-destructive"
                @click="handleLogout"
              >
                <LogOut class="w-4 h-4" />
                Log out
              </button>
            </PopoverContent>
          </Popover>
        </div>
      </div>
    </SidebarFooter>
  </Sidebar>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
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
  Settings,
  PanelLeft,
  BarChart3,
  Cpu,
  Coins,
  ChevronDown,
  Check,
  Loader2,
  Play,
  Rocket,
  LogOut,
  Plug,
  Code,
  Upload
} from 'lucide-vue-next'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import AppLogo from '@/shared/components/AppLogo.vue'
import { useEnvironmentStore } from '@/stores/environment'
import { useAuthStore } from '@/stores/auth'
import { env } from '@/lib/env'
import { useTracking } from '@/lib/tracking'
import { useAccountSettingsQuery } from '@/features/integrations/queries'

const route = useRoute()
const { track } = useTracking()
const { toggleSidebar } = useSidebar()
const environmentStore = useEnvironmentStore()
const { data: settingsData } = useAccountSettingsQuery()

const authStore = useAuthStore()
const userMenuOpen = ref(false)
const userEmail = computed(() => authStore.userEmail || '')
const userInitial = computed(() => (userEmail.value ? userEmail.value[0].toUpperCase() : '?'))

function handleLogout() {
  userMenuOpen.value = false
  authStore.logout()
}

// Platform mode — happy path order
const setupItems = [
  { title: 'Plans', to: '/plans', icon: List },
  { title: 'Features', to: '/features', icon: Star },
  { title: 'Credits', to: '/credits', icon: Coins }
]

const operateItems = [
  { title: 'Customers', to: '/customers', icon: Users },
  { title: 'Subscriptions', to: '/subscriptions', icon: RefreshCw },
  { title: 'Invoices', to: '/invoices', icon: FileText }
]

const isSandbox = computed(() => environmentStore.isSandbox)
const isDeveloperEnvironment = computed(() => environmentStore.isDeveloperEnvironment)
const showEnvironmentToggle = computed(
  () => env.environment === 'production' || env.environment === 'sandbox'
)
const environmentLabel = computed(() => {
  if (env.environment === 'staging') return 'Staging'
  if (env.environment === 'local') return 'Local'
  return env.environment
})
const envMenuOpen = ref(false)
const demoMenuOpen = ref(false)

async function switchEnvironment(target: 'production' | 'sandbox') {
  track('environment_switched', { target })
  envMenuOpen.value = false
  if (target === 'sandbox') {
    await environmentStore.switchToSandbox()
  } else {
    environmentStore.switchToProduction()
  }
}

const bottomItems = [
  { title: 'Documentation', url: 'https://github.com/tanso-io/tanso', icon: BookOpen },
  { title: 'Report Issue', url: 'https://github.com/tanso-io/tanso/issues', icon: MessageSquare }
]

function isActive(to: string) {
  if (to === '/') {
    return route.path === '/'
  }
  return route.path === to || route.path.startsWith(to + '/')
}
</script>
