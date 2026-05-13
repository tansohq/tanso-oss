<template>
  <Sheet :open="visible" @update:open="handleClose">
    <SheetContent class="w-full sm:max-w-lg flex flex-col overflow-hidden">
      <SheetHeader class="shrink-0">
        <SheetTitle>{{ isEditingPricing ? 'Edit Feature Pricing' : 'Feature Details' }}</SheetTitle>
        <SheetDescription v-if="feature">
          {{ feature.name }}
        </SheetDescription>
      </SheetHeader>

      <div v-if="feature" class="flex-1 overflow-y-auto overflow-x-hidden flex flex-col gap-5 mt-4 min-h-0 pb-4 -mx-1 px-1">
        <!-- Basic Info (view mode only) -->
        <div v-if="!isEditingPricing" class="grid grid-cols-2 gap-4">
          <div class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Name</Label>
            <p class="text-sm font-medium">{{ feature.name }}</p>
          </div>
          <div class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Key</Label>
            <div class="flex items-center gap-2">
              <p class="text-sm font-mono bg-muted px-2 py-1 rounded truncate">{{ feature.key }}</p>
              <CopyButton :value="feature.key" label="Feature key" />
            </div>
          </div>
          <div class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Pricing Type</Label>
            <p class="text-sm font-medium">{{ pricingLabel }}</p>
          </div>
          <div class="flex flex-col gap-1">
            <Label class="text-muted-foreground text-xs">Billing</Label>
            <p class="text-sm">{{ feature.pricingType === 'included' ? '\u2014' : (feature.billingTiming === 'IN_ADVANCE' ? 'In advance' : 'In arrears') }}</p>
          </div>
          <div v-if="feature.description" class="flex flex-col gap-1 col-span-2">
            <Label class="text-muted-foreground text-xs">Description</Label>
            <p class="text-sm text-muted-foreground">{{ feature.description }}</p>
          </div>
        </div>

        <!-- VIEW MODE: Per-unit details -->
        <template v-if="!isEditingPricing && feature.pricingType === 'usage_based'">
          <Separator />
          <div class="grid grid-cols-2 gap-4">
            <div v-if="!(feature.creditModelId && viewIsHardLimit)" class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">{{ feature.creditModelId && !viewIsHardLimit ? 'Overage Rate' : 'Unit Price' }}</Label>
              <p class="text-sm tabular-nums">{{ formatUnitPrice(feature.unitPrice) }}</p>
            </div>
            <div class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Unit Label</Label>
              <p class="text-sm">{{ feature.unitLabel || '\u2014' }}</p>
            </div>
            <div class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Cost Model</Label>
              <p class="text-sm">{{ feature.value?.cost_model === 'simple' ? 'Simple (flat rate)' : (feature.value?.cost_model || '\u2014') }}</p>
            </div>
            <div class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Cost Unit</Label>
              <p class="text-sm">{{ feature.value?.cost_model ? viewCostUnitLabel : '\u2014' }}</p>
            </div>
            <div class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Cost per Unit</Label>
              <p class="text-sm tabular-nums">{{ feature.costPerUnit !== undefined ? formatUnitPrice(feature.costPerUnit) : '\u2014' }}</p>
            </div>
            <div class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Margin</Label>
              <p class="text-sm font-medium" :class="marginClass">{{ calculatedMargin !== null ? `${calculatedMargin}%` : '\u2014' }}</p>
            </div>
            <div class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Usage Counting</Label>
              <p class="text-sm">{{ feature.value?.reset_mode === 'accumulate' ? 'Cumulative' : 'Per billing period' }}</p>
            </div>
            <div class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Usage Limit</Label>
              <p class="text-sm tabular-nums">{{ feature.value?.max_usage ? Number(feature.value.max_usage).toLocaleString() : 'Unlimited' }}</p>
            </div>
            <div class="flex flex-col gap-1 col-span-2">
              <Label class="text-muted-foreground text-xs">Credit Model</Label>
              <div v-if="feature.creditModelName" class="flex items-center gap-2">
                <p class="text-sm">{{ feature.creditModelName }} ({{ feature.creditDenomination }})</p>
                <Badge v-if="viewAllocation" class="bg-blue-50 text-blue-700 border border-blue-200/50 shadow-none text-xs">
                  {{ viewAllocation.creditAmount }} {{ feature.creditDenomination || 'credits' }}
                </Badge>
                <Badge v-if="viewAllocation?.hardLimit === false" variant="outline" class="text-xs">
                  Soft limit
                </Badge>
              </div>
              <p v-else class="text-sm">&mdash;</p>
            </div>
          </div>
        </template>

        <!-- VIEW MODE: Graduated details -->
        <template v-if="!isEditingPricing && feature.pricingType === 'graduated' && feature.tiers">
          <Separator />
          <div class="grid grid-cols-2 gap-4">
            <div class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Unit Label</Label>
              <p class="text-sm">{{ feature.unitLabel || '\u2014' }}</p>
            </div>
            <div class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Cost Model</Label>
              <p class="text-sm">{{ feature.value?.cost_model === 'simple' ? 'Simple (flat rate)' : (feature.value?.cost_model || '\u2014') }}</p>
            </div>
            <div class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Cost Unit</Label>
              <p class="text-sm">{{ feature.value?.cost_model ? viewCostUnitLabel : '\u2014' }}</p>
            </div>
            <div class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Cost per Unit</Label>
              <p class="text-sm tabular-nums">{{ feature.costPerUnit !== undefined ? formatUnitPrice(feature.costPerUnit) : '\u2014' }}</p>
            </div>
            <div class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Usage Counting</Label>
              <p class="text-sm">{{ feature.value?.reset_mode === 'accumulate' ? 'Cumulative' : 'Per billing period' }}</p>
            </div>
            <div class="flex flex-col gap-1">
              <Label class="text-muted-foreground text-xs">Usage Limit</Label>
              <p class="text-sm tabular-nums">{{ feature.value?.max_usage ? Number(feature.value.max_usage).toLocaleString() : 'Unlimited' }}</p>
            </div>
            <div class="flex flex-col gap-1 col-span-2">
              <Label class="text-muted-foreground text-xs">Credit Model</Label>
              <div v-if="feature.creditModelName" class="flex items-center gap-2">
                <p class="text-sm">{{ feature.creditModelName }} ({{ feature.creditDenomination }})</p>
                <Badge v-if="viewAllocation" class="bg-blue-50 text-blue-700 border border-blue-200/50 shadow-none text-xs">
                  {{ viewAllocation.creditAmount }} {{ feature.creditDenomination || 'credits' }}
                </Badge>
                <Badge v-if="viewAllocation?.hardLimit === false" variant="outline" class="text-xs">
                  Soft limit
                </Badge>
              </div>
              <p v-else class="text-sm">&mdash;</p>
            </div>
          </div>
          <div class="flex flex-col gap-2">
            <Label class="text-muted-foreground text-xs">Tiers</Label>
            <div class="rounded-md border">
              <div class="grid grid-cols-4 gap-4 text-xs text-muted-foreground px-3 py-2 bg-muted/30">
                <div>From</div>
                <div>To</div>
                <div>Price per Unit</div>
                <div>Flat Fee</div>
              </div>
              <div
                v-for="(tier, idx) in feature.tiers"
                :key="idx"
                class="grid grid-cols-4 gap-4 text-sm px-3 py-2 border-t"
              >
                <div class="tabular-nums">{{ idx === 0 ? '0' : formatTierBound(feature.tiers![idx - 1].up_to) }}</div>
                <div class="tabular-nums">{{ tier.up_to === 'inf' ? '\u221E' : tier.up_to.toLocaleString() }}</div>
                <div class="tabular-nums font-mono">{{ formatUnitPrice(tier.price_per_unit) }}</div>
                <div class="tabular-nums font-mono">{{ formatUnitPrice(tier.flat_fee ?? 0) }}</div>
              </div>
            </div>
          </div>
        </template>

        <!-- EDIT MODE: Pricing form fields -->
        <template v-if="isEditingPricing">
          <Separator />

          <!-- Section: Pricing -->
          <div class="space-y-3">
            <p class="text-xs font-medium text-muted-foreground uppercase tracking-wide">Pricing</p>

            <!-- Pricing Model Selector -->
            <div class="flex flex-col gap-1.5">
              <Label class="text-xs">Pricing Model</Label>
              <Select v-model="editPricingModel">
                <SelectTrigger class="h-9">
                  <SelectValue placeholder="Select model" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="usage">Per-unit (flat rate)</SelectItem>
                  <SelectItem value="graduated">Graduated (tiered rates)</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <!-- Per-unit: Unit Price -->
            <div v-if="editPricingModel === 'usage'" class="flex flex-col gap-1.5">
              <Label for="editUnitPrice" class="text-xs">{{ hasCreditModel && !isHardLimit ? 'Overage Rate *' : 'Unit Price *' }}</Label>
              <div class="relative">
                <span class="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">$</span>
                <Input
                  id="editUnitPrice"
                  v-model.number="editUnitPrice"
                  type="number"
                  step="0.0001"
                  min="0"
                  :placeholder="hasCreditModel && isHardLimit ? 'Billed via credits' : '0.01'"
                  class="h-9 pl-7"
                  :disabled="hasCreditModel && isHardLimit"
                />
              </div>
            </div>

            <!-- Graduated: Tiers -->
            <template v-if="editPricingModel === 'graduated'">
              <div class="flex flex-col gap-2">
                <Label class="text-xs">Tiers *</Label>
                <div class="rounded-lg border p-4 bg-muted/30">
                  <GraduatedTierEditor v-model="editTiers" :unit-label="featureUnitLabel" />
                </div>
              </div>
            </template>

            <div class="flex flex-col gap-1.5">
              <Label class="text-xs">Billing Timing</Label>
              <Select v-model="editBillingTiming">
                <SelectTrigger class="h-9">
                  <SelectValue placeholder="Select timing" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="IN_ARREARS">In arrears</SelectItem>
                  <SelectItem value="IN_ADVANCE" disabled>In advance (Coming soon)</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <!-- Section: Credit Deduction -->
          <div class="space-y-3">
            <p class="text-xs font-medium text-muted-foreground uppercase tracking-wide">Credit Deduction</p>
            <div class="flex flex-col gap-1.5">
              <Label class="text-xs">Credit Model</Label>
              <Select v-model="editCreditModelId">
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

          <!-- Section: Advanced Settings (collapsible) -->
          <Collapsible v-model:open="editShowAdvanced">
            <CollapsibleTrigger as-child>
              <button type="button" class="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors">
                <ChevronRight class="h-4 w-4 transition-transform duration-200" :class="{ 'rotate-90': editShowAdvanced }" />
                Advanced settings
              </button>
            </CollapsibleTrigger>
            <CollapsibleContent class="-mx-2 px-2 pb-1">
              <div class="space-y-4 pt-3">
                <div class="flex flex-col gap-1.5">
                  <Label class="text-xs">How usage is counted over time</Label>
                  <Select v-model="editResetMode">
                    <SelectTrigger class="h-9">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="reset">Per billing period</SelectItem>
                      <SelectItem value="accumulate">Cumulative</SelectItem>
                    </SelectContent>
                  </Select>
                  <p class="text-xs text-muted-foreground">
                    {{ editResetModeHelperText }}
                  </p>
                </div>

                <div class="flex flex-col gap-1.5">
                  <Label for="editMaxUsage" class="text-xs">Usage Limit</Label>
                  <Input
                    id="editMaxUsage"
                    v-model.number="editMaxUsage"
                    type="number"
                    min="0"
                    placeholder="Unlimited"
                    class="h-9"
                  />
                  <p class="text-xs text-muted-foreground">
                    Leave blank for unlimited. Events beyond the limit are recorded but zeroed out.
                  </p>
                </div>

                <div class="border border-dashed rounded-lg p-3 space-y-3">
                  <div class="flex items-baseline gap-1.5">
                    <Label class="text-xs font-medium">Internal Cost</Label>
                    <span class="text-xs text-muted-foreground">(optional)</span>
                  </div>
                  <div class="flex flex-col gap-1.5">
                    <Label class="text-xs">Cost Model</Label>
                    <Select v-model="editCostModel">
                      <SelectTrigger class="h-9">
                        <SelectValue placeholder="None" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="none">None</SelectItem>
                        <SelectItem value="simple">Simple (flat rate)</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div v-if="editCostModel === 'simple'" class="space-y-3">
                    <div class="flex flex-col gap-1.5">
                      <Label class="text-xs">Cost Unit</Label>
                      <Select v-model="editCostUnit">
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
                        <Label for="editCostPerUnit" class="text-xs">{{ editCostUnit === 'CURRENCY' ? `Cost per Unit (${accountCurrency})` : 'Cost per Unit' }}</Label>
                        <div class="relative">
                          <span v-if="editCostUnit === 'CURRENCY'" class="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground text-sm">$</span>
                          <Input
                            id="editCostPerUnit"
                            v-model.number="editCostPerUnit"
                            type="number"
                            step="0.0001"
                            min="0"
                            placeholder="0.005"
                            :class="['h-9', editCostUnit === 'CURRENCY' ? 'pl-7' : '']"
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

          <Alert v-if="editErrorMessage" variant="destructive">
            <AlertCircle class="h-4 w-4" />
            <AlertDescription>{{ editErrorMessage }}</AlertDescription>
          </Alert>
        </template>

      </div>

      <!-- VIEW MODE footer -->
      <SheetFooter v-if="feature && !isEditingPricing && (props.canRemove || (props.canEdit && feature.pricingType !== 'included'))" class="mt-6 shrink-0 flex items-center justify-between">
        <Button v-if="props.canRemove" variant="ghost" size="sm" class="text-destructive hover:text-destructive hover:bg-destructive/10" @click="onRemove">
          <Trash2 class="w-3.5 h-3.5 mr-1.5" />
          Remove
        </Button>
        <Button
          v-if="props.canEdit && feature.pricingType !== 'included'"
          variant="outline"
          size="sm"
          @click="startEditingPricing"
        >
          <Pencil class="w-4 h-4 mr-2" />
          Edit Pricing
        </Button>
      </SheetFooter>

      <!-- EDIT MODE footer -->
      <SheetFooter v-if="feature && isEditingPricing" class="mt-6 shrink-0 gap-2">
        <Button variant="outline" size="sm" :disabled="isSaving" @click="cancelEditingPricing">
          Cancel
        </Button>
        <Button size="sm" :disabled="isSaving || !isValid" @click="savePricing">
          <Loader2 v-if="isSaving" class="mr-2 h-4 w-4 animate-spin" />
          {{ isSaving ? 'Saving...' : 'Save' }}
        </Button>
      </SheetFooter>
    </SheetContent>
  </Sheet>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { toast } from '@/components/ui/toast/use-toast'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle
} from '@/components/ui/sheet'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger
} from '@/components/ui/collapsible'
import { Trash2, Pencil, AlertCircle, Loader2, ChevronRight } from 'lucide-vue-next'
import { Badge } from '@/components/ui/badge'
import CopyButton from '@/components/CopyButton.vue'
import GraduatedTierEditor from './GraduatedTierEditor.vue'
import { parseApiError } from '@/lib/parseApiError'
import { formatUnitPrice } from '@/lib/formatters'
import {
  useUpdatePlanFeatureRuleMutation
} from '@/features/plan-features/mutations'
import { useCreditModelsQuery, usePlanCreditAllocationsQuery } from '@/features/credits/queries'
import { useAccountSettingsQuery } from '@/features/integrations/queries'
import type { CreditModel, PlanCreditAllocation } from '@/features/credits/types'
import type { PlanStatus, GraduatedTier, LinkedFeature } from '../types'

