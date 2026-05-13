<template>
  <div class="space-y-2">
    <div class="grid grid-cols-[1fr,1fr,1fr,32px] gap-3 text-xs text-muted-foreground">
      <div>Up to</div>
      <div>Per unit</div>
      <div>Flat fee</div>
      <div></div>
    </div>

    <div
      v-for="(tier, index) in modelValue"
      :key="index"
      class="grid grid-cols-[1fr,1fr,1fr,32px] gap-3 items-center"
    >
      <!-- Up to -->
      <div v-if="tier.up_to === 'inf'" class="h-9 flex items-center text-sm text-muted-foreground px-3 border rounded-md bg-muted/30">
        &#8734;
      </div>
      <Input
        v-else
        :model-value="tier.up_to"
        type="number"
        min="1"
        step="1"
        placeholder="1000"
        class="h-9"
        @update:model-value="updateTier(index, 'up_to', Number($event))"
      />

      <!-- Price per unit -->
      <div class="relative">
        <span class="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">$</span>
        <Input
          :model-value="tier.price_per_unit"
          type="number"
          step="0.0001"
          min="0"
          placeholder="0.001"
          class="h-9 pl-7"
          @update:model-value="updateTier(index, 'price_per_unit', Number($event))"
        />
      </div>

      <!-- Flat fee -->
      <div class="relative">
        <span class="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">$</span>
        <Input
          :model-value="tier.flat_fee ?? 0"
          type="number"
          step="0.01"
          min="0"
          placeholder="0.00"
          class="h-9 pl-7"
          @update:model-value="updateTier(index, 'flat_fee', Number($event))"
        />
      </div>

      <!-- Remove button -->
      <Button
        v-if="modelValue.length > 1 && tier.up_to !== 'inf'"
        variant="ghost"
        size="sm"
        class="h-8 w-8 p-0"
        @click="removeTier(index)"
      >
        <X class="h-3.5 w-3.5" />
      </Button>
      <div v-else></div>
    </div>

    <!-- Warning if tiers not in ascending order -->
    <p v-if="hasOrderWarning" class="text-xs text-yellow-600">
      Tiers should be in ascending order by "up to" value.
    </p>

    <Button
      variant="outline"
      size="sm"
      class="text-xs"
      @click="addTier"
    >
      <Plus class="h-3 w-3 mr-1" />
      Add Tier
    </Button>

    <!-- Live Calculation Preview -->
    <div v-if="showPreview" class="bg-muted/50 rounded-lg p-3 space-y-1.5">
      <div class="text-xs font-medium text-muted-foreground">
        Example: {{ sampleUsage.toLocaleString() }} {{ unitLabel || 'units' }}
      </div>
      <div
        v-for="(line, i) in previewLines"
        :key="i"
        class="text-xs text-muted-foreground tabular-nums"
      >
        {{ line }}
      </div>
      <Separator class="my-1.5" />
      <div class="text-xs font-semibold tabular-nums">
        Total: ${{ previewTotal }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Separator } from '@/components/ui/separator'
import { Plus, X } from 'lucide-vue-next'
import type { GraduatedTier } from '../types'

const props = defineProps<{
  modelValue: GraduatedTier[]
  unitLabel?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: GraduatedTier[]]
}>()

const hasOrderWarning = computed(() => {
  const numericTiers = props.modelValue.filter((t) => t.up_to !== 'inf')
  for (let i = 1; i < numericTiers.length; i++) {
    if ((numericTiers[i].up_to as number) <= (numericTiers[i - 1].up_to as number)) {
      return true
    }
  }
  return false
})

const showPreview = computed(() => {
  return props.modelValue.some((t) => t.price_per_unit > 0)
})

const sampleUsage = computed(() => {
  const firstTier = props.modelValue.find((t) => t.up_to !== 'inf')
  if (firstTier && typeof firstTier.up_to === 'number' && firstTier.up_to > 0) {
    return Math.round(firstTier.up_to * 1.5)
  }
  return 150
})

const previewLines = computed<string[]>(() => {
  const lines: string[] = []
  let remaining = sampleUsage.value
  let prevUpTo = 0

  for (const tier of props.modelValue) {
    if (remaining <= 0) break

    const tierUpTo = tier.up_to === 'inf' ? Infinity : tier.up_to
    const tierSize = tierUpTo - prevUpTo
    const unitsInTier = Math.min(remaining, tierSize)

    if (tier.price_per_unit === 0) {
      lines.push(`${unitsInTier.toLocaleString()} ${props.unitLabel || 'units'} (free)`)
    } else {
      const cost = unitsInTier * tier.price_per_unit
      lines.push(
        `${unitsInTier.toLocaleString()} × $${tier.price_per_unit} = $${cost.toFixed(2)}`
      )
    }

    remaining -= unitsInTier
    prevUpTo = tierUpTo === Infinity ? prevUpTo : tierUpTo
  }

  return lines
})

const previewTotal = computed(() => {
  let remaining = sampleUsage.value
  let total = 0
  let prevUpTo = 0

  for (const tier of props.modelValue) {
    if (remaining <= 0) break

    const tierUpTo = tier.up_to === 'inf' ? Infinity : tier.up_to
    const tierSize = tierUpTo - prevUpTo
    const unitsInTier = Math.min(remaining, tierSize)

    total += unitsInTier * tier.price_per_unit + (tier.flat_fee ?? 0)
    remaining -= unitsInTier
    prevUpTo = tierUpTo === Infinity ? prevUpTo : tierUpTo
  }

  return total.toFixed(2)
})

function updateTier(index: number, field: 'up_to' | 'price_per_unit' | 'flat_fee', value: number) {
  const updated = [...props.modelValue]
  updated[index] = { ...updated[index], [field]: value }
  emit('update:modelValue', updated)
}

function removeTier(index: number) {
  const updated = props.modelValue.filter((_, i) => i !== index)
  emit('update:modelValue', updated)
}

function addTier() {
  const updated = [...props.modelValue]
  // Insert before the last (inf) tier
  const infIndex = updated.findIndex((t) => t.up_to === 'inf')
  const lastNumeric = updated.filter((t) => t.up_to !== 'inf').pop()
  const newUpTo = lastNumeric ? (lastNumeric.up_to as number) * 10 : 1000

  const newTier: GraduatedTier = { up_to: newUpTo, price_per_unit: 0, flat_fee: 0 }

  if (infIndex >= 0) {
    updated.splice(infIndex, 0, newTier)
  } else {
    updated.push(newTier)
  }

  emit('update:modelValue', updated)
}
</script>
