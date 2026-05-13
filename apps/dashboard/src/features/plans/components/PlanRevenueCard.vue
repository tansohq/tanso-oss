<template>
  <Card class="p-6 mb-6">
    <div class="flex items-center justify-between mb-4">
      <div v-if="isLoading">
        <Skeleton class="h-4 w-28 mb-2" />
        <Skeleton class="h-8 w-20 mb-2" />
        <Skeleton class="h-4 w-36" />
      </div>
      <div v-else>
        <div class="text-sm font-medium text-muted-foreground">Plan Revenue</div>
        <div class="text-4xl font-semibold tabular-nums">{{ formatAmount(totalRevenue) }}</div>
        <div class="text-sm text-muted-foreground mt-1">{{ getPresetLabel(selectedPeriod) }}</div>
      </div>
      <div v-if="isLoading">
        <Skeleton class="h-7 w-28" />
      </div>
      <div v-else class="flex gap-1">
        <Button
          v-for="p in periodOptions"
          :key="p"
          :variant="selectedPeriod === p ? 'default' : 'outline'"
          size="sm"
          class="h-7 px-2.5 text-xs"
          @click="selectedPeriod = p"
        >
          {{ periodButtonLabels[p] }}
        </Button>
      </div>
    </div>

    <!-- Chart -->
    <div class="h-40 flex gap-1 relative" role="img" :aria-label="`Revenue by plan - ${getPresetLabel(selectedPeriod)}`">
      <template v-if="isLoading">
        <div
          v-for="i in monthCount"
          :key="i"
          class="flex-1 rounded-t animate-pulse bg-gray-200 self-end"
          :style="{ height: '50%' }"
        />
      </template>
      <template v-else-if="hasData">
        <template v-for="(month, i) in chartData" :key="i">
          <Tooltip v-if="month.total > 0">
            <TooltipTrigger as-child>
              <div class="flex-1 h-full flex flex-col justify-end gap-px cursor-default">
                <div
                  v-for="(segment, j) in month.segments"
                  :key="j"
                  :class="[j === 0 ? 'rounded-t' : '', j === month.segments.length - 1 ? 'rounded-b' : '', planColors[j % planColors.length]]"
                  :style="{ height: segment.height }"
                />
              </div>
            </TooltipTrigger>
            <TooltipContent>
              <div class="text-xs">
                <p class="font-medium mb-1">{{ monthLabels[i] }}</p>
                <div v-for="(segment, j) in month.segments" :key="j" class="flex justify-between gap-4 tabular-nums">
                  <span>{{ planNames[j] }}</span>
                  <span>{{ formatAmount(segment.amount) }}</span>
                </div>
                <div v-if="month.segments.length > 1" class="flex justify-between gap-4 tabular-nums border-t border-border mt-1 pt-1 font-medium">
                  <span>Total</span>
                  <span>{{ formatAmount(month.total) }}</span>
                </div>
              </div>
            </TooltipContent>
          </Tooltip>
          <div v-else class="flex-1" />
        </template>
      </template>
      <template v-else>
        <div
          v-for="i in monthCount"
          :key="i"
          class="flex-1 rounded bg-muted self-end"
          style="height: 4px"
        />
      </template>
    </div>

    <!-- Month labels -->
    <div class="flex justify-between text-[10px] text-muted-foreground mt-1.5">
      <template v-if="isLoading">
        <Skeleton v-for="i in monthCount" :key="i" class="h-3 w-5" />
      </template>
      <template v-else>
        <span v-for="label in monthLabels" :key="label">{{ label }}</span>
      </template>
    </div>

    <!-- Legend -->
    <div v-if="!isLoading && planNames.length > 0" class="flex flex-wrap items-center gap-4 mt-4 text-sm">
      <span v-for="(name, i) in planNames" :key="name" class="flex items-center gap-2">
        <span class="w-3 h-3 rounded" :class="planColors[i % planColors.length]" />
        {{ name }}
      </span>
    </div>
  </Card>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { getPresetLabel, type RevenuePeriodPreset } from '@/shared/utils/datePresets'
import { formatAmount } from '@/lib/formatters'

interface Invoice {
  amount: number
  createdAt: string
  subscription: {
    plan: {
      name: string
      [key: string]: unknown
    }
    [key: string]: unknown
  }
  [key: string]: unknown
}

const props = defineProps<{
  isLoading: boolean
  invoices?: Invoice[]
}>()

const periodOptions: RevenuePeriodPreset[] = ['3m', '6m', '12m']
const periodButtonLabels: Record<RevenuePeriodPreset, string> = {
  '3m': '3M',
  '6m': '6M',
  '12m': '12M'
}

const selectedPeriod = ref<RevenuePeriodPreset>('3m')

const monthCount = computed(() => {
  switch (selectedPeriod.value) {
    case '3m': return 3
    case '6m': return 6
    case '12m': return 12
  }
})

const planColors = [
  'bg-gray-300',
  'bg-gray-500',
  'bg-gray-700',
]

const monthLabels = computed(() => {
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  const now = new Date()
  const count = monthCount.value
  const labels: string[] = []
  for (let i = count - 1; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    labels.push(months[d.getMonth()])
  }
  return labels
})

// Group invoices by month and plan
const revenueByMonth = computed(() => {
  const invoices = props.invoices ?? []
  const now = new Date()
  const count = monthCount.value

  const bucketKeys: string[] = []
  for (let i = count - 1; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    bucketKeys.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`)
  }

  const planTotals = new Map<string, Map<string, number>>()

  for (const inv of invoices) {
    const planName = inv.subscription?.plan?.name ?? 'Unknown'
    const date = new Date(inv.createdAt)
    const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`

    if (!bucketKeys.includes(key)) continue

    if (!planTotals.has(planName)) {
      planTotals.set(planName, new Map())
    }
    const monthMap = planTotals.get(planName)!
    monthMap.set(key, (monthMap.get(key) ?? 0) + inv.amount)
  }

  return { bucketKeys, planTotals }
})

const planNames = computed(() => {
  return Array.from(revenueByMonth.value.planTotals.keys()).sort()
})

const hasData = computed(() => planNames.value.length > 0)

const totalRevenue = computed(() => {
  const invoices = props.invoices ?? []
  const { bucketKeys } = revenueByMonth.value
  return invoices
    .filter((inv) => {
      const date = new Date(inv.createdAt)
      const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
      return bucketKeys.includes(key)
    })
    .reduce((sum, inv) => sum + (inv.amount ?? 0), 0)
})

const chartData = computed(() => {
  const { bucketKeys, planTotals } = revenueByMonth.value
  const names = planNames.value

  let maxTotal = 0
  for (const key of bucketKeys) {
    let total = 0
    for (const name of names) {
      total += planTotals.get(name)?.get(key) ?? 0
    }
    maxTotal = Math.max(maxTotal, total)
  }
  if (maxTotal === 0) maxTotal = 1

  return bucketKeys.map((key) => {
    let total = 0
    const segments = names.map((name) => {
      const amount = planTotals.get(name)?.get(key) ?? 0
      total += amount
      const pct = (amount / maxTotal) * 100
      return { height: pct > 0 ? `${Math.max(pct, 2)}%` : '0px', amount }
    })
    return { segments, total }
  })
})
</script>
