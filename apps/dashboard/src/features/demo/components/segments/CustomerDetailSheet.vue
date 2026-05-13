<template>
  <Sheet :open="open" @update:open="$emit('update:open', $event)">
    <SheetContent side="right" class="w-full sm:max-w-[500px] overflow-y-auto">
      <SheetHeader>
        <SheetTitle>{{ customer?.name }}</SheetTitle>
        <SheetDescription>Customer Profile</SheetDescription>
      </SheetHeader>

      <div v-if="customer" class="mt-6 space-y-6">
        <!-- Overview -->
        <div>
          <h4 class="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-3">
            Overview
          </h4>
          <div class="space-y-2 text-sm">
            <div class="flex justify-between">
              <span class="text-muted-foreground">MRR</span>
              <span class="font-medium">${{ customer.mrr.toLocaleString() }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-muted-foreground">Plan</span>
              <span>{{ customer.plan }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-muted-foreground">Seats</span>
              <span>{{ customer.seats }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-muted-foreground">Customer Since</span>
              <span>{{ customer.customerSince }}</span>
            </div>
          </div>
        </div>

        <Separator />

        <!-- Unit Economics -->
        <div>
          <h4 class="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-3">
            Unit Economics
          </h4>
          <div class="space-y-2 text-sm">
            <div class="flex justify-between items-center">
              <span class="text-muted-foreground">Gross Margin</span>
              <div class="flex items-center gap-2">
                <MarginBadge :margin="customer.grossMarginPct" />
                <TrendIndicator
                  v-if="customer.marginTrend !== null && customer.marginTrend !== 0"
                  :direction="customer.marginTrend < 0 ? 'down' : 'up'"
                  :value="Math.abs(customer.marginTrend)"
                  suffix="pp from 30d ago"
                />
              </div>
            </div>
            <div class="flex justify-between">
              <span class="text-muted-foreground">Cost/Seat</span>
              <span>${{ customer.costPerSeat?.toFixed(2) || '—' }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-muted-foreground">API Cost (30d)</span>
              <span>${{ customer.apiCost30d?.toLocaleString() || '—' }}</span>
            </div>
          </div>
        </div>

        <Separator />

        <!-- Revenue Breakdown -->
        <div>
          <h4 class="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-3">
            Revenue Breakdown
          </h4>
          <div class="space-y-2 text-sm">
            <div class="flex justify-between">
              <span class="text-muted-foreground">CARR (contracted)</span>
              <span>${{ customer.carr.toLocaleString() }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-muted-foreground">UARR (usage-based)</span>
              <span>${{ customer.uarr.toLocaleString() }}</span>
            </div>
          </div>
        </div>

        <Separator />

        <!-- Segments -->
        <div>
          <h4 class="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-3">
            Segments
          </h4>
          <div class="flex flex-wrap gap-2">
            <Badge v-for="segment in customer.segments" :key="segment" variant="secondary">
              {{ segment }}
            </Badge>
          </div>
        </div>

        <!-- Active Experiments -->
        <div v-if="customer.activeExperiments.length > 0">
          <h4 class="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-3">
            Active Experiments
          </h4>
          <div class="space-y-2">
            <div
              v-for="exp in customer.activeExperiments"
              :key="exp.id"
              class="flex items-center justify-between p-2 bg-muted/50 rounded text-sm"
            >
              <div class="flex items-center gap-2">
                <FlaskConical class="h-4 w-4 text-muted-foreground" />
                <span>{{ exp.name }}</span>
              </div>
              <div class="text-muted-foreground">
                {{ exp.variant }} · {{ exp.enrolledDaysAgo }}d ago
              </div>
            </div>
          </div>
        </div>

        <!-- Automation History -->
        <div v-if="customer.automationHistory.length > 0">
          <h4 class="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-3">
            Automation History
          </h4>
          <div class="space-y-3">
            <div v-for="(event, index) in customer.automationHistory" :key="index" class="text-sm">
              <div class="flex items-center gap-2 font-medium">
                <Workflow class="h-4 w-4 text-muted-foreground" />
                <span>"{{ event.name }}" triggered</span>
                <span class="text-muted-foreground font-normal">— {{ event.triggeredAt }}</span>
              </div>
              <div class="ml-6 text-muted-foreground">Actions: {{ event.actions.join(', ') }}</div>
            </div>
          </div>
        </div>

        <!-- Margin Trend Chart Placeholder -->
        <div>
          <h4 class="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-3">
            Margin Trend (30 Days)
          </h4>
          <div
            class="h-32 bg-muted/30 rounded flex items-center justify-center text-muted-foreground text-sm"
          >
            Chart showing margin trend over time
          </div>
        </div>
      </div>
    </SheetContent>
  </Sheet>
</template>

<script setup lang="ts">
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription
} from '@/components/ui/sheet'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import { FlaskConical, Workflow } from 'lucide-vue-next'
import type { SegmentCustomer } from '../../types'
import MarginBadge from '../shared/MarginBadge.vue'
import TrendIndicator from '../shared/TrendIndicator.vue'

defineProps<{
  open: boolean
  customer: SegmentCustomer | null
}>()

defineEmits<{
  'update:open': [value: boolean]
}>()
</script>
