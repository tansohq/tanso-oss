<template>
  <div class="space-y-4">
    <!-- Header with mode description -->
    <div class="flex items-center justify-between">
      <Label class="text-xs text-muted-foreground">
        {{
          mode === 'graduated'
            ? 'Graduated Tiers (each tier applies to units in that range)'
            : 'Volume Tiers (all units priced at tier reached)'
        }}
      </Label>
    </div>

    <!-- Visual Range Bar -->
    <div class="relative">
      <!-- Bar background -->
      <div class="h-10 rounded-lg bg-muted flex overflow-hidden">
        <div
          v-for="(tier, index) in modelValue"
          :key="index"
          :style="getTierBarStyle(tier, index)"
          :class="[
            'flex items-center justify-center text-xs font-medium border-r border-background last:border-r-0 transition-all',
            getTierBarColor(index)
          ]"
        >
          <span v-if="tier.unitPrice === 0" class="opacity-70">Free</span>
          <span v-else class="opacity-90">${{ formatPrice(tier.unitPrice) }}</span>
        </div>
      </div>
      <!-- Boundary markers -->
      <div class="flex justify-between mt-1 text-[10px] text-muted-foreground font-mono">
        <span>0</span>
        <template v-for="(tier, index) in modelValue.slice(0, -1)" :key="'marker-' + index">
          <span :style="{ marginLeft: `${getTierEndPosition(tier, index)}%` }">
            {{ tier.lastUnit?.toLocaleString() }}
          </span>
        </template>
        <span>∞</span>
      </div>
    </div>

    <!-- Tier Rows with natural language -->
    <div class="space-y-2">
      <div
        v-for="(tier, index) in modelValue"
        :key="index"
        :class="['p-3 rounded-lg border transition-colors', 'hover:bg-muted/50']"
      >
        <!-- Top row: Description and Remove button -->
        <div class="flex items-center justify-between mb-2">
          <div class="flex items-center gap-2">
            <div :class="['w-2 h-5 rounded-full shrink-0', getTierDotColor(index)]" />
            <div>
              <span class="font-medium text-sm">{{ getTierDescription(tier, index) }}</span>
              <span class="text-xs text-muted-foreground ml-2">
                {{ mode === 'graduated' ? '(each unit)' : '(all units)' }}
              </span>
            </div>
          </div>
          <Button
            v-if="modelValue.length > 1 && index < modelValue.length - 1"
            variant="ghost"
            size="sm"
            class="h-7 w-7 p-0 shrink-0"
            @click="removeTier(index)"
          >
            <X class="h-4 w-4 text-muted-foreground hover:text-destructive" />
          </Button>
        </div>

        <!-- Bottom row: Inputs -->
        <div class="flex items-center gap-4 pl-4">
          <!-- Tier Range Input (for non-unlimited tiers) -->
          <div v-if="tier.lastUnit !== null" class="flex items-center gap-1.5">
            <Label class="text-xs text-muted-foreground whitespace-nowrap">Up to</Label>
            <Input
              :model-value="tier.lastUnit"
              @update:model-value="updateTierLastUnit(index, $event)"
              type="number"
              min="1"
              class="h-8 w-24 font-mono text-sm"
            />
          </div>
          <div v-else class="flex items-center gap-1.5">
            <Label class="text-xs text-muted-foreground">Limit</Label>
            <span class="text-sm font-medium">Unlimited</span>
          </div>

          <!-- Flat Fee (optional) -->
          <div class="flex items-center gap-1.5">
            <Label class="text-xs text-muted-foreground whitespace-nowrap">Flat fee</Label>
            <div class="flex items-center">
              <span class="text-sm text-muted-foreground">$</span>
              <Input
                :model-value="tier.flatFee ?? 0"
                @update:model-value="updateTierFlatFee(index, $event)"
                type="number"
                step="0.01"
                min="0"
                placeholder="0"
                class="h-8 w-20 font-mono text-sm"
              />
            </div>
          </div>

          <!-- Unit Price -->
          <div class="flex items-center gap-1.5">
            <Label class="text-xs text-muted-foreground whitespace-nowrap">Per unit</Label>
            <div class="flex items-center">
              <span class="text-sm text-muted-foreground">$</span>
              <Input
                :model-value="tier.unitPrice"
                @update:model-value="updateTierPrice(index, $event)"
                type="number"
                step="0.0001"
                min="0"
                placeholder="0"
                class="h-8 w-24 font-mono text-sm"
              />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Add Tier Button -->
    <Button
      variant="outline"
      size="sm"
      class="w-full"
      :disabled="modelValue.length >= 10"
      @click="addTier"
    >
      <Plus class="h-4 w-4 mr-2" />
      Add Tier
    </Button>

    <!-- Calculation Preview -->
    <Card class="p-4 bg-muted/30">
      <div class="text-sm font-medium mb-2">Example: {{ sampleUsage.toLocaleString() }} units</div>
      <div class="space-y-1 text-sm">
        <template v-if="mode === 'graduated'">
          <div
            v-for="(breakdown, index) in graduatedBreakdown"
            :key="index"
            class="flex justify-between text-muted-foreground"
          >
            <span>
              <template v-if="breakdown.flatFee > 0"
                >${{ breakdown.flatFee.toFixed(2) }} flat +
              </template>
              {{ breakdown.units.toLocaleString() }} units
              {{ breakdown.price === 0 ? '(free)' : `× $${formatPrice(breakdown.price)}` }}
            </span>
            <span class="font-mono">${{ breakdown.cost.toFixed(2) }}</span>
          </div>
          <Separator class="my-2" />
          <div class="flex justify-between font-medium">
            <span>Total</span>
            <span class="font-mono">${{ graduatedTotal.toFixed(2) }}</span>
          </div>
        </template>
        <template v-else>
          <div class="flex justify-between text-muted-foreground">
            <span>All {{ sampleUsage.toLocaleString() }} units at tier rate</span>
            <span class="font-mono">${{ formatPrice(volumeTierPrice) }}/unit</span>
          </div>
          <Separator class="my-2" />
          <div class="flex justify-between font-medium">
            <span>Total</span>
            <span class="font-mono">${{ volumeTotal.toFixed(2) }}</span>
          </div>
        </template>
      </div>
    </Card>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { Plus, X } from 'lucide-vue-next'