const props = defineProps<{
  visible: boolean
  feature: LinkedFeature | null
  planId: string
  planStatus?: PlanStatus
  allFeatures?: LinkedFeature[]
  canEdit?: boolean
  canRemove?: boolean
  initialEdit?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'remove': [featureId: string]
  'update:success': []
}>()

const queryClient = useQueryClient()
const { mutateAsync: updatePlanFeatureRule, isPending: isSaving } = useUpdatePlanFeatureRuleMutation()
const { data: creditModelsData } = useCreditModelsQuery()
const creditModels = computed<CreditModel[]>(() => {
  if (!creditModelsData.value?.data) return []
  return Array.isArray(creditModelsData.value.data) ? creditModelsData.value.data : []
})

// Allocation query for hardLimit lookup
const { data: allocationsData } = usePlanCreditAllocationsQuery(computed(() => props.planId))
const allocations = computed<PlanCreditAllocation[]>(() => {
  if (!allocationsData.value?.data) return []
  return Array.isArray(allocationsData.value.data) ? allocationsData.value.data : []
})

// Account currency
const { data: accountSettingsData } = useAccountSettingsQuery()
const accountCurrency = computed(() => accountSettingsData.value?.data?.currency ?? 'USD')

// Filter credit models to only those with an allocation on this plan
const allocatedCreditModels = computed(() => {
  const allocatedIds = new Set(allocations.value.map(a => a.creditModelId))
  return creditModels.value.filter(cm => allocatedIds.has(cm.id))
})

