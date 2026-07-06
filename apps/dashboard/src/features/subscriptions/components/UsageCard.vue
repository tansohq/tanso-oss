<template>
  <component
    :is="inline ? 'div' : Card"
    :class="!inline && 'p-6 mb-6'"
    @click.stop
  >
    <!-- Header (normal mode) -->
    <template v-if="!inline">
      <h3 class="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-4">Current Period Usage</h3>
    </template>

    <!-- Loading -->
    <div v-if="loading" :class="['flex items-center gap-2', inline ? 'py-2' : 'py-4']">
      <Loader2 :class="['animate-spin text-muted-foreground', inline ? 'h-3.5 w-3.5' : 'h-4 w-4']" />
      <span :class="['text-muted-foreground', inline ? 'text-xs' : 'text-sm']">Loading usage…</span>
    </div>

    <!-- Error -->
    <div v-else-if="error" :class="['text-destructive', inline ? 'text-xs py-2' : 'text-sm']">
      Unable to load usage data.
    </div>

    <!-- Empty -->
    <p v-else-if="groups.length === 0" :class="['text-muted-foreground', inline ? 'text-xs py-2' : 'text-sm']">
      No usage recorded this period.
    </p>

    <!-- Usage data -->
    <template v-else>
      <!-- Inline compact summary -->
      <template v-if="inline">
        <div class="border-t pt-3 mt-1 space-y-1">
          <div v-for="g in groupsWithRules" :key="g.featureId" class="flex items-center justify-between text-xs">
            <span class="text-muted-foreground">
              {{ g.featureName }}
              <span v-if="g.included" class="text-muted-foreground/60">· Included</span>
              <span v-else-if="g.maxUsage !== null" class="text-muted-foreground/60">· {{ formatUnits(g.usageUnits) }} / {{ formatUnits(g.maxUsage) }} units</span>
              <span v-else class="text-muted-foreground/60">· {{ formatUnits(g.usageUnits) }} {{ g.usageUnits === 1 ? 'unit' : 'units' }}</span>
            </span>
            <span v-if="!g.included" class="tabular-nums">{{ formatAmount(g.revenue) }}</span>
          </div>
          <div v-if="basePrice !== null && basePrice !== undefined" class="text-xs text-muted-foreground pt-2">
            Estimated total: {{ formatAmount(basePrice) }} base + {{ formatAmount(totalRevenue) }} usage
            = <span class="font-medium text-foreground">{{ formatAmount(basePrice + totalRevenue) }}</span>
          </div>
        </div>
      </template>

      <!-- Full table (non-inline) -->
      <template v-else>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Feature</TableHead>
              <TableHead class="text-right">Units</TableHead>
              <TableHead class="text-right">Rate</TableHead>
              <TableHead class="text-right">Revenue</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <template v-for="g in groupsWithRules" :key="g.featureId">
              <!-- Parent row -->
              <TableRow :class="{ 'border-b-0': g.tierBreakdown.length > 0 || (g.maxUsage !== null && !g.included && g.usageUnits > g.maxUsage) }">
                <TableCell>
                  <div class="flex flex-col">
                    <span class="font-medium inline-flex items-center gap-1.5">
                      {{ g.featureName }}
                      <TooltipProvider v-if="g.resetMode === 'accumulate'">
                        <Tooltip>
                          <TooltipTrigger as-child>
                            <Badge variant="secondary" class="text-[10px] px-1.5 py-0">Cumulative</Badge>
                          </TooltipTrigger>
                          <TooltipContent>
                            <p>Pricing tiers are based on cumulative usage total</p>
                          </TooltipContent>
                        </Tooltip>
                      </TooltipProvider>
                    </span>
                    <span class="text-xs text-muted-foreground">
                      <template v-if="g.included">
                        <TooltipProvider>
                          <Tooltip>
                            <TooltipTrigger as-child>
                              <span>{{ g.pricingBadge }}</span>
                            </TooltipTrigger>
                            <TooltipContent>
                              <p>Included in the base price — no per-unit charge</p>
                            </TooltipContent>
                          </Tooltip>
                        </TooltipProvider>
                      </template>
                      <template v-else>{{ g.pricingBadge }}</template>
                    </span>
                  </div>
                </TableCell>
                <TableCell class="text-right tabular-nums">
                  <template v-if="!g.included">
                    <div v-if="g.maxUsage !== null">
                      <span :class="usagePercent(g.usageUnits, g.maxUsage) > 90 ? 'text-destructive' : ''">
                        {{ formatUnits(g.usageUnits) }} / {{ formatUnits(g.maxUsage) }}
                      </span>
                      <div v-if="g.usageUnits > 0" class="mt-1 h-1.5 w-full rounded-full bg-muted overflow-hidden">
                        <div
                          :class="['h-full rounded-full transition-all', usageBarColor(usagePercent(g.usageUnits, g.maxUsage))]"
                          :style="{ width: `${Math.min(usagePercent(g.usageUnits, g.maxUsage), 100)}%` }"
                        />
                      </div>
                    </div>
                    <template v-else>{{ formatUnits(g.usageUnits) }}</template>
                  </template>
                </TableCell>
                <TableCell class="text-right tabular-nums">
                  <template v-if="g.included" />
                  <template v-else-if="g.rateDisplay">{{ g.rateDisplay }}</template>
                  <template v-else>—</template>
                </TableCell>
                <TableCell class="text-right tabular-nums">
                  <template v-if="!g.included">{{ formatAmount(g.revenue) }}</template>
                </TableCell>
              </TableRow>
              <!-- Tier sub-rows -->
              <TableRow
                v-for="(tier, ti) in g.tierBreakdown"
                :key="`${g.featureId}-tier-${ti}`"
                :class="ti < g.tierBreakdown.length - 1 ? 'border-b-0' : ''"
              >
                <TableCell class="pl-6 text-xs text-muted-foreground py-1 tabular-nums">{{ tier.label }}</TableCell>
                <TableCell class="text-right text-xs text-muted-foreground py-1 tabular-nums">{{ formatUnits(tier.units) }}</TableCell>
                <TableCell class="text-right text-xs text-muted-foreground py-1 tabular-nums">{{ formatUnitPrice(tier.rate) }}/unit</TableCell>
                <TableCell class="text-right text-xs text-muted-foreground py-1 tabular-nums">{{ formatAmount(tier.amount) }}</TableCell>
              </TableRow>
              <!-- Capped usage breakdown (only when over limit) -->
              <TableRow v-if="g.maxUsage !== null && !g.included && g.usageUnits > g.maxUsage" :key="`${g.featureId}-cap`">
                <TableCell class="pl-6 text-xs text-muted-foreground py-1">
                  Capped at {{ formatUnits(g.maxUsage) }}
                  <span v-if="g.usageUnits > g.maxUsage" class="text-destructive">
                    · {{ formatUnits(g.usageUnits - g.maxUsage) }} over limit
                  </span>
                </TableCell>
                <TableCell class="text-right text-xs text-muted-foreground py-1 tabular-nums">
                  {{ formatUnits(Math.min(g.usageUnits, g.maxUsage)) }}
                </TableCell>
                <TableCell class="text-right text-xs text-muted-foreground py-1 tabular-nums">
                  <template v-if="g.pricePerUnit !== null">{{ formatUnitPrice(g.pricePerUnit) }}/unit</template>
                </TableCell>
                <TableCell class="text-right text-xs text-muted-foreground py-1 tabular-nums">
                  <template v-if="g.pricePerUnit !== null">{{ formatAmount(Math.min(g.usageUnits, g.maxUsage) * g.pricePerUnit) }}</template>
                  <template v-else>{{ formatAmount(g.revenue) }}</template>
                </TableCell>
              </TableRow>
            </template>
            <!-- Total row -->
            <TableRow class="border-t-2">
              <TableCell class="font-semibold">Total</TableCell>
              <TableCell class="text-right tabular-nums font-semibold">{{ formatUnits(totalUnits) }}</TableCell>
              <TableCell></TableCell>
              <TableCell class="text-right tabular-nums font-semibold">{{ formatAmount(totalRevenue) }}</TableCell>
            </TableRow>
          </TableBody>
        </Table>

        <!-- Estimated total footer -->
        <template v-if="basePrice !== null && basePrice !== undefined">
          <div class="border-t pt-3 mt-3">
            <div class="flex justify-between text-sm">
              <span class="text-muted-foreground">Estimated total</span>
              <span>
                <span class="text-muted-foreground">{{ formatAmount(basePrice) }} base + {{ formatAmount(totalRevenue) }} usage =</span>
                <span class="font-semibold tabular-nums ml-1">{{ formatAmount(basePrice + totalRevenue) }}</span>
              </span>
            </div>
          </div>
        </template>
      </template>
    </template>

    <!-- Footer (normal mode only) -->
    <p v-if="!inline" class="text-xs text-muted-foreground mt-4">
      Usage reflects customer activity during this subscription's billing period.
    </p>
  </component>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger
} from '@/components/ui/tooltip'
import { Loader2 } from 'lucide-vue-next'
import { formatAmount, formatUnitPrice, formatUsageNumber } from '@/lib/formatters'
import { useSubscriptionUsage } from '../composables/useSubscriptionUsage'
import { getPlanFeatureRule } from '@/features/plan-features/api'

