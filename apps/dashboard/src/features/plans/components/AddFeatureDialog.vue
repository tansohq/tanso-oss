<template>
  <Sheet :open="visible" @update:open="emit('update:visible', $event)">
    <SheetContent side="right" class="w-full sm:max-w-lg flex flex-col overflow-hidden">
      <SheetHeader class="shrink-0">
        <SheetTitle>Add Feature</SheetTitle>
        <SheetDescription>
          Add an existing feature to this plan or create a new one
        </SheetDescription>
      </SheetHeader>

      <div class="flex-1 overflow-y-auto overflow-x-hidden flex flex-col gap-5 mt-4 min-h-0 pb-4 -mx-1 px-1">
        <!-- Select Feature -->
        <div class="space-y-2">
          <Label class="text-sm font-medium">Feature</Label>
          <Select v-model="selectedFeatureId">
            <SelectTrigger>
              <SelectValue placeholder="Select a feature..." />
            </SelectTrigger>
            <SelectContent position="popper" class="max-h-60">
              <SelectItem
                v-for="feature in availableFeatures"
                :key="feature.id"
                :value="feature.id"
              >
                {{ feature.name }}
              </SelectItem>
              <SelectSeparator v-if="availableFeatures.length > 0" />
              <SelectItem value="__CREATE_NEW__">
                <span class="flex items-center gap-1.5">
                  <Plus class="w-4 h-4" />
                  Create new feature
                </span>
              </SelectItem>
            </SelectContent>
          </Select>
        </div>

        <!-- Create New Feature Form -->
        <div v-if="selectedFeatureId === '__CREATE_NEW__'" class="space-y-3 p-4 rounded-lg border bg-muted/30">
          <div class="grid grid-cols-2 gap-3">
            <div class="flex flex-col gap-1.5">
              <Label for="newFeatureName" class="text-xs">Name *</Label>
              <Input
                id="newFeatureName"
                v-model="newFeatureName"
                placeholder="Analytics Dashboard"
                class="h-9"
                @input="onNewFeatureNameInput"
              />
              <p v-if="submitAttempted && validationErrors.newFeatureName" class="text-xs text-destructive">
                {{ validationErrors.newFeatureName }}
              </p>
            </div>
            <div class="flex flex-col gap-1.5">
              <Label for="newFeatureKey" class="text-xs">Key *</Label>
              <Input
                id="newFeatureKey"
                v-model="newFeatureKey"
                placeholder="analytics_dashboard"
                class="h-9"
                @input="newFeatureKeyManuallyEdited = true"
              />
              <p v-if="submitAttempted && validationErrors.newFeatureKey" class="text-xs text-destructive">
                {{ validationErrors.newFeatureKey }}
              </p>
            </div>
          </div>

          <div class="flex flex-col gap-1.5">
            <Label for="newFeatureDescription" class="text-xs">Description</Label>
            <Textarea
              id="newFeatureDescription"
              v-model="newFeatureDescription"
              placeholder="Optional description for this feature"
              class="min-h-[60px] resize-none"
            />
          </div>
        </div>

        <!-- Pricing Type (only shown after feature selected) -->
        <template v-if="hasFeatureSelected">
          <Separator />

          <div class="space-y-2">
            <Label class="text-sm font-medium">Pricing Type</Label>
            <Select v-model="pricingType">
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="included">Included</SelectItem>
                <SelectItem value="usage_based">Per-unit pricing</SelectItem>
                <SelectItem value="graduated">Graduated pricing</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <!-- Section 1: Measurement -->
          <div v-if="pricingType === 'usage_based' || pricingType === 'graduated'" class="space-y-2">
            <p class="text-xs font-medium text-muted-foreground uppercase tracking-wide">Measurement</p>
            <div class="flex flex-col gap-1.5">
              <Label for="unitLabel" class="text-xs">Unit Label *</Label>
              <Input
                id="unitLabel"
                v-model="unitLabel"
                placeholder="e.g. devices, API calls, seats"
                class="h-9"
                @input="unitLabelManuallyEdited = true"
              />
              <p v-if="submitAttempted && validationErrors.unitLabel" class="text-xs text-destructive">
                {{ validationErrors.unitLabel }}
              </p>
            </div>
          </div>

          <!-- Section 2: Pricing -->
          <div v-if="pricingType === 'usage_based' || pricingType === 'graduated'" class="space-y-3">
            <p class="text-xs font-medium text-muted-foreground uppercase tracking-wide">Pricing</p>

            <!-- Per-unit: Unit Price -->
            <div v-if="pricingType === 'usage_based'" class="flex flex-col gap-1.5">
              <Label for="unitPrice" class="text-xs">{{ hasCreditModel && !isHardLimit ? 'Overage Rate *' : 'Unit Price *' }}</Label>
              <div class="relative">
                <span class="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">$</span>
                <Input
                  id="unitPrice"
                  v-model.number="unitPrice"
                  type="number"
                  step="0.0001"
                  min="0"
                  :placeholder="hasCreditModel && isHardLimit ? 'Billed via credits' : '0.01'"
                  class="h-9 pl-7"
                  :disabled="hasCreditModel && isHardLimit"
                />
              </div>
              <p v-if="submitAttempted && validationErrors.unitPrice" class="text-xs text-destructive">
                {{ validationErrors.unitPrice }}
              </p>
            </div>

            <!-- Graduated: Tiers -->
            <template v-if="pricingType === 'graduated'">
              <p class="text-xs text-muted-foreground">
                Each tier is charged at its own rate. For example, the first 1,000 units might be free, then units 1,001–10,000 cost $0.10 each.
              </p>
              <div class="flex flex-col gap-2">
                <Label class="text-xs">Tiers *</Label>
                <div class="rounded-lg border p-4 bg-muted/30">
                  <GraduatedTierEditor v-model="tiers" :unit-label="unitLabel" />
                </div>
              </div>
            </template>

            <div class="flex flex-col gap-1.5">
              <Label class="text-xs">Billing Timing</Label>
              <Select v-model="billingTiming">
                <SelectTrigger class="h-9">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="IN_ARREARS">In arrears</SelectItem>
                  <SelectItem value="IN_ADVANCE" disabled>
                    In advance (Coming soon)
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <!-- Credit Model (optional) -->
          <div v-if="pricingType === 'usage_based' || pricingType === 'graduated'" class="space-y-2">
            <p class="text-xs font-medium text-muted-foreground uppercase tracking-wide">Credit Deduction</p>
            <div class="flex flex-col gap-1.5">
              <Label class="text-xs">Credit Model</Label>
              <Select v-model="creditModelId">
                <SelectTrigger class="h-9">
                  <SelectValue placeholder="None (bill directly)" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="__NONE__">None (bill directly)</SelectItem>
                  <SelectItem
                    v-for="cm in allocatedCreditModels"
                    :key="cm.id"
                    :value="cm.id"
                  >
                    {{ cm.name }} ({{ cm.denomination }})
                  </SelectItem>
                </SelectContent>
              </Select>
              <p v-if="hasCreditModel" class="text-xs text-muted-foreground">
                {{ isHardLimit
                  ? 'All usage is billed through credits. Events are blocked when the credit pool is depleted.'
                  : 'Credits are deducted 1:1 per usage unit. When credits run out, overage is billed at the rate above.' }}
              </p>
              <p v-else class="text-xs text-muted-foreground">
                Credits are deducted 1:1 per usage unit. Overage beyond the credit balance is billed at the unit price above.
              </p>
            </div>
          </div>

          <!-- Section 3: Advanced Settings (collapsible) -->
          <Collapsible v-if="pricingType === 'usage_based' || pricingType === 'graduated'" v-model:open="showAdvanced">
            <CollapsibleTrigger as-child>
              <button type="button" class="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors">
                <ChevronRight class="h-4 w-4 transition-transform duration-200" :class="{ 'rotate-90': showAdvanced }" />
                Advanced settings
              </button>
            </CollapsibleTrigger>
            <CollapsibleContent class="-mx-2 px-2 pb-1">
              <div class="space-y-4 pt-3">
                <div class="flex flex-col gap-1.5">
                  <Label class="text-xs">How usage is counted over time</Label>
                  <Select v-model="resetMode">
                    <SelectTrigger class="h-9">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="reset">Per billing period</SelectItem>
                      <SelectItem value="accumulate">Cumulative</SelectItem>
                    </SelectContent>
                  </Select>
                  <p class="text-xs text-muted-foreground">
                    {{ resetMode === 'accumulate' ? 'Usage accumulates across billing periods.' : 'Usage is measured independently each billing period.' }}
                  </p>
                </div>

                <div class="flex flex-col gap-1.5">
                  <Label for="maxUsage" class="text-xs">Usage Limit</Label>
                  <Input
                    id="maxUsage"
                    v-model.number="maxUsage"
                    type="number"
                    min="0"
                    placeholder="Unlimited"
                    class="h-9"
                  />
                  <p class="text-xs text-muted-foreground">
                    Leave blank for unlimited. Usage beyond the limit is recorded but not billed.
                  </p>
                </div>

                <div class="border border-dashed rounded-lg p-3 space-y-3">
                  <div class="flex items-baseline gap-1.5">
                    <Label class="text-xs font-medium">Internal Cost</Label>
                    <span class="text-xs text-muted-foreground">(optional)</span>
                  </div>
                  <div class="flex flex-col gap-1.5">
                    <Label class="text-xs">Cost Model</Label>
                    <Select v-model="costModel">
                      <SelectTrigger class="h-9">
                        <SelectValue placeholder="None" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="none">None</SelectItem>
                        <SelectItem value="simple">Simple (flat rate)</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div v-if="costModel === 'simple'" class="space-y-3">
                    <div class="flex flex-col gap-1.5">
                      <Label class="text-xs">Cost Unit</Label>
                      <Select v-model="costUnit">
                        <SelectTrigger class="h-9">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="CURRENCY">Currency ({{ accountCurrency }})</SelectItem>
                          <SelectItem value="TOKENS">Tokens</SelectItem>
                          <SelectItem value="CREDITS">Credits</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>
                    <div class="grid grid-cols-2 gap-4">
                      <div class="flex flex-col gap-1.5">
                        <Label for="costPerUnit" class="text-xs">{{ costUnit === 'CURRENCY' ? `Cost per Unit (${accountCurrency})` : 'Cost per Unit' }}</Label>
                        <div class="relative">
                          <span v-if="costUnit === 'CURRENCY'" class="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">$</span>
                          <Input
                            id="costPerUnit"
                            v-model.number="costPerUnit"
                            type="number"
                            step="0.0001"
                            min="0"
                            placeholder="0.005"
                            :class="['h-9', costUnit === 'CURRENCY' ? 'pl-7' : '']"
                          />
                        </div>
                      </div>
                      <div class="flex flex-col gap-1.5">
                        <Label class="text-xs">Margin</Label>
                        <div class="h-9 flex items-center">
                          <span v-if="calculatedMargin !== null" :class="marginClass">
                            {{ calculatedMargin }}%
                          </span>
                          <span v-else class="text-muted-foreground text-sm">—</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </CollapsibleContent>
          </Collapsible>
        </template>

        <Alert v-if="errorMessage" variant="destructive">
          <AlertCircle class="h-4 w-4" />
          <AlertDescription>{{ errorMessage }}</AlertDescription>
        </Alert>
      </div>

      <SheetFooter class="shrink-0 flex-col items-stretch gap-2">
        <div class="flex justify-end gap-2">
          <Button variant="outline" @click="close" :disabled="isSubmitting">
            Cancel
          </Button>
          <Button @click="addFeature" :disabled="isSubmitting">
            <Loader2 v-if="isSubmitting" class="mr-2 h-4 w-4 animate-spin" />
            {{ isSubmitting ? 'Adding...' : 'Add Feature' }}
          </Button>
        </div>
        <p v-if="submitAttempted && !canSubmit && firstValidationError" class="text-xs text-destructive text-right">
          {{ firstValidationError }}
        </p>
      </SheetFooter>
    </SheetContent>
  </Sheet>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { toast } from '@/components/ui/toast/use-toast'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Separator } from '@/components/ui/separator'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle
} from '@/components/ui/sheet'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectSeparator,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import { Plus, Loader2, AlertCircle, ChevronRight } from 'lucide-vue-next'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger
} from '@/components/ui/collapsible'
import { parseApiError } from '@/lib/parseApiError'
import { useFeaturesQuery } from '@/features/features/queries'
import { useCreditModelsQuery, usePlanCreditAllocationsQuery } from '@/features/credits/queries'
import { useAccountSettingsQuery } from '@/features/integrations/queries'
import type { CreditModel, PlanCreditAllocation } from '@/features/credits/types'
import { useCreateFeatureMutation } from '@/features/features/mutations'
import {
  useCreatePlanFeatureRuleMutation
} from '@/features/plan-features/mutations'
import { getPlanFeatures } from '../api'
import GraduatedTierEditor from './GraduatedTierEditor.vue'
import type { Feature } from '@/features/features/types'
import type { GraduatedTier, PlanStatus, LinkedFeature } from '../types'