// View-mode: allocation details for the feature's credit model
const viewAllocation = computed(() => {
  if (!props.feature?.creditModelId) return null
  return allocations.value.find(a => a.creditModelId === props.feature?.creditModelId) ?? null
})

// Edit-mode: hardLimit for the selected credit model
const isHardLimit = computed(() => {
  const cmId = editCreditModelId.value
  if (cmId === '__NONE__') return false
  const allocation = allocations.value.find(a => a.creditModelId === cmId)
  if (allocation?.hardLimit != null) return allocation.hardLimit
  const cm = creditModels.value.find(c => c.id === cmId)
  return cm?.hardLimit !== false
})

const hasCreditModel = computed(() => editCreditModelId.value !== '__NONE__')

// View-mode: hardLimit for the feature's credit model
const viewIsHardLimit = computed(() => {
  if (!props.feature?.creditModelId) return false
  const allocation = allocations.value.find(a => a.creditModelId === props.feature?.creditModelId)
  if (allocation?.hardLimit != null) return allocation.hardLimit
  const cm = creditModels.value.find(c => c.id === props.feature?.creditModelId)
  return cm?.hardLimit !== false
})

// Edit mode state
const isEditingPricing = ref(false)
const editPricingModel = ref<'usage' | 'graduated'>('usage')
const editUnitPrice = ref<number | undefined>(undefined)
const editCostPerUnit = ref<number | undefined>(undefined)
const editCostModel = ref<string>('none')
const editCostUnit = ref<string>('CURRENCY')
const editBillingTiming = ref<string>('IN_ARREARS')
const editTiers = ref<GraduatedTier[]>([
  { up_to: 1000, price_per_unit: 0, flat_fee: 0 },
  { up_to: 'inf', price_per_unit: 0.001, flat_fee: 0 }
])
const editErrorMessage = ref<string | null>(null)
const editShowAdvanced = ref(false)
const editCreditModelId = ref<string>('__NONE__')
const editResetMode = ref<'reset' | 'accumulate'>('reset')
const editMaxUsage = ref<number | undefined>(undefined)

