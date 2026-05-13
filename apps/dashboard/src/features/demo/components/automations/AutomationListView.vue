<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-4">
        <!-- Search -->
        <div class="relative">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            v-model="searchQuery"
            placeholder="Search automations..."
            class="pl-9 w-64 lg:w-80"
          />
        </div>
        <!-- Type Filters -->
        <div class="flex gap-2">
          <Button
            v-for="filter in filters"
            :key="filter.value"
            :variant="automationFilter === filter.value ? 'default' : 'outline'"
            size="sm"
            @click="automationFilter = filter.value"
          >
            <component :is="filter.icon" class="h-3.5 w-3.5 mr-2" />
            {{ filter.label }}
          </Button>
        </div>
      </div>
      <!-- Create Dropdown -->
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button>
            <Plus class="h-4 w-4 mr-2" />
            Create New
            <ChevronDown class="h-4 w-4 ml-2" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem @click="openAutomationBuilder">
            <Bell class="h-4 w-4 mr-2" />
            Alert
          </DropdownMenuItem>
          <DropdownMenuItem @click="openAutomationBuilder">
            <Zap class="h-4 w-4 mr-2" />
            Action
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>

    <!-- Stats -->
    <div class="grid grid-cols-3 gap-5">
      <Card class="p-6">
        <div class="flex items-center gap-5">
          <div class="rounded-xl bg-primary/10 p-2.5">
            <Zap class="h-5 w-5 text-primary" />
          </div>
          <div>
            <p class="text-sm text-muted-foreground">Active</p>
            <p class="text-2xl font-semibold tracking-tight">{{ activeCount }}</p>
          </div>
        </div>
      </Card>
      <Card class="p-6">
        <div class="flex items-center gap-5">
          <div class="rounded-xl bg-blue-500/10 p-2.5">
            <Activity class="h-5 w-5 text-blue-500" />
          </div>
          <div>
            <p class="text-sm text-muted-foreground">Triggered This Week</p>
            <p class="text-2xl font-semibold tracking-tight">{{ triggeredThisWeek }}</p>
          </div>
        </div>
      </Card>
      <Card class="p-6">
        <div class="flex items-center gap-5">
          <div class="rounded-xl bg-green-500/10 p-2.5">
            <CheckCircle class="h-5 w-5 text-green-500" />
          </div>
          <div>
            <p class="text-sm text-muted-foreground">Actions Executed</p>
            <p class="text-2xl font-semibold tracking-tight">{{ actionsExecuted }}</p>
          </div>
        </div>
      </Card>
    </div>

    <!-- Automation Cards -->
    <div
      v-if="filteredAutomations.length > 0"
      class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5"
    >
      <AutomationCard
        v-for="automation in filteredAutomations"
        :key="automation.id"
        :automation="automation"
        @click="editAutomation(automation.id)"
        @toggle="toggleAutomation(automation.id)"
      />
    </div>

    <!-- Empty State -->
    <div v-else class="flex flex-col items-center justify-center py-16 text-muted-foreground">
      <Inbox class="w-16 h-16 mb-6 text-muted-foreground/50" />
      <p class="text-lg font-medium text-foreground mb-2">No automations found</p>
      <p class="text-sm mb-4">Try adjusting your search or filters</p>
      <Button
        variant="outline"
        @click="
          () => {
            automationFilter = 'all'
            searchQuery = ''
          }
        "
      >
        Clear Filters
      </Button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu'
import {
  Plus,
  Zap,
  Activity,
  Bell,
  ChevronDown,
  Search,
  Inbox,
  LayoutGrid,
  CheckCircle
} from 'lucide-vue-next'
import { useDemoState } from '../../composables/useDemoState'
import AutomationCard from './AutomationCard.vue'

type AutomationFilterType = 'all' | 'alerts' | 'actions'

const { automationsData, toggleAutomation, openAutomationBuilder, editAutomation } = useDemoState()

const automationFilter = ref<AutomationFilterType>('all')
const searchQuery = ref('')

const filters = [
  { label: 'All', value: 'all' as const, icon: LayoutGrid },
  { label: 'Alerts', value: 'alerts' as const, icon: Bell },
  { label: 'Actions', value: 'actions' as const, icon: Zap }
]

// Filter out experiments - only show alerts and actions
const automationsOnly = computed(() => {
  return automationsData.value.filter((a) => a.type !== 'experiment')
})

const filteredAutomations = computed(() => {
  let result = automationsOnly.value

  // Apply type filter
  if (automationFilter.value !== 'all') {
    const typeMap: Record<string, string> = {
      alerts: 'alert',
      actions: 'action'
    }
    result = result.filter((a) => a.type === typeMap[automationFilter.value])
  }

  // Apply search
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(
      (a) =>
        a.name.toLowerCase().includes(query) ||
        a.trigger.typeLabel.toLowerCase().includes(query) ||
        a.trigger.segmentName?.toLowerCase().includes(query)
    )
  }

  return result
})

const activeCount = computed(() => {
  return automationsOnly.value.filter((a) => a.status === 'active').length
})

const triggeredThisWeek = computed(() => {
  return automationsOnly.value.reduce((sum, a) => sum + a.stats.triggeredThisWeek, 0)
})

const actionsExecuted = computed(() => {
  return automationsOnly.value.reduce((sum, a) => sum + a.stats.actionsExecuted, 0)
})
</script>