const props = defineProps<{
  visible: boolean
  planId: string
  linkedFeatures?: LinkedFeature[]
  planStatus?: PlanStatus
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'update:success': []
}>()

const queryClient = useQueryClient()

// Form state
const selectedFeatureId = ref<string>('')
const newFeatureName = ref('')
const newFeatureKey = ref('')
const newFeatureDescription = ref('')
const newFeatureKeyManuallyEdited = ref(false)
const pricingType = ref<'included' | 'usage_based' | 'graduated'>('included')
const unitPrice = ref<number | undefined>(undefined)
const unitLabel = ref('')
const costPerUnit = ref<number | undefined>(undefined)
const costModel = ref<string>('none')
const costUnit = ref<string>('CURRENCY')
const billingTiming = ref('IN_ARREARS')
const tiers = ref<GraduatedTier[]>([
  { up_to: 1000, price_per_unit: 0, flat_fee: 0 },
  { up_to: 'inf', price_per_unit: 0.001, flat_fee: 0 }
])
const resetMode = ref<'reset' | 'accumulate'>('reset')
const maxUsage = ref<number | undefined>(undefined)
const unitLabelManuallyEdited = ref(false)
const showAdvanced = ref(false)
const errorMessage = ref<string | null>(null)
const creditModelId = ref<string>('__NONE__')
const submitAttempted = ref(false)