import type { PricingTier } from '../../types'

const props = withDefaults(
  defineProps<{
    modelValue: PricingTier[]
    mode: 'graduated' | 'volume'
    sampleUsage?: number
  }>(),
  {
    sampleUsage: 150
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: PricingTier[]]
}>()

// Tier colors for visual distinction
const tierColors = [
  'bg-emerald-500/20 text-emerald-700',
  'bg-blue-500/20 text-blue-700',
  'bg-violet-500/20 text-violet-700',
  'bg-amber-500/20 text-amber-700',
  'bg-rose-500/20 text-rose-700',
  'bg-cyan-500/20 text-cyan-700',
  'bg-lime-500/20 text-lime-700',
  'bg-orange-500/20 text-orange-700',
  'bg-pink-500/20 text-pink-700',
  'bg-teal-500/20 text-teal-700'
]

const tierDotColors = [
  'bg-emerald-500',
  'bg-blue-500',
  'bg-violet-500',
  'bg-amber-500',
  'bg-rose-500',
  'bg-cyan-500',
  'bg-lime-500',
  'bg-orange-500',
  'bg-pink-500',
  'bg-teal-500'
]

function getTierBarColor(index: number): string {
  return tierColors[index % tierColors.length]
}

function getTierDotColor(index: number): string {
  return tierDotColors[index % tierDotColors.length]
}

// Calculate visual width for each tier in the bar
function getTierBarStyle(tier: PricingTier, _index: number): Record<string, string> {
  const maxDisplay = getMaxDisplayValue()
  const tierStart = tier.firstUnit
  const tierEnd = tier.lastUnit === null ? maxDisplay : Math.min(tier.lastUnit, maxDisplay)
  const tierWidth = tierEnd - tierStart + 1
  const widthPercent = Math.max((tierWidth / maxDisplay) * 100, 10) // Min 10% width for visibility

  // Last tier (unlimited) gets remaining space
  if (tier.lastUnit === null) {
    return { flex: '1' }
  }

  return { width: `${widthPercent}%`, minWidth: '40px' }
}

function getTierEndPosition(_tier: PricingTier, _index: number): number {
  // This is simplified - could be more accurate for positioning
  return 0
}

function getMaxDisplayValue(): number {
  // Find the last non-unlimited tier's end value, with a buffer
  const lastDefinedTier = props.modelValue.filter((t) => t.lastUnit !== null).pop()
  return lastDefinedTier?.lastUnit ? lastDefinedTier.lastUnit * 1.5 : 1000
}

function getTierDescription(tier: PricingTier, index: number): string {
  if (index === 0 && tier.firstUnit === 1) {
    if (tier.lastUnit === null) {
      return 'All units'
    }
    return `First ${tier.lastUnit.toLocaleString()} units`
  }

  if (tier.lastUnit === null) {
    return `${tier.firstUnit.toLocaleString()}+ units`
  }

  return `${tier.firstUnit.toLocaleString()} - ${tier.lastUnit.toLocaleString()} units`
}

