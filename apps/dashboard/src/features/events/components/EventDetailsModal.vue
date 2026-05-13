<template>
  <Sheet :open="visible" @update:open="emit('update:visible', $event)">
    <SheetContent class="w-full sm:max-w-lg overflow-y-auto">
      <SheetHeader>
        <div class="flex items-center justify-between pr-8">
          <SheetTitle>Event Details</SheetTitle>
          <div class="flex items-center gap-0 border rounded-md">
            <Button
              variant="ghost"
              size="sm"
              class="h-6 w-6 p-0 rounded-r-none"
              :disabled="!canGoPrev"
              @click="emit('navigate', -1)"
            >
              <ChevronUp class="h-3.5 w-3.5" />
            </Button>
            <Button
              variant="ghost"
              size="sm"
              class="h-6 w-6 p-0 rounded-l-none border-l"
              :disabled="!canGoNext"
              @click="emit('navigate', 1)"
            >
              <ChevronDown class="h-3.5 w-3.5" />
            </Button>
          </div>
        </div>
      </SheetHeader>

      <div v-if="event" class="space-y-6 mt-6">
        <!-- Basic Info -->
        <div class="grid grid-cols-2 gap-4">
          <div class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Event Type</Label>
            <Badge class="bg-gray-100/80 text-gray-900 border-0 shadow-none">{{
              event.eventType ?? '—'
            }}</Badge>
          </div>

          <div class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Event Name</Label>
            <p class="text-sm font-medium">{{ event.eventName ?? '—' }}</p>
          </div>

          <div class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Occurred At</Label>
            <p class="text-sm tabular-nums">{{ formatDateTime(event.occurredAt) }}</p>
          </div>

          <div class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Created At</Label>
            <p class="text-sm tabular-nums">{{ formatDateTime(event.createdAt) }}</p>
          </div>

          <div v-if="event.customerReferenceId" class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Customer</Label>
            <p class="text-sm font-medium">{{ event.customerReferenceId }}</p>
          </div>

          <div v-if="event.model" class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Model</Label>
            <p class="text-sm font-medium">{{ event.model }}</p>
          </div>

          <div v-if="event.modelProvider" class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Provider</Label>
            <p class="text-sm font-medium">{{ event.modelProvider }}</p>
          </div>
        </div>

        <!-- IDs Section -->
        <div class="space-y-3">
          <div class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Event ID</Label>
            <div class="flex items-center gap-2">
              <p class="text-sm font-mono bg-muted px-2 py-1 rounded truncate flex-1">
                {{ event.id }}
              </p>
              <CopyButton :value="event.id" label="Event ID" />
            </div>
          </div>

          <div v-if="event.subscriptionId" class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Subscription ID</Label>
            <div class="flex items-center gap-2">
              <p class="text-sm font-mono bg-muted px-2 py-1 rounded truncate flex-1">
                {{ event.subscriptionId }}
              </p>
              <NativeBadge :is-native="event.subscriptionIsNative" />
              <CopyButton :value="event.subscriptionId" label="Subscription ID" />
            </div>
          </div>
        </div>

        <!-- Entitled Status & Usage Units -->
        <div
          v-if="
            event.properties?.isEntitled !== undefined ||
            (event.usageUnits !== null && event.usageUnits !== undefined)
          "
          class="grid grid-cols-2 gap-4"
        >
          <div v-if="event.properties?.isEntitled !== undefined" class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Entitled</Label>
            <Badge
              :class="
                event.properties.isEntitled
                  ? 'bg-emerald-50 text-emerald-700 border border-emerald-200/50'
                  : 'bg-gray-50 text-gray-600 border border-gray-200/50'
              "
              class="w-fit shadow-none"
            >
              {{ event.properties.isEntitled ? 'true' : 'false' }}
            </Badge>
          </div>
          <div
            v-if="event.usageUnits !== null && event.usageUnits !== undefined"
            class="flex flex-col gap-1"
          >
            <Label class="text-muted-foreground text-xs">Usage Units</Label>
            <p class="text-sm tabular-nums">{{ event.usageUnits.toLocaleString() }}</p>
          </div>
        </div>

        <!-- Usage Unit Type -->
        <div v-if="event.usageUnitType" class="flex flex-col gap-1">
          <Label class="text-muted-foreground text-xs">Usage Unit Type</Label>
          <p class="text-sm">{{ event.usageUnitType }}</p>
        </div>

        <!-- Error -->
        <div v-if="event.ingestError" class="flex flex-col gap-1">
          <Label class="text-muted-foreground text-xs">Ingest Error</Label>
          <p class="text-sm text-destructive bg-destructive/10 px-3 py-2 rounded">
            {{ event.ingestError }}
          </p>
        </div>

        <!-- Properties & Meta -->
        <div v-if="hasProperties" class="flex flex-col gap-1">
          <Label class="text-muted-foreground text-xs">Properties</Label>
          <pre class="bg-muted p-4 rounded-lg text-xs font-mono overflow-x-auto">{{
            formatJson(event.properties)
          }}</pre>
        </div>

        <div v-if="hasMeta" class="flex flex-col gap-1">
          <Label class="text-muted-foreground text-xs">Meta</Label>
          <pre class="bg-muted p-4 rounded-lg text-xs font-mono overflow-x-auto">{{
            formatJson(event.meta)
          }}</pre>
        </div>

        <!-- Revenue & Cost Data -->
        <div v-if="hasPriceOrCost" class="grid grid-cols-2 gap-4">
          <div
            v-if="event.revenueAmount !== null && event.revenueAmount !== undefined"
            class="flex flex-col gap-1"
          >
            <Label class="text-muted-foreground text-xs">Revenue</Label>
            <p class="text-sm tabular-nums">{{ formatAmount(event.revenueAmount) }}</p>
          </div>
          <div
            v-if="event.costAmount !== null && event.costAmount !== undefined"
            class="flex flex-col gap-1"
          >
            <Label class="text-muted-foreground text-xs">Cost</Label>
            <p class="text-sm tabular-nums">{{ formatAmount(event.costAmount) }}</p>
          </div>
        </div>

        <!-- Pricing Context -->
        <div v-if="pricingModel || unitPrice" class="grid grid-cols-2 gap-4">
          <div v-if="pricingModel" class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Pricing Model</Label>
            <Badge class="w-fit shadow-none bg-muted text-foreground border-0">{{
              pricingModel
            }}</Badge>
          </div>
          <div v-if="unitPrice" class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Unit Price</Label>
            <p class="text-sm tabular-nums">{{ formatAmount(unitPrice) }}</p>
          </div>
        </div>

        <!-- Additional Details -->
        <Collapsible v-model:open="showAdditionalDetails" class="mt-6">
          <CollapsibleTrigger
            class="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground"
          >
            <ChevronRight
              class="h-4 w-4 transition-transform"
              :class="{ 'rotate-90': showAdditionalDetails }"
            />
            Additional Details
          </CollapsibleTrigger>
          <CollapsibleContent class="mt-4 space-y-3">
            <!-- Customer ID (internal) -->
            <div v-if="event.customerId && !event.customerIsNative" class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Internal Customer ID</Label>
              <div class="flex items-center gap-2">
                <p class="text-sm font-mono bg-muted px-2 py-1 rounded truncate flex-1">
                  {{ event.customerId }}
                </p>
                <CopyButton :value="event.customerId" label="Internal Customer ID" />
              </div>
            </div>

            <!-- Idempotency Key -->
            <div v-if="event.eventIdempotencyKey" class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Idempotency Key</Label>
              <div class="flex items-center gap-2">
                <p class="text-sm font-mono bg-muted px-2 py-1 rounded truncate flex-1">
                  {{ event.eventIdempotencyKey }}
                </p>
                <CopyButton :value="event.eventIdempotencyKey" label="Idempotency Key" />
              </div>
            </div>

            <!-- Flow ID -->
            <div v-if="event.flowId" class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Flow ID</Label>
              <div class="flex items-center gap-2">
                <p class="text-sm font-mono bg-muted px-2 py-1 rounded truncate flex-1">
                  {{ event.flowId }}
                </p>
                <CopyButton :value="event.flowId" label="Flow ID" />
              </div>
            </div>

            <!-- Customer Reference ID -->
            <div v-if="event.customerReferenceId" class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Customer Reference ID</Label>
              <div class="flex items-center gap-2">
                <p class="text-sm font-mono bg-muted px-2 py-1 rounded truncate flex-1">
                  {{ event.customerReferenceId }}
                </p>
                <CopyButton :value="event.customerReferenceId" label="Customer Reference ID" />
              </div>
            </div>

            <!-- Feature ID -->
            <div v-if="event.featureId && !event.featureIsNative" class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Feature ID</Label>
              <div class="flex items-center gap-2">
                <p class="text-sm font-mono bg-muted px-2 py-1 rounded truncate flex-1">
                  {{ event.featureId }}
                </p>
                <CopyButton :value="event.featureId" label="Feature ID" />
              </div>
            </div>

            <!-- Entitlement ID -->
            <div
              v-if="event.entitlementId && !event.entitlementIsNative"
              class="flex flex-col gap-1"
            >
              <Label class="text-muted-foreground text-xs">Entitlement ID</Label>
              <div class="flex items-center gap-2">
                <p class="text-sm font-mono bg-muted px-2 py-1 rounded truncate flex-1">
                  {{ event.entitlementId }}
                </p>
                <CopyButton :value="event.entitlementId" label="Entitlement ID" />
              </div>
            </div>

            <!-- Invoice ID -->
            <div v-if="event.invoiceId && !event.invoiceIsNative" class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Invoice ID</Label>
              <div class="flex items-center gap-2">
                <p class="text-sm font-mono bg-muted px-2 py-1 rounded truncate flex-1">
                  {{ event.invoiceId }}
                </p>
                <CopyButton :value="event.invoiceId" label="Invoice ID" />
              </div>
            </div>

            <!-- Context -->
            <div v-if="hasContext" class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Context</Label>
              <pre class="bg-muted p-4 rounded-lg text-xs font-mono overflow-x-auto">{{
                formatJson(event.context)
              }}</pre>
            </div>

            <!-- Modified At -->
            <div v-if="event.modifiedAt" class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Modified At</Label>
              <p class="text-sm tabular-nums">{{ formatDateTime(event.modifiedAt) }}</p>
            </div>
          </CollapsibleContent>
        </Collapsible>
      </div>
    </SheetContent>
  </Sheet>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChevronRight, ChevronUp, ChevronDown } from 'lucide-vue-next'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Collapsible, CollapsibleTrigger, CollapsibleContent } from '@/components/ui/collapsible'