// Current features (local copy)
const currentFeatures = ref<LinkedFeature[]>([])

// Query for plan's current features
const planId = computed(() => props.planId)
const { data: planFeaturesData } = useQuery({
  queryKey: ['plan-features', planId],
  queryFn: () => getPlanFeatures(planId.value),
  enabled: computed(() => props.visible && !!planId.value)
})

// Queries and mutations
const { data: creditModelsData } = useCreditModelsQuery()
const creditModels = computed<CreditModel[]>(() => {
  if (!creditModelsData.value?.data) return []
  return Array.isArray(creditModelsData.value.data) ? creditModelsData.value.data : []
})

// Allocation query for hardLimit lookup
const { data: allocationsData } = usePlanCreditAllocationsQuery(computed(() => props.planId))
const allocationsForPlan = computed<PlanCreditAllocation[]>(() => {
  if (!allocationsData.value?.data) return []
  return Array.isArray(allocationsData.value.data) ? allocationsData.value.data : []
})

// Filter credit models to only those with an allocation on this plan
const allocatedCreditModels = computed(() => {
  const allocatedIds = new Set(allocationsForPlan.value.map(a => a.creditModelId))
  return creditModels.value.filter(cm => allocatedIds.has(cm.id))
})

// Account currency
const { data: accountSettingsData } = useAccountSettingsQuery()
const accountCurrency = computed(() => accountSettingsData.value?.data?.currency ?? 'USD')

