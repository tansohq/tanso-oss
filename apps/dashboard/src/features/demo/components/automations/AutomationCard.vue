<template>
  <Card
    class="cursor-pointer hover:bg-muted/50 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
    @click="$emit('click')"
  >
    <CardHeader class="pb-3">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <component :is="typeIcon" class="h-4 w-4" :class="typeIconClass" />
          <CardTitle class="text-base font-semibold tracking-tight">{{
            automation.name
          }}</CardTitle>
        </div>
        <Switch :checked="automation.status === 'active'" @update:checked="$emit('toggle')" />
      </div>
    </CardHeader>
    <CardContent class="space-y-4">
      <!-- Trigger → Actions Flow -->
      <div class="flex items-center gap-2 text-sm">
        <div class="flex items-center gap-1.5 text-muted-foreground">
          <span>{{ automation.trigger.typeLabel }}</span>
        </div>
        <ArrowRight class="h-3.5 w-3.5 text-muted-foreground" />
        <div class="flex items-center gap-1">
          <component
            v-for="(action, index) in automation.actions.slice(0, 3)"
            :key="index"
            :is="getActionIcon(action.type)"
            class="h-3.5 w-3.5 text-muted-foreground"
          />
          <span v-if="automation.actions.length > 3" class="text-xs text-muted-foreground">
            +{{ automation.actions.length - 3 }}
          </span>
        </div>
      </div>

      <!-- Segment Target -->
      <p v-if="automation.trigger.segmentName" class="text-xs text-muted-foreground">
        Target: {{ automation.trigger.segmentName }}
      </p>

      <!-- Stats -->
      <div class="pt-1">
        <span class="text-xs text-muted-foreground">
          {{ automation.stats.triggeredThisWeek }} triggered this week
        </span>
      </div>
    </CardContent>
  </Card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Switch } from '@/components/ui/switch'
import {
  Zap,
  ArrowRight,
  MessageSquare,
  Mail,
  Bell,
  RefreshCw,
  FileText,
  Settings,
  Gauge,
  type LucideIcon
} from 'lucide-vue-next'
import type { Automation, AutomationActionType } from '../../types'

const props = defineProps<{
  automation: Automation
}>()

defineEmits<{
  click: []
  toggle: []
}>()

const typeIcon = computed(() => {
  switch (props.automation.type) {
    case 'alert':
      return Bell
    case 'action':
      return Zap
    default:
      return Zap
  }
})

const typeIconClass = computed(() => {
  switch (props.automation.type) {
    case 'alert':
      return 'text-amber-500'
    case 'action':
      return 'text-blue-500'
    default:
      return 'text-muted-foreground'
  }
})

const actionIconMap: Record<AutomationActionType, LucideIcon> = {
  slack: MessageSquare,
  email: Mail,
  in_app: Bell,
  crm_update: RefreshCw,
  create_task: FileText,
  feature_access: Settings,
  usage_limit: Gauge,
  run_experiment: Zap
}

function getActionIcon(type: AutomationActionType): LucideIcon {
  return actionIconMap[type] || Bell
}
</script>
