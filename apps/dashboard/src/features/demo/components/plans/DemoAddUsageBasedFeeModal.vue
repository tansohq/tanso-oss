<template>
  <Dialog :open="visible" @update:open="emit('update:visible', $event)">
    <DialogContent class="sm:max-w-[800px] max-h-[90vh] overflow-y-auto">
      <DialogHeader>
        <DialogTitle>{{ editingFee ? 'Edit Feature' : 'Add Feature' }}</DialogTitle>
        <DialogDescription> Configure how this feature is priced </DialogDescription>
      </DialogHeader>

      <div class="flex flex-col gap-4 py-4">
        <!-- Feature Name -->
        <div class="flex flex-col gap-1.5">
          <Label for="featureName">Feature Name *</Label>
          <Input id="featureName" v-model="componentName" placeholder="e.g., API Requests" />
        </div>

        <!-- Linked Feature -->
        <div class="flex flex-col gap-1.5">
          <Label>Linked Feature *</Label>
          <Select v-model="selectedFeatureId">
            <SelectTrigger>
              <SelectValue placeholder="Select a feature..." />
            </SelectTrigger>
            <SelectContent>
              <SelectItem
                v-for="feature in availableFeatures"
                :key="feature.id"
                :value="feature.id"
              >
                <div class="flex items-center gap-2">
                  <span>{{ feature.name }}</span>
                  <span class="text-xs text-muted-foreground font-mono">{{ feature.key }}</span>
                </div>
              </SelectItem>
            </SelectContent>
          </Select>
        </div>

        <Separator />

        <!-- Pricing Model Selection -->
        <div class="flex flex-col gap-3">
          <Label>Pricing Model *</Label>

          <!-- Common Models -->
          <div class="space-y-2">
            <button
              v-for="model in commonPricingModels"
              :key="model.value"
              :class="[
                'w-full flex items-start gap-3 p-3 rounded-lg border-2 transition-colors text-left',
                priceModel === model.value
                  ? 'border-primary bg-primary/5'
                  : 'border-border hover:border-primary/50'
              ]"
              @click="priceModel = model.value"
            >
              <div
                class="flex-shrink-0 w-8 h-8 rounded-md bg-muted flex items-center justify-center"
              >
                <component :is="model.icon" class="h-4 w-4 text-muted-foreground" />
              </div>
              <div class="flex-1 min-w-0">
                <div class="font-medium text-sm">{{ model.label }}</div>
                <div class="text-xs text-muted-foreground mt-0.5">{{ model.description }}</div>
                <div class="text-xs text-muted-foreground/70 mt-1 font-mono">
                  {{ model.example }}
                </div>
              </div>
            </button>
          </div>

          <!-- Advanced Models (Collapsible) -->
          <button
            type="button"
            class="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors py-1"
            @click="showAdvancedModels = !showAdvancedModels"
          >
            <ChevronRight
              :class="['h-4 w-4 transition-transform', showAdvancedModels && 'rotate-90']"
            />
            <span>{{ showAdvancedModels ? 'Hide' : 'Show' }} advanced pricing models</span>
          </button>

          <div v-if="showAdvancedModels" class="space-y-2">
            <button
              v-for="model in advancedPricingModels"
              :key="model.value"
              :class="[
                'w-full flex items-start gap-3 p-3 rounded-lg border-2 transition-colors text-left',
                priceModel === model.value
                  ? 'border-primary bg-primary/5'
                  : 'border-border hover:border-primary/50'
              ]"
              @click="priceModel = model.value"
            >
              <div
                class="flex-shrink-0 w-8 h-8 rounded-md bg-muted flex items-center justify-center"
              >
                <component :is="model.icon" class="h-4 w-4 text-muted-foreground" />
              </div>
              <div class="flex-1 min-w-0">
                <div class="font-medium text-sm">{{ model.label }}</div>
                <div class="text-xs text-muted-foreground mt-0.5">{{ model.description }}</div>
                <div class="text-xs text-muted-foreground/70 mt-1 font-mono">
                  {{ model.example }}
                </div>
              </div>
            </button>
          </div>
        </div>

        <Separator />

        <!-- Pricing Configuration -->
        <div class="flex flex-col gap-3">
          <!-- Flat Rate -->
          <div v-if="priceModel === 'fixed'" class="flex flex-col gap-3">
            <Label>Amount *</Label>
            <div class="flex items-center gap-2">
              <span class="text-sm">$</span>
              <Input
                v-model.number="flatRateAmount"
                type="number"
                step="0.01"
                min="0"
                placeholder="49.00"
                class="w-32 font-mono"
              />
              <span class="text-sm text-muted-foreground">per billing cycle</span>
            </div>
          </div>

          <!-- Per Unit -->
          <div v-else-if="priceModel === 'per_unit'" class="flex flex-col gap-3">
            <Label>Unit Price *</Label>
            <div class="flex items-center gap-2">
              <span class="text-sm">$</span>
              <Input
                v-model.number="unitPrice"
                type="number"
                step="0.0001"
                min="0"
                placeholder="0.001"
                class="w-32 font-mono"
              />
              <span class="text-sm text-muted-foreground">per unit</span>
            </div>
            <DemoPricingCalculationPreview
              price-model="per_unit"
              :unit-price="unitPrice"
              :sample-usage="150"
            />
          </div>

          <!-- Graduated -->
          <div v-else-if="priceModel === 'graduated'">
            <DemoTierEditor v-model="tiers" mode="graduated" :sample-usage="150" />
          </div>

          <!-- Volume -->
          <div v-else-if="priceModel === 'volume'">
            <DemoTierEditor v-model="tiers" mode="volume" :sample-usage="150" />
          </div>

          <!-- Package -->
          <div v-else-if="priceModel === 'package'" class="flex flex-col gap-3">
            <div class="flex flex-col gap-1.5">
              <Label>Package Size *</Label>
              <div class="flex items-center gap-2">
                <Input
                  v-model.number="packageSize"
                  type="number"
                  min="1"
                  placeholder="1000"
                  class="w-32 font-mono"
                />
                <span class="text-sm text-muted-foreground">units per package</span>
              </div>
            </div>
            <div class="flex flex-col gap-1.5">
              <Label>Package Price *</Label>
              <div class="flex items-center gap-2">
                <span class="text-sm">$</span>
                <Input
                  v-model.number="packagePrice"
                  type="number"
                  step="0.01"
                  min="0"
                  placeholder="25.00"
                  class="w-32 font-mono"
                />
                <span class="text-sm text-muted-foreground">per package</span>
              </div>
            </div>
            <DemoPricingCalculationPreview
              price-model="package"
              :package-size="packageSize"
              :package-price="packagePrice"
              :sample-usage="2500"
            />
          </div>

          <!-- Matrix -->
          <div v-else-if="priceModel === 'matrix'">
            <DemoMatrixEditor
              v-model="matrixDimensions"
              :matrix2-d="matrix2D"
              :mode="matrixMode"
              @update:matrix2-d="matrix2D = $event"
              @update:mode="matrixMode = $event"
            />
          </div>

          <!-- Percentage + Fixed (Fintech) -->
          <div v-else-if="priceModel === 'percentage_plus_fixed'" class="flex flex-col gap-3">
            <div class="flex flex-col gap-1.5">
              <Label>Percentage Rate *</Label>
              <div class="flex items-center gap-2">
                <Input
                  v-model.number="percentageRate"
                  type="number"
                  step="0.1"
                  min="0"
                  max="100"
                  placeholder="2.9"
                  class="w-24 font-mono"
                />
                <span class="text-sm text-muted-foreground">% of transaction</span>
              </div>
            </div>
            <div class="flex flex-col gap-1.5">
              <Label>Fixed Fee *</Label>
              <div class="flex items-center gap-2">
                <span class="text-sm">$</span>
                <Input
                  v-model.number="fixedFeePerUnit"
                  type="number"
                  step="0.01"
                  min="0"
                  placeholder="0.30"
                  class="w-24 font-mono"
                />
                <span class="text-sm text-muted-foreground">per transaction</span>
              </div>
            </div>
            <!-- Preview calculation -->
            <div class="p-4 rounded-lg bg-muted/30 border">
              <div class="text-sm font-medium mb-2">Example: $100.00 transaction</div>
              <div class="space-y-1 text-sm">
                <div class="flex justify-between text-muted-foreground">
                  <span>{{ percentageRate }}% of $100.00</span>
                  <span class="font-mono">${{ ((100 * percentageRate) / 100).toFixed(2) }}</span>
                </div>
                <div class="flex justify-between text-muted-foreground">
                  <span>Fixed fee</span>
                  <span class="font-mono">${{ fixedFeePerUnit.toFixed(2) }}</span>
                </div>
                <Separator class="my-2" />
                <div class="flex justify-between font-medium">
                  <span>Total fee</span>
                  <span class="font-mono"
                    >${{ ((100 * percentageRate) / 100 + fixedFeePerUnit).toFixed(2) }}</span
                  >
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="handleClose">Cancel</Button>
        <Button @click="handleSave" :disabled="!canSave">
          {{ editingFee ? 'Save Changes' : 'Add Fee' }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import {
  DollarSign,
  TrendingDown,
  Layers,
  Package,
  Grid3X3,
  Percent,
  ChevronRight,
  Receipt
} from 'lucide-vue-next'
import { useDemoState } from '../../composables/useDemoState'
import { toast } from '@/components/ui/toast/use-toast'
import type {
  UsageBasedFee,
  PriceModel,
  PricingTier,
  MatrixPriceDimension,
  Matrix2DPricing
} from '../../types'
import DemoTierEditor from './DemoTierEditor.vue'
import DemoMatrixEditor from './DemoMatrixEditor.vue'
import DemoPricingCalculationPreview from './DemoPricingCalculationPreview.vue'

const props = defineProps<{
  visible: boolean
  planId: string
  version: number
  editingFee?: UsageBasedFee
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const { featuresData, addUsageBasedFee, updateUsageBasedFee } = useDemoState()

// All features are now available (types removed - pricing determined at plan level)
const availableFeatures = computed(() => featuresData.value)

// Common pricing models (most frequently used)
const commonPricingModels = [
  {
    value: 'fixed' as PriceModel,
    label: 'Flat Rate',
    description: 'Fixed recurring charge',
    example: '$49/month or $499/year',
    icon: Receipt
  },
  {
    value: 'per_unit' as PriceModel,
    label: 'Per Unit',
    description: 'Same price for every unit',
    example: '100 units × $0.10 = $10.00',
    icon: DollarSign
  },
  {
    value: 'graduated' as PriceModel,
    label: 'Graduated',
    description: 'Each tier charged at its own rate',
    example: '5 @ $0.10 + 5 @ $0.08 = $0.90',
    icon: TrendingDown
  },
  {
    value: 'volume' as PriceModel,
    label: 'Volume',
    description: 'All units priced at the tier you reach',
    example: '10 units in tier 2 → all 10 @ $0.08 = $0.80',
    icon: Layers
  }
]

// Advanced pricing models (less common, specialized use cases)
const advancedPricingModels = [
  {
    value: 'package' as PriceModel,
    label: 'Package',
    description: 'Pay for blocks of units',
    example: '1-1000 units = $25, 1001-2000 = $50',
    icon: Package
  },
  {
    value: 'matrix' as PriceModel,
    label: 'Matrix',
    description: 'Price varies by multiple factors',
    example: 'model × region × token type',
    icon: Grid3X3
  },
  {
    value: 'percentage_plus_fixed' as PriceModel,
    label: '% + Fixed',
    description: 'Percentage of transaction + flat fee',
    example: '2.9% + $0.30 per transaction',
    icon: Percent
  }
]

// Toggle for advanced models visibility
const showAdvancedModels = ref(false)

// Form state
const componentName = ref('')
const selectedFeatureId = ref('')
const priceModel = ref<PriceModel>('fixed')

// Flat rate
const flatRateAmount = ref(49)

// Per unit
const unitPrice = ref(0.001)

// Graduated/Volume tiers
const tiers = ref<PricingTier[]>([
  { firstUnit: 1, lastUnit: 100, unitPrice: 0.1 },
  { firstUnit: 101, lastUnit: 500, unitPrice: 0.08 },
  { firstUnit: 501, lastUnit: null, unitPrice: 0.05 }
])

// Package
const packageSize = ref(1000)
const packagePrice = ref(25)

// Matrix (1D)
const matrixDimensions = ref<MatrixPriceDimension[]>([
  {
    key: 'model_type',
    values: [
      { value: 'gpt-4o', unitPrice: 0.03 },
      { value: 'gpt-4o-mini', unitPrice: 0.0015 }
    ],
    defaultPrice: 0.01
  }
])

// Matrix mode and 2D matrix
const matrixMode = ref<'1d' | '2d'>('1d')
const matrix2D = ref<Matrix2DPricing>({
  dimension1: { key: 'context_window', values: ['8k', '32k', '128k'] },
  dimension2: { key: 'io_direction', values: ['input', 'output'] },
  prices: {
    '8k': { input: 0.03, output: 0.06 },
    '32k': { input: 0.06, output: 0.12 },
    '128k': { input: 0.12, output: 0.24 }
  },
  defaultPrice: 0.01
})

// Percentage + Fixed (fintech)
const percentageRate = ref(2.9) // 2.9%
const fixedFeePerUnit = ref(0.3) // $0.30

// Validation
const canSave = computed(() => {
  if (!componentName.value.trim() || !selectedFeatureId.value) return false

  switch (priceModel.value) {
    case 'fixed':
      return flatRateAmount.value > 0
    case 'per_unit':
      return unitPrice.value > 0
    case 'graduated':
    case 'volume':
      return tiers.value.length > 0 && tiers.value.every((t) => t.unitPrice >= 0)
    case 'package':
      return packageSize.value > 0 && packagePrice.value > 0
    case 'matrix':
      if (matrixMode.value === '2d') {
        return (
          matrix2D.value.dimension1.key.trim() !== '' &&
          matrix2D.value.dimension2.key.trim() !== '' &&
          matrix2D.value.dimension1.values.length > 0 &&
          matrix2D.value.dimension2.values.length > 0
        )
      }
      return (
        matrixDimensions.value.length > 0 &&
        matrixDimensions.value.every((d) => d.key.trim() && d.values.length > 0)
      )
    case 'percentage_plus_fixed':
      return percentageRate.value >= 0 && fixedFeePerUnit.value >= 0
    default:
      return false
  }
})

// Get feature name helper
function getFeatureName(featureId: string): string {
  const feature = featuresData.value.find((f) => f.id === featureId)
  return feature?.name || featureId
}

// Check if a pricing model is advanced
const advancedModelValues = advancedPricingModels.map((m) => m.value)
function isAdvancedModel(model: PriceModel): boolean {
  return advancedModelValues.includes(model)
}

// Load editing fee data
watch(
  () => props.visible,
  (visible) => {
    if (visible && props.editingFee) {
      componentName.value = props.editingFee.component
      selectedFeatureId.value = props.editingFee.featureId || ''
      priceModel.value = props.editingFee.priceModel

      // Auto-expand advanced section if editing an advanced model
      if (isAdvancedModel(props.editingFee.priceModel)) {
        showAdvancedModels.value = true
      }

      if (props.editingFee.unitPrice !== undefined) {
        if (props.editingFee.priceModel === 'fixed') {
          flatRateAmount.value = props.editingFee.unitPrice
        } else {
          unitPrice.value = props.editingFee.unitPrice
        }
      }
      if (props.editingFee.tiers) {
        tiers.value = [...props.editingFee.tiers]
      }
      if (props.editingFee.packageSize !== undefined) {
        packageSize.value = props.editingFee.packageSize
      }
      if (props.editingFee.packagePrice !== undefined) {
        packagePrice.value = props.editingFee.packagePrice
      }
      if (props.editingFee.matrixDimensions) {
        matrixDimensions.value = JSON.parse(JSON.stringify(props.editingFee.matrixDimensions))
        matrixMode.value = '1d'
      }
      if (props.editingFee.matrix2D) {
        matrix2D.value = JSON.parse(JSON.stringify(props.editingFee.matrix2D))
        matrixMode.value = '2d'
      }
      if (props.editingFee.percentageRate !== undefined) {
        percentageRate.value = props.editingFee.percentageRate * 100 // Convert from decimal to percentage
      }
      if (props.editingFee.fixedFeePerUnit !== undefined) {
        fixedFeePerUnit.value = props.editingFee.fixedFeePerUnit
      }
    } else if (visible) {
      // Reset form for new fee
      resetForm()
    }
  }
)

function resetForm() {
  componentName.value = ''
  selectedFeatureId.value = ''
  priceModel.value = 'fixed'
  showAdvancedModels.value = false
  flatRateAmount.value = 49
  unitPrice.value = 0.001
  tiers.value = [
    { firstUnit: 1, lastUnit: 100, unitPrice: 0.1 },
    { firstUnit: 101, lastUnit: 500, unitPrice: 0.08 },
    { firstUnit: 501, lastUnit: null, unitPrice: 0.05 }
  ]
  packageSize.value = 1000
  packagePrice.value = 25
  matrixDimensions.value = [
    {
      key: 'model_type',
      values: [
        { value: 'gpt-4o', unitPrice: 0.03 },
        { value: 'gpt-4o-mini', unitPrice: 0.0015 }
      ],
      defaultPrice: 0.01
    }
  ]
  matrixMode.value = '1d'
  matrix2D.value = {
    dimension1: { key: 'context_window', values: ['8k', '32k', '128k'] },
    dimension2: { key: 'io_direction', values: ['input', 'output'] },
    prices: {
      '8k': { input: 0.03, output: 0.06 },
      '32k': { input: 0.06, output: 0.12 },
      '128k': { input: 0.12, output: 0.24 }
    },
    defaultPrice: 0.01
  }
  percentageRate.value = 2.9
  fixedFeePerUnit.value = 0.3
}

function handleClose() {
  resetForm()
  emit('update:visible', false)
}

function handleSave() {
  if (!canSave.value) return

  const feeData: Omit<UsageBasedFee, 'id' | 'billingCycle'> = {
    component: componentName.value.trim(),
    featureId: selectedFeatureId.value,
    featureName: getFeatureName(selectedFeatureId.value),
    priceModel: priceModel.value
  }

  // Add pricing model specific data
  switch (priceModel.value) {
    case 'fixed':
      feeData.unitPrice = flatRateAmount.value
      break
    case 'per_unit':
      feeData.unitPrice = unitPrice.value
      break
    case 'graduated':
    case 'volume':
      feeData.tiers = [...tiers.value]
      break
    case 'package':
      feeData.packageSize = packageSize.value
      feeData.packagePrice = packagePrice.value
      break
    case 'matrix':
      if (matrixMode.value === '2d') {
        feeData.matrix2D = JSON.parse(JSON.stringify(matrix2D.value))
        feeData.defaultUnitPrice = matrix2D.value.defaultPrice
      } else {
        feeData.matrixDimensions = JSON.parse(JSON.stringify(matrixDimensions.value))
        feeData.defaultUnitPrice = matrixDimensions.value[0]?.defaultPrice || 0
      }
      break
    case 'percentage_plus_fixed':
      feeData.percentageRate = percentageRate.value / 100 // Convert from percentage to decimal
      feeData.fixedFeePerUnit = fixedFeePerUnit.value
      break
  }

  if (props.editingFee) {
    updateUsageBasedFee(
      props.planId,
      props.version,
      props.editingFee.id,
      feeData as Omit<UsageBasedFee, 'id'>
    )
    toast({
      title: 'Feature updated',
      description: `Feature "${componentName.value}" has been updated.`
    })
  } else {
    addUsageBasedFee(props.planId, props.version, feeData as Omit<UsageBasedFee, 'id'>)
    toast({
      title: 'Feature added',
      description: `Feature "${componentName.value}" has been added to the plan.`
    })
  }

  handleClose()
}
</script>