const props = defineProps<{
  subscriptionId: string
  planId: string
  periodStart: string
  periodEnd: string
  basePrice?: number | null
  intervalMonths?: string | null
  inline?: boolean
}>()

const subscriptionIdRef = computed(() => props.subscriptionId)
const planIdRef = computed(() => props.planId)
const periodStartRef = computed(() => props.periodStart)
const periodEndRef = computed(() => props.periodEnd)

const {
  groups,
  totalUnits,
  totalRevenue,
  loading,
  error
} = useSubscriptionUsage(subscriptionIdRef, planIdRef, periodStartRef, periodEndRef)

// Fetch feature pricing rules for rate display
interface GraduatedTier {
  up_to: number | 'inf'
  price_per_unit: number
  flat_fee?: number
}

interface FeatureRuleData {
  featureId: string
  model: string | null
  pricePerUnit: number | null
  tierCount: number
  tiers: GraduatedTier[] | null
  maxUsage: number | null
}

const featureRulesMap = ref<Map<string, FeatureRuleData>>(new Map())
let rulesFetchId = 0

watch(
  groups,
  async (currentGroups) => {
    if (!currentGroups || currentGroups.length === 0 || !props.planId) return

    const fetchId = ++rulesFetchId
    const newMap = new Map<string, FeatureRuleData>()

    const promises = currentGroups.map(async (g) => {
      try {
        const response = await getPlanFeatureRule(props.planId, g.featureId)
        const value = response?.data?.value as Record<string, unknown> | undefined
        const model = (value?.model as string) ?? g.model
        const tiers = Array.isArray(value?.tiers) ? (value.tiers as GraduatedTier[]) : null
        newMap.set(g.featureId, {
          featureId: g.featureId,
          model: model ?? null,
          pricePerUnit: (value?.price_per_unit as number) ?? null,
          tierCount: tiers ? tiers.length : 0,
          tiers,
          maxUsage: typeof value?.max_usage === 'number' ? value.max_usage : null
        })
      } catch (error) {
        // A 404 means no explicit rule; fall back to the model from usage data.
        // Any other failure is a real error and must be logged, not swallowed.
        const status = (error as { response?: { status?: number } })?.response?.status
        if (status !== 404) {
          console.error(`Failed to fetch pricing rule for plan ${props.planId} feature ${g.featureId}:`, error)
        }
        newMap.set(g.featureId, {
          featureId: g.featureId,
          model: g.model,
          pricePerUnit: null,
          tierCount: 0,
          tiers: null,
          maxUsage: null
        })
      }
    })

    await Promise.all(promises)
    if (fetchId === rulesFetchId) {
      featureRulesMap.value = newMap
    }
  },
  { immediate: true }
)

