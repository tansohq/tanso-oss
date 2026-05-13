<template>
  <div v-if="calculationText" class="mt-3 p-3 rounded-md bg-blue-50 border border-blue-100">
    <div class="flex items-start gap-2">
      <Calculator class="h-4 w-4 text-blue-500 mt-0.5 flex-shrink-0" />
      <div class="text-sm">
        <p class="font-medium text-blue-700 mb-1">
          Example Calculation ({{ sampleUsage.toLocaleString() }} units)
        </p>
        <p class="text-blue-600 font-mono text-xs whitespace-pre-wrap">{{ calculationText }}</p>
        <p class="text-blue-700 font-semibold mt-1">Total: ${{ totalAmount.toFixed(2) }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Calculator } from 'lucide-vue-next'
import type { PricingTier, PriceModel } from '../../types'

const props = withDefaults(
  defineProps<{
    priceModel: PriceModel | 'graduated' | 'volume'
    tiers?: PricingTier[]
    unitPrice?: number
    packageSize?: number
    packagePrice?: number
    sampleUsage?: number
  }>(),
  {
    sampleUsage: 150
  }
)

const calculationText = computed(() => {
  const usage = props.sampleUsage

  switch (props.priceModel) {
    case 'per_unit':
      if (!props.unitPrice) return null
      return `${usage.toLocaleString()} units × $${props.unitPrice.toFixed(4)}`

    case 'graduated':
      return calculateGraduatedText(usage)

    case 'volume':
      return calculateVolumeText(usage)

    case 'package': {
      if (!props.packageSize || !props.packagePrice) return null
      const packages = Math.ceil(usage / props.packageSize)
      return `${usage.toLocaleString()} units ÷ ${props.packageSize} = ${packages} packages\n${packages} packages × $${props.packagePrice.toFixed(2)}`
    }

    default:
      return null
  }
})

const totalAmount = computed(() => {
  const usage = props.sampleUsage

  switch (props.priceModel) {
    case 'per_unit':
      return (props.unitPrice ?? 0) * usage

    case 'graduated':
      return calculateGraduatedTotal(usage)

    case 'volume':
      return calculateVolumeTotal(usage)

    case 'package':
      if (!props.packageSize || !props.packagePrice) return 0
      return Math.ceil(usage / props.packageSize) * props.packagePrice

    default:
      return 0
  }
})

function calculateGraduatedText(usage: number): string | null {
  if (!props.tiers || props.tiers.length === 0) return null

  let remaining = usage
  const parts: string[] = []

  for (const tier of props.tiers) {
    if (remaining <= 0) break

    const tierStart = tier.firstUnit
    const tierEnd = tier.lastUnit ?? Infinity
    const tierRange = tierEnd - tierStart + 1
    const unitsInTier = Math.min(remaining, tierRange)

    if (unitsInTier > 0) {
      const tierCost = unitsInTier * tier.unitPrice
      parts.push(
        `${unitsInTier.toLocaleString()} units × $${tier.unitPrice.toFixed(4)} = $${tierCost.toFixed(2)}`
      )
      remaining -= unitsInTier
    }
  }

  return parts.join('\n')
}

function calculateGraduatedTotal(usage: number): number {
  if (!props.tiers || props.tiers.length === 0) return 0

  let remaining = usage
  let total = 0

  for (const tier of props.tiers) {
    if (remaining <= 0) break

    const tierStart = tier.firstUnit
    const tierEnd = tier.lastUnit ?? Infinity
    const tierRange = tierEnd - tierStart + 1
    const unitsInTier = Math.min(remaining, tierRange)

    if (unitsInTier > 0) {
      total += unitsInTier * tier.unitPrice
      remaining -= unitsInTier
    }
  }

  return total
}

function calculateVolumeText(usage: number): string | null {
  if (!props.tiers || props.tiers.length === 0) return null

  // Find the tier that matches our usage
  for (const tier of props.tiers) {
    const tierEnd = tier.lastUnit ?? Infinity
    if (usage <= tierEnd) {
      return `All ${usage.toLocaleString()} units at tier rate: $${tier.unitPrice.toFixed(4)}/unit\n${usage.toLocaleString()} × $${tier.unitPrice.toFixed(4)}`
    }
  }

  // Use last tier if we exceed all
  const lastTier = props.tiers[props.tiers.length - 1]
  return `All ${usage.toLocaleString()} units at tier rate: $${lastTier.unitPrice.toFixed(4)}/unit\n${usage.toLocaleString()} × $${lastTier.unitPrice.toFixed(4)}`
}

function calculateVolumeTotal(usage: number): number {
  if (!props.tiers || props.tiers.length === 0) return 0

  // Find the tier that matches our usage
  for (const tier of props.tiers) {
    const tierEnd = tier.lastUnit ?? Infinity
    if (usage <= tierEnd) {
      return usage * tier.unitPrice
    }
  }

  // Use last tier if we exceed all
  const lastTier = props.tiers[props.tiers.length - 1]
  return usage * lastTier.unitPrice
}
</script>