function formatPrice(price: number): string {
  if (price >= 1) return price.toFixed(2)
  if (price >= 0.01) return price.toFixed(3)
  return price.toFixed(4)
}

// Graduated pricing breakdown
const graduatedBreakdown = computed(() => {
  const breakdown: { units: number; price: number; flatFee: number; cost: number }[] = []
  let remaining = props.sampleUsage

  for (const tier of props.modelValue) {
    if (remaining <= 0) break

    const tierMax = tier.lastUnit === null ? Infinity : tier.lastUnit
    const tierMin = tier.firstUnit
    const tierRange = tierMax - tierMin + 1
    const unitsInTier = Math.min(remaining, tierRange)
    const flatFee = tier.flatFee ?? 0

    breakdown.push({
      units: unitsInTier,
      price: tier.unitPrice,
      flatFee,
      cost: flatFee + unitsInTier * tier.unitPrice
    })

    remaining -= unitsInTier
  }

  return breakdown
})

const graduatedTotal = computed(() => {
  return graduatedBreakdown.value.reduce((sum, b) => sum + b.cost, 0)
})

// Volume pricing calculation
const volumeTierPrice = computed(() => {
  for (const tier of props.modelValue) {
    const tierMax = tier.lastUnit === null ? Infinity : tier.lastUnit
    if (props.sampleUsage <= tierMax) {
      return tier.unitPrice
    }
  }
  return props.modelValue[props.modelValue.length - 1]?.unitPrice || 0
})

const volumeTotal = computed(() => {
  return props.sampleUsage * volumeTierPrice.value
})

function updateTierLastUnit(index: number, value: number | string) {
  const numValue = typeof value === 'string' ? parseInt(value, 10) : value
  const newTiers = [...props.modelValue]
  newTiers[index] = { ...newTiers[index], lastUnit: numValue || null }

  // Auto-update next tier's firstUnit if in graduated mode
  if (
    props.mode === 'graduated' &&
    index < newTiers.length - 1 &&
    newTiers[index].lastUnit !== null
  ) {
    newTiers[index + 1] = {
      ...newTiers[index + 1],
      firstUnit: (newTiers[index].lastUnit as number) + 1
    }
  }

  emit('update:modelValue', newTiers)
}

function updateTierPrice(index: number, value: number | string) {
  const numValue = typeof value === 'string' ? parseFloat(value) : value
  const newTiers = [...props.modelValue]
  newTiers[index] = { ...newTiers[index], unitPrice: numValue || 0 }
  emit('update:modelValue', newTiers)
}

function updateTierFlatFee(index: number, value: number | string) {
  const numValue = typeof value === 'string' ? parseFloat(value) : value
  const newTiers = [...props.modelValue]
  newTiers[index] = { ...newTiers[index], flatFee: numValue || 0 }
  emit('update:modelValue', newTiers)
}

function addTier() {
  if (props.modelValue.length >= 10) return

  const newTiers = [...props.modelValue]
  // Get the second-to-last tier's lastUnit (or 0 if none)
  const prevLastUnit = newTiers.length > 1 ? (newTiers[newTiers.length - 2].lastUnit ?? 100) : 0

  // Calculate a reasonable new lastUnit
  const newLastUnit = Math.max(prevLastUnit * 2, prevLastUnit + 100)

  // Insert new tier before the unlimited tier
  const unlimitedTier = newTiers.pop()!
  newTiers.push({
    firstUnit: (newTiers[newTiers.length - 1]?.lastUnit ?? 0) + 1,
    lastUnit: newLastUnit,
    unitPrice: unlimitedTier.unitPrice * 0.8 // Suggest 20% discount
  })

  // Update unlimited tier's firstUnit
  newTiers.push({
    ...unlimitedTier,
    firstUnit: newLastUnit + 1
  })

  emit('update:modelValue', newTiers)
}

function removeTier(index: number) {
  if (props.modelValue.length <= 1) return

  const newTiers = [...props.modelValue]
  newTiers.splice(index, 1)

  // Recalculate firstUnit values for graduated mode
  if (props.mode === 'graduated') {
    for (let i = 1; i < newTiers.length; i++) {
      const prevLastUnit = newTiers[i - 1].lastUnit
      newTiers[i] = {
        ...newTiers[i],
        firstUnit: prevLastUnit !== null ? prevLastUnit + 1 : 1
      }
    }
  }

  emit('update:modelValue', newTiers)
}
</script>
