<template>
  <Card
    class="flex flex-col transition-all cursor-pointer hover:bg-muted/50"
    @click="emit('click')"
  >
    <CardHeader class="pb-2">
      <div>
        <div class="flex items-center gap-2">
          <CardTitle class="text-lg truncate">{{ plan.name }}</CardTitle>
          <Badge
            v-if="planStatus === 'draft'"
            class="bg-amber-50 text-amber-700 border border-amber-200/50 shadow-none text-xs shrink-0"
          >
            Draft
          </Badge>
          <Badge
            v-else-if="planStatus === 'archived'"
            class="bg-gray-50 text-gray-600 border border-gray-200/50 shadow-none text-xs shrink-0"
          >
            Archived
          </Badge>
        </div>
        <div class="mt-1 flex items-baseline gap-2">
          <span class="text-2xl font-bold">{{ formatPrice(plan.priceAmount) }}</span>
          <span class="text-muted-foreground">/ {{ formatIntervalShort(plan.intervalMonths) }}</span>
        </div>
        <div v-if="hasUsagePricing" class="text-xs text-muted-foreground/60 italic mt-0.5">+ usage-based</div>
      </div>
    </CardHeader>
    <CardContent class="mt-auto">

      <div class="pt-3 border-t">
        <template v-if="isLoadingFeatures">
          <Skeleton class="h-5 w-16 rounded-full" />
        </template>
        <TooltipProvider v-else :delay-duration="0">
          <Tooltip v-if="features.length > 0">
            <TooltipTrigger as-child>
              <Badge class="text-xs bg-gray-100/80 text-gray-900 border-0 shadow-none cursor-default">
                {{ features.length }} {{ features.length === 1 ? 'feature' : 'features' }}
              </Badge>
            </TooltipTrigger>
            <TooltipContent side="bottom" class="max-w-xs">
              <p v-for="feature in features.slice(0, 4)" :key="feature.id" class="text-xs">{{ feature.name }}</p>
              <p v-if="features.length > 4" class="text-xs text-muted-foreground">+{{ features.length - 4 }} more</p>
            </TooltipContent>
          </Tooltip>
          <span v-else class="text-sm text-amber-600 flex items-center gap-1">
            <AlertTriangle class="h-3.5 w-3.5" />
            No features
          </span>
        </TooltipProvider>
      </div>
    </CardContent>
  </Card>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger
} from '@/components/ui/tooltip'
import { AlertTriangle } from 'lucide-vue-next'
import { formatPrice, formatIntervalShort } from '@/lib/formatters'
import { useDemoPrefix } from '@/lib/useDemoPrefix'
import { getPlanFeatures } from '../api'
import { getPlanFeatureRule } from '@/features/plan-features/api'
import type { Plan } from '../types'

const props = defineProps<{
  plan: Plan
}>()

const emit = defineEmits<{
  click: []
}>()

const { isDemo } = useDemoPrefix()
const queryClient = useQueryClient()
const planStatus = computed(() => props.plan.status ?? 'draft')

const { data: featuresData, isLoading: isLoadingFeatures } = useQuery({
  queryKey: ['plan-features', props.plan.id],
  queryFn: () => getPlanFeatures(props.plan.id),
  staleTime: 30000,
  enabled: !isDemo.value
})

const features = computed(() => {
  if (isDemo.value) {
    const cached = queryClient.getQueryData<{ data?: { features?: Array<{ id: string; name: string }> } }>(['plan-features', props.plan.id])
    return cached?.data?.features || []
  }
  return featuresData.value?.data?.features || []
})

// Fetch pricing rules to detect usage-based features
const featureModels = ref<Map<string, string>>(new Map())
let modelsFetchId = 0

watch(
  () => featuresData.value?.data?.features,
  async (featureList) => {
    if (!featureList || featureList.length === 0 || isDemo.value) {
      featureModels.value = new Map()
      return
    }
    const fetchId = ++modelsFetchId
    const models = new Map<string, string>()
    await Promise.all(
      featureList.map(async (f) => {
        try {
          const rule = await getPlanFeatureRule(props.plan.id, f.id)
          if (rule?.data?.value?.model) {
            models.set(f.id, rule.data.value.model as string)
          }
        } catch (error) {
          // A 404 means the feature genuinely has no pricing rule (included).
          // Any other failure is a real error and must not be silently treated
          // as "included" — log it so the usage-based indicator can't hide a fault.
          const status = (error as { response?: { status?: number } })?.response?.status
          if (status !== 404) {
            console.error(`Failed to fetch pricing rule for plan ${props.plan.id} feature ${f.id}:`, error)
          }
        }
      })
    )
    // Only apply results if this is still the latest fetch
    if (fetchId === modelsFetchId) {
      featureModels.value = models
    }
  },
  { immediate: true }
)

const hasUsagePricing = computed(() => {
  for (const model of featureModels.value.values()) {
    if (model === 'usage' || model === 'graduated') return true
  }
  return false
})
</script>