const isHardLimit = computed(() => {
  const cmId = creditModelId.value
  if (cmId === '__NONE__') return false
  const allocation = allocationsForPlan.value.find(a => a.creditModelId === cmId)
  if (allocation?.hardLimit != null) return allocation.hardLimit
  const cm = creditModels.value.find(c => c.id === cmId)
  return cm?.hardLimit !== false
})

const hasCreditModel = computed(() => creditModelId.value !== '__NONE__')

const { data: featuresData } = useFeaturesQuery()
const { mutateAsync: createFeature, isPending: isCreatingFeature } = useCreateFeatureMutation()
const { mutateAsync: createPlanFeatureRule, isPending: isSyncing } = useCreatePlanFeatureRuleMutation()

const isSubmitting = computed(() => isSyncing.value || isCreatingFeature.value)

const allFeatures = computed(() => {
  if (!featuresData.value) return []
  if (Array.isArray(featuresData.value)) return featuresData.value
  if (featuresData.value.data && Array.isArray(featuresData.value.data)) {
    return featuresData.value.data
  }
  return []
})

const availableFeatures = computed(() => {
  const linkedIds = new Set(currentFeatures.value.map((f) => f.id))
  return allFeatures.value.filter((f: Feature) => !linkedIds.has(f.id))
})