const featureUnitLabel = computed(() => {
  if (!props.feature) return undefined
  const value = props.feature.value as { usage_unit_type?: string } | undefined
  return value?.usage_unit_type ?? props.feature.unitLabel
})

const pricingLabel = computed(() => {
  if (!props.feature) return ''
  switch (props.feature.pricingType) {
    case 'included': return 'Included'
    case 'usage_based': return 'Per Unit'
    case 'graduated': return 'Graduated'
    default: return ''
  }
})

// Margin calculation — works for both view and edit mode
const calculatedMargin = computed(() => {
  const price = isEditingPricing.value ? editUnitPrice.value : props.feature?.unitPrice
  const cost = isEditingPricing.value ? editCostPerUnit.value : props.feature?.costPerUnit
  if (price && cost && price > 0) {
    return Math.round(((price - cost) / price) * 10000) / 100
  }
  return null
})

const marginClass = computed(() => {
  if (calculatedMargin.value === null) return ''
  if (calculatedMargin.value >= 50) return 'text-green-600 font-medium'
  if (calculatedMargin.value >= 20) return 'text-yellow-600 font-medium'
  return 'text-red-600 font-medium'
})

const viewCostUnitLabel = computed(() => {
  const costUnit = (props.feature?.value as { cost_unit?: string } | undefined)?.cost_unit ?? 'CURRENCY'
  if (costUnit === 'CURRENCY') return `Currency (${accountCurrency.value})`
  if (costUnit === 'TOKENS') return 'Tokens'
  if (costUnit === 'CREDITS') return 'Credits'
  return costUnit
})

