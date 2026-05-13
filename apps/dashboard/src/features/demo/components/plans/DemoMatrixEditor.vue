<template>
  <div class="space-y-4">
    <!-- Mode Toggle -->
    <div class="flex items-center gap-2">
      <Label class="text-xs text-muted-foreground">Matrix Type:</Label>
      <div class="flex gap-1">
        <Button
          :variant="matrixMode === '1d' ? 'default' : 'outline'"
          size="sm"
          class="h-7 text-xs"
          @click="handleModeChange('1d')"
        >
          1D Lookup
        </Button>
        <Button
          :variant="matrixMode === '2d' ? 'default' : 'outline'"
          size="sm"
          class="h-7 text-xs"
          @click="handleModeChange('2d')"
        >
          2D Grid
        </Button>
      </div>
    </div>

    <!-- 1D Mode -->
    <template v-if="matrixMode === '1d'">
      <div class="flex items-center justify-between">
        <Label class="text-xs text-muted-foreground">
          1D Matrix Pricing (price varies by single dimension)
        </Label>
      </div>

      <!-- Dimensions -->
      <div class="space-y-4">
        <div
          v-for="(dimension, dimIndex) in modelValue"
          :key="dimIndex"
          class="p-3 rounded-lg border bg-muted/20"
        >
          <div class="flex items-center justify-between mb-3">
            <div class="flex items-center gap-2">
              <Grid3X3 class="h-4 w-4 text-muted-foreground" />
              <Input
                :model-value="dimension.key"
                @update:model-value="updateDimensionKey(dimIndex, $event)"
                placeholder="e.g., model_type"
                class="h-8 w-40 font-mono text-sm"
              />
            </div>
            <Button
              v-if="modelValue.length > 1"
              variant="ghost"
              size="sm"
              class="h-7 w-7 p-0"
              @click="removeDimension(dimIndex)"
            >
              <X class="h-3.5 w-3.5 text-muted-foreground hover:text-destructive" />
            </Button>
          </div>

          <!-- Dimension Values -->
          <div class="space-y-2">
            <div
              v-for="(valueItem, valIndex) in dimension.values"
              :key="valIndex"
              class="flex items-center gap-2"
            >
              <Input
                :model-value="valueItem.value"
                @update:model-value="updateDimensionValue(dimIndex, valIndex, 'value', $event)"
                placeholder="e.g., gpt-4o"
                class="h-7 w-32 text-sm"
              />
              <span class="text-xs text-muted-foreground">@</span>
              <div class="flex items-center gap-1">
                <span class="text-sm">$</span>
                <Input
                  :model-value="valueItem.unitPrice"
                  @update:model-value="
                    updateDimensionValue(dimIndex, valIndex, 'unitPrice', $event)
                  "
                  type="number"
                  step="0.0001"
                  min="0"
                  placeholder="0.00"
                  class="h-7 w-24 font-mono text-sm"
                />
              </div>
              <span class="text-xs text-muted-foreground">/ unit</span>
              <Button
                v-if="dimension.values.length > 1"
                variant="ghost"
                size="sm"
                class="h-7 w-7 p-0"
                @click="removeValue(dimIndex, valIndex)"
              >
                <X class="h-3 w-3 text-muted-foreground hover:text-destructive" />
              </Button>
            </div>
          </div>

          <!-- Add Value Button -->
          <Button
            variant="ghost"
            size="sm"
            class="mt-2 h-7 text-xs"
            :disabled="dimension.values.length >= 10"
            @click="addValue(dimIndex)"
          >
            <Plus class="h-3 w-3 mr-1" />
            Add Value
          </Button>

          <!-- Default Price for this dimension -->
          <div class="flex items-center gap-2 mt-3 pt-3 border-t">
            <span class="text-xs text-muted-foreground">Default price:</span>
            <span class="text-sm">$</span>
            <Input
              :model-value="dimension.defaultPrice"
              @update:model-value="updateDefaultPrice(dimIndex, $event)"
              type="number"
              step="0.0001"
              min="0"
              placeholder="0.00"
              class="h-7 w-24 font-mono text-sm"
            />
            <span class="text-xs text-muted-foreground">/ unit (fallback)</span>
          </div>
        </div>
      </div>

      <!-- Add Dimension Button -->
      <Button
        variant="outline"
        size="sm"
        class="w-full"
        :disabled="modelValue.length >= 3"
        @click="addDimension"
      >
        <Plus class="h-3.5 w-3.5 mr-1.5" />
        Add Dimension
      </Button>

      <!-- Matrix Preview -->
      <div
        v-if="modelValue.length > 0 && modelValue[0].values.length > 0"
        class="mt-3 p-3 rounded-md bg-purple-50 border border-purple-100"
      >
        <div class="flex items-start gap-2">
          <Grid3X3 class="h-4 w-4 text-purple-500 mt-0.5 flex-shrink-0" />
          <div class="text-sm">
            <p class="font-medium text-purple-700 mb-2">Price Matrix Preview</p>
            <div class="overflow-x-auto">
              <table class="text-xs border-collapse">
                <thead>
                  <tr>
                    <th
                      class="p-1.5 text-left text-purple-600 font-medium border-b border-purple-200"
                    >
                      {{ modelValue[0]?.key || 'Dimension' }}
                    </th>
                    <th
                      class="p-1.5 text-right text-purple-600 font-medium border-b border-purple-200"
                    >
                      Price
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(val, i) in modelValue[0]?.values || []" :key="i">
                    <td class="p-1.5 text-purple-700 font-mono">{{ val.value || '(empty)' }}</td>
                    <td class="p-1.5 text-right text-purple-600 font-mono">
                      ${{ val.unitPrice.toFixed(4) }}
                    </td>
                  </tr>
                  <tr class="border-t border-purple-200">
                    <td class="p-1.5 text-purple-500 italic">other</td>
                    <td class="p-1.5 text-right text-purple-500 font-mono">
                      ${{ (modelValue[0]?.defaultPrice || 0).toFixed(4) }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 2D Mode -->
    <template v-else>
      <div class="flex items-center justify-between">
        <Label class="text-xs text-muted-foreground">
          2D Matrix Pricing (price varies by two dimensions, e.g., context × I/O)
        </Label>
      </div>

      <!-- Dimension 1 (Rows) -->
      <div class="p-3 rounded-lg border bg-muted/20">
        <div class="flex items-center gap-2 mb-3">
          <span class="text-xs font-medium text-muted-foreground w-16">Rows:</span>
          <Input
            :model-value="matrix2DValue.dimension1.key"
            @update:model-value="update2DDimensionKey(1, $event)"
            placeholder="e.g., context_window"
            class="h-8 w-40 font-mono text-sm"
          />
        </div>
        <div class="flex flex-wrap gap-2">
          <div
            v-for="(val, valIndex) in matrix2DValue.dimension1.values"
            :key="valIndex"
            class="flex items-center gap-1"
          >
            <Input
              :model-value="val"
              @update:model-value="update2DDimensionValue(1, valIndex, $event)"
              placeholder="e.g., 8k"
              class="h-7 w-20 text-sm"
            />
            <Button
              v-if="matrix2DValue.dimension1.values.length > 1"
              variant="ghost"
              size="sm"
              class="h-7 w-7 p-0"
              @click="remove2DValue(1, valIndex)"
            >
              <X class="h-3 w-3 text-muted-foreground hover:text-destructive" />
            </Button>
          </div>
          <Button
            variant="ghost"
            size="sm"
            class="h-7 text-xs"
            :disabled="matrix2DValue.dimension1.values.length >= 6"
            @click="add2DValue(1)"
          >
            <Plus class="h-3 w-3 mr-1" />
            Add
          </Button>
        </div>
      </div>

      <!-- Dimension 2 (Columns) -->
      <div class="p-3 rounded-lg border bg-muted/20">
        <div class="flex items-center gap-2 mb-3">
          <span class="text-xs font-medium text-muted-foreground w-16">Columns:</span>
          <Input
            :model-value="matrix2DValue.dimension2.key"
            @update:model-value="update2DDimensionKey(2, $event)"
            placeholder="e.g., io_direction"
            class="h-8 w-40 font-mono text-sm"
          />
        </div>
        <div class="flex flex-wrap gap-2">
          <div
            v-for="(val, valIndex) in matrix2DValue.dimension2.values"
            :key="valIndex"
            class="flex items-center gap-1"
          >
            <Input
              :model-value="val"
              @update:model-value="update2DDimensionValue(2, valIndex, $event)"
              placeholder="e.g., input"
              class="h-7 w-20 text-sm"
            />
            <Button
              v-if="matrix2DValue.dimension2.values.length > 1"
              variant="ghost"
              size="sm"
              class="h-7 w-7 p-0"
              @click="remove2DValue(2, valIndex)"
            >
              <X class="h-3 w-3 text-muted-foreground hover:text-destructive" />
            </Button>
          </div>
          <Button
            variant="ghost"
            size="sm"
            class="h-7 text-xs"
            :disabled="matrix2DValue.dimension2.values.length >= 6"
            @click="add2DValue(2)"
          >
            <Plus class="h-3 w-3 mr-1" />
            Add
          </Button>
        </div>
      </div>

      <!-- 2D Price Grid -->
      <div
        v-if="
          matrix2DValue.dimension1.values.length > 0 && matrix2DValue.dimension2.values.length > 0
        "
        class="p-3 rounded-lg border bg-muted/20"
      >
        <Label class="text-xs text-muted-foreground mb-3 block">Price Grid ($ per unit)</Label>
        <div class="overflow-x-auto">
          <table class="text-sm border-collapse w-full">
            <thead>
              <tr>
                <th
                  class="p-2 text-left text-muted-foreground font-medium border-b bg-muted/30 sticky left-0"
                >
                  {{ matrix2DValue.dimension1.key || 'Rows' }} \
                  {{ matrix2DValue.dimension2.key || 'Cols' }}
                </th>
                <th
                  v-for="col in matrix2DValue.dimension2.values"
                  :key="col"
                  class="p-2 text-center text-muted-foreground font-medium border-b bg-muted/30 min-w-[80px]"
                >
                  {{ col || '(empty)' }}
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in matrix2DValue.dimension1.values" :key="row">
                <td
                  class="p-2 font-medium text-muted-foreground border-b bg-muted/30 sticky left-0"
                >
                  {{ row || '(empty)' }}
                </td>
                <td v-for="col in matrix2DValue.dimension2.values" :key="col" class="p-1 border-b">
                  <div class="flex items-center justify-center gap-1">
                    <span class="text-xs text-muted-foreground">$</span>
                    <Input
                      :model-value="get2DPrice(row, col)"
                      @update:model-value="set2DPrice(row, col, $event)"
                      type="number"
                      step="0.0001"
                      min="0"
                      placeholder="0.00"
                      class="h-7 w-20 font-mono text-xs text-center"
                    />
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Default Price -->
      <div class="flex items-center gap-2 p-3 rounded-lg border bg-muted/20">
        <span class="text-xs text-muted-foreground">Default price (fallback):</span>
        <span class="text-sm">$</span>
        <Input
          :model-value="matrix2DValue.defaultPrice"
          @update:model-value="update2DDefaultPrice($event)"
          type="number"
          step="0.0001"
          min="0"
          placeholder="0.00"
          class="h-7 w-24 font-mono text-sm"
        />
        <span class="text-xs text-muted-foreground">/ unit</span>
      </div>

      <!-- 2D Preview -->
      <div
        v-if="
          matrix2DValue.dimension1.values.length > 0 && matrix2DValue.dimension2.values.length > 0
        "
        class="p-3 rounded-md bg-purple-50 border border-purple-100"
      >
        <div class="flex items-start gap-2">
          <Grid3X3 class="h-4 w-4 text-purple-500 mt-0.5 flex-shrink-0" />
          <div class="text-sm w-full">
            <p class="font-medium text-purple-700 mb-2">2D Price Matrix Preview</p>
            <div class="overflow-x-auto">
              <table class="text-xs border-collapse w-full">
                <thead>
                  <tr>
                    <th
                      class="p-1.5 text-left text-purple-600 font-medium border-b border-purple-200"
                    ></th>
                    <th
                      v-for="col in matrix2DValue.dimension2.values"
                      :key="col"
                      class="p-1.5 text-center text-purple-600 font-medium border-b border-purple-200"
                    >
                      {{ col }}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in matrix2DValue.dimension1.values" :key="row">
                    <td class="p-1.5 text-purple-700 font-medium">{{ row }}</td>
                    <td
                      v-for="col in matrix2DValue.dimension2.values"
                      :key="col"
                      class="p-1.5 text-center text-purple-600 font-mono"
                    >
                      ${{ (get2DPrice(row, col) || 0).toFixed(4) }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Plus, X, Grid3X3 } from 'lucide-vue-next'
import type { MatrixPriceDimension, Matrix2DPricing } from '../../types'

const props = defineProps<{
  modelValue: MatrixPriceDimension[]
  matrix2D?: Matrix2DPricing
  mode?: '1d' | '2d'
}>()

const emit = defineEmits<{
  'update:modelValue': [value: MatrixPriceDimension[]]
  'update:matrix2D': [value: Matrix2DPricing]
  'update:mode': [value: '1d' | '2d']
}>()

// Internal mode state (synced with prop)
const matrixMode = ref<'1d' | '2d'>(props.mode || '1d')

// Internal 2D state
const matrix2DValue = ref<Matrix2DPricing>(
  props.matrix2D || {
    dimension1: { key: 'context_window', values: ['8k', '32k', '128k'] },
    dimension2: { key: 'io_direction', values: ['input', 'output'] },
    prices: {
      '8k': { input: 0.03, output: 0.06 },
      '32k': { input: 0.06, output: 0.12 },
      '128k': { input: 0.12, output: 0.24 }
    },
    defaultPrice: 0.01
  }
)

// Sync mode from prop
watch(
  () => props.mode,
  (newMode) => {
    if (newMode) matrixMode.value = newMode
  }
)

// Sync matrix2D from prop
watch(
  () => props.matrix2D,
  (newMatrix2D) => {
    if (newMatrix2D) {
      matrix2DValue.value = JSON.parse(JSON.stringify(newMatrix2D))
    }
  },
  { deep: true }
)

function handleModeChange(mode: '1d' | '2d') {
  matrixMode.value = mode
  emit('update:mode', mode)
}

// 1D Mode functions
function updateDimensionKey(dimIndex: number, value: string | number) {
  const newDimensions = [...props.modelValue]
  newDimensions[dimIndex] = { ...newDimensions[dimIndex], key: String(value) }
  emit('update:modelValue', newDimensions)
}

function updateDimensionValue(
  dimIndex: number,
  valIndex: number,
  field: 'value' | 'unitPrice',
  value: string | number
) {
  const newDimensions = [...props.modelValue]
  const newValues = [...newDimensions[dimIndex].values]

  if (field === 'value') {
    newValues[valIndex] = { ...newValues[valIndex], value: String(value) }
  } else {
    const numValue = typeof value === 'string' ? parseFloat(value) : value
    newValues[valIndex] = { ...newValues[valIndex], unitPrice: numValue || 0 }
  }

  newDimensions[dimIndex] = { ...newDimensions[dimIndex], values: newValues }
  emit('update:modelValue', newDimensions)
}

function updateDefaultPrice(dimIndex: number, value: string | number) {
  const numValue = typeof value === 'string' ? parseFloat(value) : value
  const newDimensions = [...props.modelValue]
  newDimensions[dimIndex] = { ...newDimensions[dimIndex], defaultPrice: numValue || 0 }
  emit('update:modelValue', newDimensions)
}

function addValue(dimIndex: number) {
  const newDimensions = [...props.modelValue]
  const newValues = [...newDimensions[dimIndex].values, { value: '', unitPrice: 0 }]
  newDimensions[dimIndex] = { ...newDimensions[dimIndex], values: newValues }
  emit('update:modelValue', newDimensions)
}

function removeValue(dimIndex: number, valIndex: number) {
  const newDimensions = [...props.modelValue]
  const newValues = newDimensions[dimIndex].values.filter((_, i) => i !== valIndex)
  newDimensions[dimIndex] = { ...newDimensions[dimIndex], values: newValues }
  emit('update:modelValue', newDimensions)
}

function addDimension() {
  emit('update:modelValue', [
    ...props.modelValue,
    {
      key: '',
      values: [{ value: '', unitPrice: 0 }],
      defaultPrice: 0
    }
  ])
}

function removeDimension(dimIndex: number) {
  emit(
    'update:modelValue',
    props.modelValue.filter((_, i) => i !== dimIndex)
  )
}

// 2D Mode functions
function update2DDimensionKey(dim: 1 | 2, value: string | number) {
  const newMatrix = { ...matrix2DValue.value }
  if (dim === 1) {
    newMatrix.dimension1 = { ...newMatrix.dimension1, key: String(value) }
  } else {
    newMatrix.dimension2 = { ...newMatrix.dimension2, key: String(value) }
  }
  matrix2DValue.value = newMatrix
  emit('update:matrix2D', newMatrix)
}

function update2DDimensionValue(dim: 1 | 2, valIndex: number, value: string | number) {
  const newMatrix = { ...matrix2DValue.value }
  const dimension = dim === 1 ? 'dimension1' : 'dimension2'
  const oldValue = newMatrix[dimension].values[valIndex]
  const newValue = String(value)

  const newValues = [...newMatrix[dimension].values]
  newValues[valIndex] = newValue
  newMatrix[dimension] = { ...newMatrix[dimension], values: newValues }

  // Update prices keys if dimension1 value changed
  if (dim === 1 && oldValue !== newValue) {
    const newPrices: Record<string, Record<string, number>> = {}
    for (const key of Object.keys(newMatrix.prices)) {
      if (key === oldValue) {
        newPrices[newValue] = newMatrix.prices[key]
      } else {
        newPrices[key] = newMatrix.prices[key]
      }
    }
    newMatrix.prices = newPrices
  }

  // Update prices nested keys if dimension2 value changed
  if (dim === 2 && oldValue !== newValue) {
    const newPrices: Record<string, Record<string, number>> = {}
    for (const [rowKey, rowPrices] of Object.entries(newMatrix.prices)) {
      newPrices[rowKey] = {}
      for (const [colKey, price] of Object.entries(rowPrices)) {
        if (colKey === oldValue) {
          newPrices[rowKey][newValue] = price
        } else {
          newPrices[rowKey][colKey] = price
        }
      }
    }
    newMatrix.prices = newPrices
  }

  matrix2DValue.value = newMatrix
  emit('update:matrix2D', newMatrix)
}

function add2DValue(dim: 1 | 2) {
  const newMatrix = { ...matrix2DValue.value }
  const dimension = dim === 1 ? 'dimension1' : 'dimension2'
  const newValues = [...newMatrix[dimension].values, '']
  newMatrix[dimension] = { ...newMatrix[dimension], values: newValues }
  matrix2DValue.value = newMatrix
  emit('update:matrix2D', newMatrix)
}

function remove2DValue(dim: 1 | 2, valIndex: number) {
  const newMatrix = { ...matrix2DValue.value }
  const dimension = dim === 1 ? 'dimension1' : 'dimension2'
  const removedValue = newMatrix[dimension].values[valIndex]
  const newValues = newMatrix[dimension].values.filter((_, i) => i !== valIndex)
  newMatrix[dimension] = { ...newMatrix[dimension], values: newValues }

  // Remove from prices
  if (dim === 1) {
    delete newMatrix.prices[removedValue]
  } else {
    for (const rowKey of Object.keys(newMatrix.prices)) {
      delete newMatrix.prices[rowKey][removedValue]
    }
  }

  matrix2DValue.value = newMatrix
  emit('update:matrix2D', newMatrix)
}

function get2DPrice(row: string, col: string): number {
  return matrix2DValue.value.prices[row]?.[col] ?? matrix2DValue.value.defaultPrice
}

function set2DPrice(row: string, col: string, value: string | number) {
  const numValue = typeof value === 'string' ? parseFloat(value) : value
  const newMatrix = { ...matrix2DValue.value }

  if (!newMatrix.prices[row]) {
    newMatrix.prices[row] = {}
  }
  newMatrix.prices[row] = { ...newMatrix.prices[row], [col]: numValue || 0 }

  matrix2DValue.value = newMatrix
  emit('update:matrix2D', newMatrix)
}

function update2DDefaultPrice(value: string | number) {
  const numValue = typeof value === 'string' ? parseFloat(value) : value
  const newMatrix = { ...matrix2DValue.value, defaultPrice: numValue || 0 }
  matrix2DValue.value = newMatrix
  emit('update:matrix2D', newMatrix)
}
</script>
