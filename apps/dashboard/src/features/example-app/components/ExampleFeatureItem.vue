<template>
  <!-- Usage-based feature -->
  <li v-if="pricingType === 'usage_based'" class="flex items-start gap-3">
    <component
      :is="isPurePrepaid ? Check : BarChart3"
      :class="isPurePrepaid ? 'w-5 h-5 text-primary shrink-0 mt-0.5' : 'w-5 h-5 text-blue-500 shrink-0 mt-0.5'"
    />
    <div class="flex flex-col">
      <span class="font-medium">{{ name }}</span>
      <span v-if="usageSubLine" class="text-sm text-muted-foreground mt-0.5">
        {{ usageSubLine }}
      </span>
    </div>
  </li>

  <!-- Graduated feature (simple prepaid) -->
  <li v-else-if="pricingType === 'graduated' && prepaidInfo" class="flex items-start gap-3">
    <component
      :is="prepaidInfo.overagePrice === 0 ? Check : BarChart3"
      :class="prepaidInfo.overagePrice === 0 ? 'w-5 h-5 text-primary shrink-0 mt-0.5' : 'w-5 h-5 text-blue-500 shrink-0 mt-0.5'"
    />
    <div class="flex flex-col">
      <span class="font-medium">{{ name }}</span>
      <span v-if="prepaidSubLine" class="text-sm text-muted-foreground mt-0.5">
        {{ prepaidSubLine }}
      </span>
    </div>
  </li>

  <!-- Graduated feature (complex tiers) -->
  <li v-else-if="pricingType === 'graduated' && tiers && tiers.length > 0" class="flex items-start gap-3">
    <Layers class="w-5 h-5 text-purple-500 shrink-0 mt-0.5" />
    <div class="flex flex-col">
      <span class="font-medium">{{ name }}</span>
      <TooltipProvider v-if="tieredSummary">
        <Tooltip>
          <TooltipTrigger as-child>
            <span class="text-sm text-muted-foreground border-b border-dotted border-muted-foreground/50 cursor-help mt-0.5">
              {{ tieredSummary }}
            </span>
          </TooltipTrigger>
          <TooltipContent class="max-w-xs">
            <table class="text-xs w-full">
              <thead>
                <tr>
                  <th class="text-left pr-3">Tier</th>
                  <th class="text-right">Price</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(tier, i) in tiers" :key="i">
                  <td class="pr-3">
                    {{ tier.up_to === 'inf' ? 'Unlimited' : `Up to ${Number(tier.up_to).toLocaleString()} ${unitLabel || 'units'}` }}
                  </td>
                  <td class="text-right">
                    {{ tier.price_per_unit === 0 ? 'Free' : formatUsagePrice(tier.price_per_unit, unitLabel, null) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </TooltipContent>
        </Tooltip>
      </TooltipProvider>
    </div>
  </li>

  <!-- Included feature (default) -->
  <li v-else class="flex items-start gap-3">
    <Check class="w-5 h-5 text-primary shrink-0 mt-0.5" />
    <span>{{ name }}</span>
  </li>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Check, BarChart3, Layers } from 'lucide-vue-next'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger
} from '@/components/ui/tooltip'
import {
  formatUsagePrice,
  isSimplePrepaidGraduated,
  formatTieredSummary
} from '../lib/examplePricing'

const props = defineProps<{
  name: string
  pricingType: 'included' | 'usage_based' | 'graduated'
  unitPrice?: number
  unitLabel?: string
  maxUsage?: number | null
  tiers?: Array<{ up_to: number | 'inf'; price_per_unit: number; flat_fee?: number }>
  currency?: string
}>()

const currency = computed(() => props.currency || 'USD')

const isPurePrepaid = computed(() => props.unitPrice === 0)

const usageSubLine = computed(() =>
  formatUsagePrice(props.unitPrice, props.unitLabel, props.maxUsage, currency.value)
)

const prepaidInfo = computed(() => isSimplePrepaidGraduated(props.tiers))

const prepaidSubLine = computed(() => {
  if (!prepaidInfo.value) return ''
  return formatUsagePrice(
    prepaidInfo.value.overagePrice,
    props.unitLabel,
    prepaidInfo.value.includedAmount,
    currency.value
  )
})

const tieredSummary = computed(() =>
  formatTieredSummary(props.tiers, props.unitLabel, currency.value)
)
</script>