const editResetModeHelperText = computed(() => {
  return editResetMode.value === 'accumulate'
    ? 'Usage accumulates across billing periods.'
    : 'Usage is measured independently each billing period.'
})

const isValid = computed(() => {
  if (editPricingModel.value === 'graduated') {
    return editTiers.value.length >= 1 && editTiers.value.every((t) => t.price_per_unit >= 0)
  }
  if (editPricingModel.value === 'usage') {
    if (hasCreditModel.value && isHardLimit.value) return true
    return editUnitPrice.value !== undefined && editUnitPrice.value > 0
  }
  return editUnitPrice.value !== undefined && editUnitPrice.value > 0
})

// Reset state when drawer opens; optionally start in edit mode
watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      editErrorMessage.value = null
      editShowAdvanced.value = false
      if (props.initialEdit && props.canEdit && props.feature && props.feature.pricingType !== 'included') {
        initFromFeature(props.feature)
        isEditingPricing.value = true
      } else {
        isEditingPricing.value = false
      }
    }
  }
)

function initFromFeature(feature: LinkedFeature) {
  const value = feature.value as {
    model?: string
    price_per_unit?: number
    usage_unit_type?: string
    cost_model?: string
    cost_per_unit?: number
    cost_unit?: string
    billing_timing?: string
    reset_mode?: string
    max_usage?: number
    tiers?: Array<{ up_to: number | 'inf'; price_per_unit: number; flat_fee?: number }>
  } | undefined

  const defaultTiers = [
    { up_to: 1000 as number | 'inf', price_per_unit: 0, flat_fee: 0 },
    { up_to: 'inf' as const, price_per_unit: 0.001, flat_fee: 0 }
  ]

  const model = value?.model
  if (model === 'graduated') {
    editPricingModel.value = 'graduated'
    const tiers = value?.tiers
    // Validate tier structure before using
    const tiersValid = Array.isArray(tiers) && tiers.length > 0 &&
      tiers.every((t) => t && typeof t.price_per_unit === 'number' && (typeof t.up_to === 'number' || t.up_to === 'inf'))
    editTiers.value = tiersValid
      ? tiers.map((t) => ({ ...t, flat_fee: t.flat_fee ?? 0 }))
      : defaultTiers
  } else {
    editPricingModel.value = 'usage'
    editUnitPrice.value = value?.price_per_unit ?? feature.unitPrice
  }
  editCostPerUnit.value = value?.cost_per_unit ?? feature.costPerUnit
  editCostModel.value = value?.cost_model ?? 'none'
  editCostUnit.value = value?.cost_unit ?? 'CURRENCY'
  editBillingTiming.value = (value?.billing_timing as string) || 'IN_ARREARS'
  editResetMode.value = (value?.reset_mode === 'accumulate') ? 'accumulate' : 'reset'
  editMaxUsage.value = value?.max_usage ?? undefined
  editCreditModelId.value = feature.creditModelId || '__NONE__'
}