// Calculate margin
const calculatedMargin = computed(() => {
  if (unitPrice.value && costPerUnit.value && unitPrice.value > 0) {
    const margin = ((unitPrice.value - costPerUnit.value) / unitPrice.value) * 100
    return Math.round(margin * 100) / 100
  }
  return null
})

const marginClass = computed(() => {
  if (calculatedMargin.value === null) return ''
  if (calculatedMargin.value >= 50) return 'text-green-600 font-medium'
  if (calculatedMargin.value >= 20) return 'text-yellow-600 font-medium'
  return 'text-red-600 font-medium'
})

// Check if a feature is selected (existing or creating new with valid fields)
const isCreatingNew = computed(() => selectedFeatureId.value === '__CREATE_NEW__')

const hasFeatureSelected = computed(() => {
  if (isCreatingNew.value) {
    return newFeatureName.value.trim() !== '' && newFeatureKey.value.trim() !== ''
  }
  return selectedFeatureId.value !== ''
})

// Validation
const canSubmit = computed(() => {
  if (!hasFeatureSelected.value) return false

  if (pricingType.value === 'usage_based') {
    if (unitLabel.value.trim() === '') return false
    if (hasCreditModel.value && isHardLimit.value) return true
    return unitPrice.value !== undefined && unitPrice.value >= 0
  }

  if (pricingType.value === 'graduated') {
    if (unitLabel.value.trim() === '') return false
    if (tiers.value.length < 1) return false
    return tiers.value.every((t) => t.price_per_unit >= 0)
  }

  return true
})

const validationErrors = computed(() => {
  const errors: Record<string, string> = {}
  if (isCreatingNew.value) {
    if (newFeatureName.value.trim() === '') errors.newFeatureName = 'Name is required'
    if (newFeatureKey.value.trim() === '') errors.newFeatureKey = 'Key is required'
  }
  if (hasFeatureSelected.value && (pricingType.value === 'usage_based' || pricingType.value === 'graduated')) {
    if (unitLabel.value.trim() === '') errors.unitLabel = 'Unit label is required'
  }
  if (pricingType.value === 'usage_based' && !(hasCreditModel.value && isHardLimit.value)) {
    if (unitPrice.value === undefined) errors.unitPrice = 'Unit price is required'
    else if (unitPrice.value < 0) errors.unitPrice = 'Unit price must be 0 or greater'
  }
  return errors
})

const firstValidationError = computed(() => {
  const keys = Object.keys(validationErrors.value)
  return keys.length > 0 ? validationErrors.value[keys[0]] : null
})

// Sync local state from query or props
watch(
  [() => planFeaturesData.value, () => props.linkedFeatures],
  ([queryData, propsData]) => {
    const features = queryData?.data?.features || propsData || []
    currentFeatures.value = [...features]
  },
  { immediate: true }
)

// Auto-fill unit label with feature name
watch(selectedFeatureId, (id) => {
  if (unitLabelManuallyEdited.value) return
  if (id && id !== '__CREATE_NEW__') {
    const feature = allFeatures.value.find((f: Feature) => f.id === id)
    unitLabel.value = feature?.name ?? ''
  } else if (id !== '__CREATE_NEW__') {
    unitLabel.value = ''
  }
})

watch(newFeatureName, (name) => {
  if (unitLabelManuallyEdited.value || !isCreatingNew.value) return
  unitLabel.value = name
})

// Reset form when drawer opens
watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      resetForm()
    }
  }
)

function generateKey(name: string): string {
  return name
    .toLowerCase()
    .replace(/[^a-z0-9\s-]/g, '')
    .replace(/\s+/g, '_')
    .replace(/-+/g, '_')
    .replace(/^_+|_+$/g, '')
}

function onNewFeatureNameInput() {
  if (!newFeatureKeyManuallyEdited.value) {
    newFeatureKey.value = generateKey(newFeatureName.value)
  }
}

