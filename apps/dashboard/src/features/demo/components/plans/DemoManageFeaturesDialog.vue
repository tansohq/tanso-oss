<template>
  <Dialog :open="visible" @update:open="emit('update:visible', $event)">
    <DialogContent class="sm:max-w-[600px]">
      <DialogHeader>
        <DialogTitle>Manage Features</DialogTitle>
        <DialogDescription> Add features to this plan </DialogDescription>
      </DialogHeader>

      <div class="flex flex-col gap-4">
        <!-- Current Features List -->
        <div class="space-y-2">
          <h4 class="text-sm font-medium text-muted-foreground">Current Features</h4>
          <div
            v-if="currentFeatures.length > 0"
            class="divide-y rounded-lg border max-h-[200px] overflow-y-auto"
          >
            <div
              v-for="feature in currentFeatures"
              :key="feature.id"
              class="flex items-center justify-between p-3 hover:bg-muted/50 transition-colors"
            >
              <div class="flex flex-col gap-0.5">
                <span class="font-medium">{{ feature.name }}</span>
                <span class="text-xs text-muted-foreground font-mono">{{ feature.key }}</span>
              </div>
              <Button variant="ghost" size="sm" @click="handleRemoveFeature(feature.id)">
                <Trash2 class="h-4 w-4 text-muted-foreground hover:text-destructive" />
              </Button>
            </div>
          </div>
          <div
            v-else
            class="flex items-center gap-2 p-3 rounded-lg border border-amber-200 bg-amber-50/50"
          >
            <AlertTriangle class="w-4 h-4 text-amber-500 flex-shrink-0" />
            <p class="text-sm text-amber-700">
              Add at least one feature to make this plan available
            </p>
          </div>
        </div>

        <Separator />

        <!-- Add Feature Section -->
        <div class="space-y-3">
          <h4 class="text-sm font-medium text-muted-foreground">Add Feature</h4>

          <div v-if="availableFeatures.length > 0" class="flex gap-2">
            <Select v-model="selectedFeatureId" class="flex-1">
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
            <Button @click="handleAddFeature" :disabled="!selectedFeatureId"> Add </Button>
          </div>

          <p v-else class="text-sm text-muted-foreground">
            All features have been added to this plan
          </p>

          <!-- Create New Feature - Collapsible -->
          <div class="pt-2">
            <Button
              v-if="!showCreateForm"
              variant="ghost"
              size="sm"
              class="text-muted-foreground hover:text-foreground"
              @click="showCreateForm = true"
            >
              <Plus class="w-4 h-4 mr-1" />
              Create new feature
            </Button>

            <div v-else class="space-y-3 p-3 rounded-lg border bg-muted/30">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">New Feature</span>
                <Button variant="ghost" size="sm" @click="resetCreateForm">
                  <X class="w-4 h-4" />
                </Button>
              </div>

              <!-- Name & Key -->
              <div class="grid grid-cols-2 gap-3">
                <div class="flex flex-col gap-1.5">
                  <Label for="newFeatureName" class="text-xs">Name *</Label>
                  <Input
                    id="newFeatureName"
                    v-model="newFeatureName"
                    placeholder="e.g., SSO Access"
                    class="h-9"
                    @input="onNewFeatureNameInput"
                  />
                </div>
                <div class="flex flex-col gap-1.5">
                  <Label for="newFeatureKey" class="text-xs">Key *</Label>
                  <Input
                    id="newFeatureKey"
                    v-model="newFeatureKey"
                    placeholder="e.g., sso_access"
                    class="h-9"
                    @input="newFeatureKeyManuallyEdited = true"
                  />
                </div>
              </div>

              <Button
                size="sm"
                class="w-full"
                @click="handleCreateAndAddFeature"
                :disabled="!canCreateFeature"
              >
                Create & Add
              </Button>
            </div>
          </div>
        </div>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="close"> Done </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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
import { Plus, X, Trash2, AlertTriangle } from 'lucide-vue-next'
import { useDemoState } from '../../composables/useDemoState'
import { toast } from '@/components/ui/toast/use-toast'

const props = defineProps<{
  visible: boolean
  planId: string
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const {
  getPlanFeatures,
  getAvailableFeatures,
  addFeatureToPlan,
  removeFeatureFromPlan,
  createFeature
} = useDemoState()

const selectedFeatureId = ref<string>('')
const showCreateForm = ref(false)

// Create feature form state
const newFeatureName = ref('')
const newFeatureKey = ref('')
const newFeatureKeyManuallyEdited = ref(false)

// Get current features linked to this plan
const currentFeatures = computed(() => {
  if (!props.planId) return []
  return getPlanFeatures(props.planId)
})

// Get features available to add
const availableFeatures = computed(() => {
  if (!props.planId) return []
  return getAvailableFeatures(props.planId)
})

// Validation for create button
const canCreateFeature = computed(() => {
  return !!(newFeatureName.value && newFeatureKey.value)
})

// Reset form when dialog opens/closes
watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      selectedFeatureId.value = ''
      resetCreateForm()
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
  showCreateForm.value = false
  newFeatureName.value = ''
  newFeatureKey.value = ''
  newFeatureKeyManuallyEdited.value = false
}

function handleAddFeature() {
  if (!selectedFeatureId.value || !props.planId) return

  addFeatureToPlan(props.planId, selectedFeatureId.value)
  toast({ title: 'Success', description: 'Feature added to plan' })
  selectedFeatureId.value = ''
}

function handleRemoveFeature(featureId: string) {
  if (!props.planId) return

  removeFeatureFromPlan(props.planId, featureId)
  toast({ title: 'Success', description: 'Feature removed from plan' })
}

function handleCreateAndAddFeature() {
  if (!canCreateFeature.value || !props.planId) return

  const newFeature = createFeature({
    name: newFeatureName.value,
    key: newFeatureKey.value,
    description: '',
    isEnabled: true
  })

  addFeatureToPlan(props.planId, newFeature.id)
  toast({ title: 'Success', description: 'Feature created and added to plan' })
  resetCreateForm()
}

function close() {
  selectedFeatureId.value = ''
  resetCreateForm()
  emit('update:visible', false)
}
</script>