function startEditingPricing() {
  if (props.feature) {
    initFromFeature(props.feature)
    editErrorMessage.value = null
    isEditingPricing.value = true
  }
}

function cancelEditingPricing() {
  isEditingPricing.value = false
  editErrorMessage.value = null
}

async function savePricing() {
  if (!props.feature || !isValid.value) return

  try {
    editErrorMessage.value = null

    const value = props.feature.value as {
      usage_unit_type?: string
    } | undefined
    const unitLabelValue = value?.usage_unit_type ?? props.feature.unitLabel ?? 'unit'
    const resolvedCreditModelId = editCreditModelId.value !== '__NONE__' ? editCreditModelId.value : undefined

    if (editPricingModel.value === 'graduated') {
      await updatePlanFeatureRule({
        planId: props.planId,
        featureId: props.feature.id,
        type: 'BASE',
        value: {
          model: 'graduated',
          usage_unit_type: unitLabelValue,
          billing_timing: editBillingTiming.value,
          tiers: editTiers.value,
          ...(editCostModel.value !== 'none' && {
            cost_model: editCostModel.value,
            cost_per_unit: editCostPerUnit.value,
            cost_unit: editCostUnit.value,
          }),
          ...(editResetMode.value !== 'reset' && { reset_mode: editResetMode.value }),
          ...(editMaxUsage.value !== undefined && editMaxUsage.value > 0 && { max_usage: editMaxUsage.value })
        },
        isEnabled: true,
        creditModelId: resolvedCreditModelId
      })
    } else {
      await updatePlanFeatureRule({
        planId: props.planId,
        featureId: props.feature.id,
        type: 'BASE',
        value: {
          model: 'usage',
          price_per_unit: (resolvedCreditModelId && isHardLimit.value) ? 0 : editUnitPrice.value,
          usage_unit_type: unitLabelValue,
          billing_timing: editBillingTiming.value,
          ...(editCostModel.value !== 'none' && {
            cost_model: editCostModel.value,
            cost_per_unit: editCostPerUnit.value,
            cost_unit: editCostUnit.value,
          }),
          ...(editResetMode.value !== 'reset' && { reset_mode: editResetMode.value }),
          ...(editMaxUsage.value !== undefined && editMaxUsage.value > 0 && { max_usage: editMaxUsage.value })
        },
        isEnabled: true,
        creditModelId: resolvedCreditModelId
      })
    }

    queryClient.invalidateQueries({ queryKey: ['plan-features', props.planId] })

    toast({ title: 'Success', description: 'Feature pricing updated' })
    emit('update:success')
    isEditingPricing.value = false
    emit('update:visible', false)
  } catch (error) {
    const parsedError = parseApiError(error)
    editErrorMessage.value = parsedError.message
    toast({ title: 'Error', description: parsedError.message, variant: 'destructive' })
  }
}

function handleClose(open: boolean) {
  emit('update:visible', open)
}

function onRemove() {
  if (props.feature) {
    emit('remove', props.feature.id)
  }
}


function formatTierBound(upTo: number | 'inf'): string {
  if (upTo === 'inf') return '\u221E'
  return upTo.toLocaleString()
}

</script>
