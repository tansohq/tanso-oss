<template>
  <Card>
    <CardHeader>
      <div class="flex items-center justify-between">
        <div>
          <CardTitle class="text-base">Spending Alerts</CardTitle>
          <CardDescription>Configure notifications when usage approaches limits</CardDescription>
        </div>
        <Badge variant="outline" class="text-xs">
          {{ enabledCount }}/{{ thresholds.length }} active
        </Badge>
      </div>
    </CardHeader>
    <CardContent>
      <div class="space-y-3">
        <div
          v-for="threshold in thresholds"
          :key="threshold"
          :class="[
            'flex items-center justify-between p-3 rounded-lg border transition-colors',
            isEnabled(threshold) ? 'bg-muted/30' : 'opacity-60'
          ]"
        >
          <div class="flex items-center gap-3">
            <div
              :class="[
                'flex h-8 w-8 items-center justify-center rounded-full text-sm font-medium',
                getThresholdColor(threshold)
              ]"
            >
              {{ threshold }}%
            </div>
            <div>
              <div class="font-medium text-sm">{{ getThresholdLabel(threshold) }}</div>
              <div class="text-xs text-muted-foreground">
                {{ getThresholdDescription(threshold) }}
              </div>
            </div>
          </div>
          <div class="flex items-center gap-3">
            <!-- Alert status indicator -->
            <div v-if="isTriggered(threshold)" class="flex items-center gap-1 text-amber-600">
              <Bell class="h-3.5 w-3.5" />
              <span class="text-xs font-medium">Triggered</span>
            </div>
            <Switch
              :checked="isEnabled(threshold)"
              @update:checked="toggleThreshold(threshold, $event)"
            />
          </div>
        </div>
      </div>

      <!-- Summary of triggered alerts -->
      <div
        v-if="triggeredAlerts.length > 0"
        class="mt-4 p-3 rounded-lg bg-amber-50 border border-amber-200"
      >
        <div class="flex items-start gap-2">
          <AlertTriangle class="h-4 w-4 text-amber-600 mt-0.5 shrink-0" />
          <div>
            <div class="text-sm font-medium text-amber-800">
              {{ triggeredAlerts.length }} alert{{
                triggeredAlerts.length > 1 ? 's' : ''
              }}
              triggered
            </div>
            <div class="text-xs text-amber-700 mt-1">
              Usage has exceeded {{ triggeredAlerts.map((t) => t + '%').join(', ') }} threshold{{
                triggeredAlerts.length > 1 ? 's' : ''
              }}. Consider reviewing usage patterns or adjusting plan limits.
            </div>
          </div>
        </div>
      </div>
    </CardContent>
  </Card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Bell, AlertTriangle } from 'lucide-vue-next'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import type { SpendingAlertConfig } from '../../types'

const props = withDefaults(
  defineProps<{
    config: SpendingAlertConfig
    currentUsagePercent?: number // Highest usage percentage across all features
  }>(),
  {
    currentUsagePercent: 0
  }
)

const emit = defineEmits<{
  'update:config': [value: SpendingAlertConfig]
}>()

const thresholds = computed(() => props.config.thresholds)

const enabledCount = computed(() => props.config.enabledAlerts.length)

const triggeredAlerts = computed(() => {
  return props.config.enabledAlerts.filter((t) => props.currentUsagePercent >= t)
})

function isEnabled(threshold: number): boolean {
  return props.config.enabledAlerts.includes(threshold)
}

function isTriggered(threshold: number): boolean {
  return isEnabled(threshold) && props.currentUsagePercent >= threshold
}

function toggleThreshold(threshold: number, enabled: boolean) {
  const newEnabledAlerts = enabled
    ? [...props.config.enabledAlerts, threshold].sort((a, b) => a - b)
    : props.config.enabledAlerts.filter((t) => t !== threshold)

  emit('update:config', {
    ...props.config,
    enabledAlerts: newEnabledAlerts
  })
}

function getThresholdColor(threshold: number): string {
  if (threshold >= 100) return 'bg-red-100 text-red-700'
  if (threshold >= 95) return 'bg-red-50 text-red-600'
  if (threshold >= 80) return 'bg-amber-100 text-amber-700'
  return 'bg-yellow-100 text-yellow-700'
}

function getThresholdLabel(threshold: number): string {
  if (threshold >= 100) return 'Limit Reached'
  if (threshold >= 95) return 'Critical Warning'
  if (threshold >= 80) return 'High Usage Warning'
  return 'Usage Milestone'
}

function getThresholdDescription(threshold: number): string {
  if (threshold >= 100) return 'Notify when usage hits the limit'
  if (threshold >= 95) return 'Alert when approaching limit'
  if (threshold >= 80) return 'Warn when usage is getting high'
  return 'Notify at halfway point'
}
</script>