function resetCreateForm() {
  newFeatureName.value = ''
  newFeatureKey.value = ''
  newFeatureDescription.value = ''
  newFeatureKeyManuallyEdited.value = false
}

function resetForm() {
  selectedFeatureId.value = ''
  resetCreateForm()
  pricingType.value = 'included'
  unitPrice.value = undefined
  unitLabel.value = ''
  unitLabelManuallyEdited.value = false
  costPerUnit.value = undefined
  costModel.value = 'none'
  costUnit.value = 'CURRENCY'
  billingTiming.value = 'IN_ARREARS'
  tiers.value = [
    { up_to: 1000, price_per_unit: 0, flat_fee: 0 },
    { up_to: 'inf', price_per_unit: 0.001, flat_fee: 0 }
  ]
  resetMode.value = 'reset'
  maxUsage.value = undefined
  showAdvanced.value = false
  errorMessage.value = null
  creditModelId.value = '__NONE__'
  submitAttempted.value = false
}

function buildFeaturePayload(featureId: string) {
  if (pricingType.value === 'usage_based') {
    const resolvedCreditModelId = creditModelId.value !== '__NONE__' ? creditModelId.value : undefined
    return {
      featureId,
      isEnabled: true,
      type: 'BASE',
      value: {
        model: 'usage',
        price_per_unit: (resolvedCreditModelId && isHardLimit.value) ? 0 : unitPrice.value,
        usage_unit_type: unitLabel.value,
        billing_timing: billingTiming.value,
        ...(costModel.value !== 'none' && {
          cost_model: costModel.value,
          cost_per_unit: costPerUnit.value,
          cost_unit: costUnit.value,
        }),
        ...(resetMode.value !== 'reset' && { reset_mode: resetMode.value }),
        ...(maxUsage.value !== undefined && maxUsage.value > 0 && { max_usage: maxUsage.value })
      }
    }
  }

  if (pricingType.value === 'graduated') {
    return {
      featureId,
      isEnabled: true,
      type: 'BASE',
      value: {
        model: 'graduated',
        usage_unit_type: unitLabel.value,
        billing_timing: billingTiming.value,
        tiers: tiers.value,
        ...(costModel.value !== 'none' && {
          cost_model: costModel.value,
          cost_per_unit: costPerUnit.value,
          cost_unit: costUnit.value,
        }),
        ...(resetMode.value !== 'reset' && { reset_mode: resetMode.value }),
        ...(maxUsage.value !== undefined && maxUsage.value > 0 && { max_usage: maxUsage.value })
      }
    }
  }

  return {
    featureId,
    isEnabled: true,
    type: 'BASE',
    value: {}
  }
}


async function addFeature() {
  submitAttempted.value = true
  if (!canSubmit.value) return
  try {
    errorMessage.value = null
    let featureId: string

    if (isCreatingNew.value && newFeatureName.value && newFeatureKey.value) {
      // Create new feature first
      const result = await createFeature({
        name: newFeatureName.value,
        key: newFeatureKey.value,
        description: newFeatureDescription.value || '',
        isEnabled: true
      })

      const createdFeature = result?.data
      if (!createdFeature?.id) {
        throw new Error('Failed to create feature')
      }
      featureId = createdFeature.id
      queryClient.invalidateQueries({ queryKey: ['features'] })
    } else if (selectedFeatureId.value && selectedFeatureId.value !== '__CREATE_NEW__') {
      featureId = selectedFeatureId.value
    } else {
      errorMessage.value = 'Please select or create a feature'
      return
    }

    const payload = buildFeaturePayload(featureId)
    const resolvedCreditModelId = creditModelId.value !== '__NONE__' ? creditModelId.value : undefined
    await createPlanFeatureRule({
      planId: props.planId,
      featureId: featureId,
      type: payload.type,
      value: payload.value,
      isEnabled: payload.isEnabled,
      creditModelId: resolvedCreditModelId
    })

    queryClient.invalidateQueries({ queryKey: ['plan-features', props.planId] })
    emit('update:success')

    toast({ title: 'Success', description: 'Feature added to plan' })
    close()
  } catch (error) {
    const parsedError = parseApiError(error)
    errorMessage.value = parsedError.message
    toast({ title: 'Error', description: parsedError.message, variant: 'destructive' })
  }
}

function close() {
  resetForm()
  emit('update:visible', false)
}
</script>