interface TierBreakdownLine {
  label: string
  units: number
  rate: number
  amount: number
}

function computeTierBreakdown(units: number, tiers: GraduatedTier[]): TierBreakdownLine[] {
  const lines: TierBreakdownLine[] = []
  let remaining = units
  let prevUpTo = 0
  const fmt = (n: number) => new Intl.NumberFormat('en-US').format(n)

  for (const tier of tiers) {
    if (remaining <= 0) break
    const tierUpTo = tier.up_to === 'inf' ? Infinity : tier.up_to
    const tierSize = tierUpTo - prevUpTo
    const unitsInTier = Math.min(remaining, tierSize)
    const cost = unitsInTier * tier.price_per_unit + (tier.flat_fee ?? 0)

    let label: string
    if (prevUpTo === 0 && tierUpTo !== Infinity) {
      label = `First ${fmt(tierUpTo)}`
    } else if (tierUpTo === Infinity) {
      label = `${fmt(prevUpTo + 1)}+`
    } else {
      label = `${fmt(prevUpTo + 1)}–${fmt(tierUpTo)}`
    }

    lines.push({ label, units: unitsInTier, rate: tier.price_per_unit, amount: cost })

    remaining -= unitsInTier
    prevUpTo = tierUpTo === Infinity ? prevUpTo : tierUpTo
  }

  return lines
}

const groupsWithRules = computed(() => {
  return groups.value.map(g => {
    const rule = featureRulesMap.value.get(g.featureId)
    let rateDisplay: string | null = null
    let pricingBadge = 'Included'
    let tierBreakdown: TierBreakdownLine[] = []

    if (rule) {
      if (rule.model === 'usage' && rule.pricePerUnit !== null) {
        rateDisplay = `${formatUnitPrice(rule.pricePerUnit)}/unit`
        pricingBadge = `Per Unit (${formatUnitPrice(rule.pricePerUnit)})`
      } else if (rule.model === 'graduated') {
        pricingBadge = `Graduated (${rule.tierCount} tiers)`
        if (rule.tiers) {
          tierBreakdown = computeTierBreakdown(g.usageUnits, rule.tiers)
        } else {
          rateDisplay = 'Graduated'
        }
      } else if (rule.model === 'usage') {
        pricingBadge = 'Per Unit'
      }
    }

    const included = pricingBadge === 'Included'
    const maxUsage = rule?.maxUsage ?? null
    const pricePerUnit = rule?.pricePerUnit ?? null
    return { ...g, rateDisplay, pricingBadge, tierBreakdown, included, maxUsage, pricePerUnit }
  })
})

function usagePercent(used: number, max: number): number {
  if (max <= 0) return 0
  return (used / max) * 100
}

function usageBarColor(percent: number): string {
  if (percent > 90) return 'bg-destructive'
  if (percent >= 75) return 'bg-amber-500'
  return 'bg-emerald-500'
}

function formatUnits(units: number): string {
  return formatUsageNumber(units)
}

</script>