import CopyButton from '@/components/CopyButton.vue'
import NativeBadge from './NativeBadge.vue'
import { formatDateTime, formatAmount } from '@/lib/formatters'
import type { Event } from '../types'

const props = defineProps<{
  visible: boolean
  event: Event | null
  canGoPrev?: boolean
  canGoNext?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  navigate: [direction: -1 | 1]
}>()

const showAdditionalDetails = ref(false)

const hasProperties = computed(
  () => props.event?.properties && Object.keys(props.event.properties).length > 0
)
const hasMeta = computed(() => props.event?.meta && Object.keys(props.event.meta).length > 0)
const hasContext = computed(
  () => props.event?.context && Object.keys(props.event.context).length > 0
)
const hasPriceOrCost = computed(() => {
  const e = props.event
  return (
    (e?.revenueAmount !== null && e?.revenueAmount !== undefined) ||
    (e?.costAmount !== null && e?.costAmount !== undefined)
  )
})

const pricingModel = computed(() => {
  const ctx = props.event?.context
  if (!ctx) return null
  const val = ctx.sys_pricing_model
  return typeof val === 'string' ? val : null
})

const unitPrice = computed(() => {
  const ctx = props.event?.context
  if (!ctx) return null
  const val = ctx.sys_captured_unit_price
  return typeof val === 'number' ? val : null
})

function formatJson(data: Record<string, unknown> | null | undefined): string {
  if (!data) return '{}'
  return JSON.stringify(data, null, 2)
}
</script>
