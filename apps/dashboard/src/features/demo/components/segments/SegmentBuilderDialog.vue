<template>
  <Dialog :open="open" @update:open="$emit('update:open', $event)">
    <DialogContent class="max-w-4xl max-h-[90vh] overflow-hidden flex flex-col">
      <DialogHeader>
        <DialogTitle>{{ segmentId ? 'Edit Segment' : 'Create Segment' }}</DialogTitle>
        <DialogDescription>
          Define conditions to segment customers into this segment.
        </DialogDescription>
      </DialogHeader>

      <div class="flex-1 overflow-hidden flex gap-6 mt-4">
        <!-- Left: Rule Builder -->
        <div class="flex-1 overflow-y-auto p-1 pr-4">
          <div class="space-y-4">
            <!-- Segment Name -->
            <div>
              <Label for="name">Segment Name</Label>
              <Input
                id="name"
                v-model="segmentName"
                placeholder="e.g., High-Value Margin Erosion"
                class="mt-1"
                autofocus
              />
            </div>

            <!-- Conditions -->
            <div>
              <Label>Conditions</Label>
              <p class="text-sm text-muted-foreground mb-3">
                All of the following must be true (AND):
              </p>

              <div class="space-y-3">
                <div
                  v-for="(rule, index) in rules"
                  :key="rule.id"
                  class="flex items-center gap-2 p-3 border rounded-lg"
                >
                  <Select v-model="rule.field">
                    <SelectTrigger class="w-[180px]">
                      <SelectValue placeholder="Select field" />
                    </SelectTrigger>
                    <SelectContent>
                      <template v-for="category in fieldCategories" :key="category.label">
                        <SelectGroup>
                          <SelectLabel class="flex items-center gap-2">
                            {{ category.label }}
                            <Badge v-if="category.isNew" variant="secondary" class="text-xs"
                              >New</Badge
                            >
                          </SelectLabel>
                          <SelectItem
                            v-for="field in category.fields"
                            :key="field.value"
                            :value="field.value"
                          >
                            {{ field.label }}
                          </SelectItem>
                        </SelectGroup>
                      </template>
                    </SelectContent>
                  </Select>

                  <Select v-model="rule.operator">
                    <SelectTrigger class="w-[150px]">
                      <SelectValue placeholder="Operator" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="less_than">is less than</SelectItem>
                      <SelectItem value="at_most">is at most</SelectItem>
                      <SelectItem value="equals">is equal to</SelectItem>
                      <SelectItem value="at_least">is at least</SelectItem>
                      <SelectItem value="greater_than">is greater than</SelectItem>
                      <SelectItem value="between">is between</SelectItem>
                    </SelectContent>
                  </Select>

                  <Input v-model="rule.value" type="number" class="w-[100px]" placeholder="Value" />

                  <Button variant="ghost" size="icon" @click="removeRule(index)">
                    <X class="h-4 w-4" />
                  </Button>
                </div>

                <div v-if="rules.length > 0" class="text-center text-sm text-muted-foreground py-2">
                  AND
                </div>
              </div>

              <Button variant="outline" class="mt-3" @click="addRule">
                <Plus class="h-4 w-4 mr-2" />
                Add condition
              </Button>
            </div>
          </div>
        </div>

        <!-- Right: Live Preview -->
        <div class="w-80 border-l pl-6 overflow-y-auto">
          <div class="sticky top-0 bg-background pb-4">
            <h4 class="text-sm font-medium text-muted-foreground uppercase tracking-wide">
              Preview
            </h4>
          </div>

          <div v-if="rules.length > 0" class="space-y-4">
            <!-- Match Summary -->
            <Card class="p-4 text-center">
              <div class="text-3xl font-bold">{{ previewCount }}</div>
              <div class="text-sm text-muted-foreground">customers match</div>
              <div class="text-lg font-semibold mt-1">${{ previewMrr }}</div>
              <div class="text-sm text-muted-foreground">MRR</div>
              <div class="text-lg font-semibold mt-1">{{ previewMargin }}%</div>
              <div class="text-sm text-muted-foreground">Avg Gross Margin</div>
            </Card>

            <!-- Warning -->
            <Alert v-if="previewCount < 30" class="bg-amber-50 border-amber-200">
              <AlertTriangle class="h-4 w-4 text-amber-600" />
              <AlertTitle class="text-amber-800 text-sm">Small sample size</AlertTitle>
              <AlertDescription class="text-amber-700 text-xs">
                May affect experiment validity. Consider Bayesian or switchback tests.
              </AlertDescription>
            </Alert>

            <!-- Sample Customers -->
            <div>
              <h5 class="text-xs font-medium text-muted-foreground uppercase mb-2">
                Sample Customers
              </h5>
              <div class="space-y-2">
                <div
                  v-for="customer in previewCustomers"
                  :key="customer.name"
                  class="flex justify-between text-sm p-2 bg-muted/50 rounded"
                >
                  <span class="truncate">{{ customer.name }}</span>
                  <span class="text-muted-foreground">${{ customer.mrr }}</span>
                </div>
              </div>
            </div>
          </div>

          <div v-else class="text-center text-muted-foreground py-8">
            Add conditions to see matching customers
          </div>
        </div>
      </div>

      <DialogFooter class="mt-6">
        <Button variant="outline" @click="$emit('update:open', false)">Cancel</Button>
        <Button @click="saveSegment">Save Segment</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Alert, AlertTitle, AlertDescription } from '@/components/ui/alert'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import { Plus, X, AlertTriangle } from 'lucide-vue-next'
import { segmentFieldOptions } from '../../data/mockData'

defineProps<{
  open: boolean
  segmentId: string | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const segmentName = ref('')

interface Rule {
  id: string
  field: string
  operator: string
  value: string
}

const rules = ref<Rule[]>([
  {
    id: `rule_${Date.now()}`,
    field: '',
    operator: '',
    value: ''
  }
])

const fieldCategories = computed(() => {
  const categories: Record<
    string,
    { label: string; isNew: boolean; fields: typeof segmentFieldOptions }
  > = {}

  for (const field of segmentFieldOptions) {
    if (!categories[field.category]) {
      categories[field.category] = {
        label: field.categoryLabel,
        isNew: field.category === 'margin_cost' || field.category === 'ai_native',
        fields: []
      }
    }
    categories[field.category].fields.push(field)
  }

  return Object.values(categories)
})

function addRule() {
  rules.value.push({
    id: `rule_${Date.now()}`,
    field: '',
    operator: '',
    value: ''
  })
}

function removeRule(index: number) {
  rules.value.splice(index, 1)
}

// Mock preview data
const previewCount = computed(() => {
  if (rules.value.length === 0) return 0
  return 47 + rules.value.length * 3
})

const previewMrr = computed(() => {
  return (previewCount.value * 1238).toLocaleString()
})

const previewMargin = computed(() => {
  return 42 + rules.value.length * 2
})

const previewCustomers = computed(() => [
  { name: 'Acme Corp', mrr: '2,400' },
  { name: 'TechFlow Inc', mrr: '1,850' },
  { name: 'DataSync Labs', mrr: '890' },
  { name: 'CloudPeak', mrr: '1,200' },
  { name: 'Quantum AI', mrr: '3,100' }
])

function saveSegment() {
  // In a real app, this would save the segment
  emit('update:open', false)
}
</script>
