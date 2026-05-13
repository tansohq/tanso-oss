<template>
  <Dialog :open="visible" @update:open="emit('update:visible', $event)">
    <DialogContent class="sm:max-w-[500px]">
      <DialogHeader>
        <DialogTitle>Change Plan</DialogTitle>
        <DialogDescription>
          Migrate this subscription to a different plan or version.
        </DialogDescription>
      </DialogHeader>

      <div v-if="subscription" class="flex flex-col gap-4 py-4">
        <!-- Current Plan Info -->
        <div class="p-3 bg-muted rounded-lg">
          <div class="text-sm text-muted-foreground mb-1">Current Plan</div>
          <div class="font-medium">{{ subscription.planName }} - v{{ subscription.version }}</div>
        </div>

        <!-- New Plan Selection -->
        <div class="space-y-2">
          <Label>New Plan</Label>
          <Select v-model="selectedPlanId">
            <SelectTrigger>
              <SelectValue placeholder="Select a plan" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem v-for="plan in availablePlans" :key="plan.id" :value="plan.id">
                {{ plan.name }}
              </SelectItem>
            </SelectContent>
          </Select>
        </div>

        <!-- Version Selection -->
        <div v-if="selectedPlanId" class="space-y-2">
          <Label>Version</Label>
          <Select v-model="selectedVersion">
            <SelectTrigger>
              <SelectValue placeholder="Select a version" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem
                v-for="version in availableVersions"
                :key="version.version"
                :value="String(version.version)"
              >
                v{{ version.version }}
                <span v-if="version.isDefault" class="text-muted-foreground ml-1">(default)</span>
                <span v-if="version.status === 'draft'" class="text-yellow-600 ml-1">(draft)</span>
              </SelectItem>
            </SelectContent>
          </Select>
        </div>

        <!-- Timing -->
        <div class="space-y-2">
          <Label>Effective Date</Label>
          <RadioGroup v-model="timing" class="space-y-2">
            <div class="flex items-center space-x-2">
              <RadioGroupItem value="immediate" id="timing-immediate" />
              <Label for="timing-immediate" class="font-normal cursor-pointer">
                Apply immediately
              </Label>
            </div>
            <div class="flex items-center space-x-2">
              <RadioGroupItem value="end_of_period" id="timing-end" />
              <Label for="timing-end" class="font-normal cursor-pointer">
                Apply at end of billing period ({{ subscription.currentPeriodEnd }})
              </Label>
            </div>
          </RadioGroup>
        </div>

        <!-- Preview -->
        <div v-if="selectedPlanId && selectedVersion" class="p-3 bg-blue-50 rounded-lg">
          <div class="flex items-center gap-2 text-sm">
            <ArrowRight class="h-4 w-4 text-blue-600" />
            <span>
              Changing to <strong>{{ selectedPlanName }}</strong> v{{ selectedVersion }}
            </span>
          </div>
        </div>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="handleClose">Cancel</Button>
        <Button @click="handleSave" :disabled="!canSave">
          <RefreshCw class="h-4 w-4 mr-2" />
          Change Plan
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ArrowRight, RefreshCw } from 'lucide-vue-next'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { useDemoState } from '../../composables/useDemoState'
import { toast } from '@/components/ui/toast/use-toast'

const props = defineProps<{
  visible: boolean
  subscriptionId: string | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const { subscriptionsData, pricingPlansData, changeSubscriptionPlan } = useDemoState()

const selectedPlanId = ref<string>('')
const selectedVersion = ref<string>('')
const timing = ref<'immediate' | 'end_of_period'>('immediate')

const subscription = computed(() => {
  if (!props.subscriptionId) return null
  return subscriptionsData.value.find((s) => s.id === props.subscriptionId) ?? null
})

const availablePlans = computed(() => {
  return pricingPlansData.value.filter((p) => p.status === 'active')
})

const selectedPlan = computed(() => {
  if (!selectedPlanId.value) return null
  return pricingPlansData.value.find((p) => p.id === selectedPlanId.value) ?? null
})

const selectedPlanName = computed(() => selectedPlan.value?.name ?? '')

const availableVersions = computed(() => {
  if (!selectedPlan.value) return []
  return selectedPlan.value.versions.filter((v) => v.status !== 'archived')
})

const canSave = computed(() => {
  return selectedPlanId.value && selectedVersion.value
})

// Reset form when modal opens
watch(
  () => props.visible,
  (visible) => {
    if (visible && subscription.value) {
      selectedPlanId.value = subscription.value.planId
      selectedVersion.value = String(subscription.value.version)
      timing.value = 'immediate'
    }
  }
)

// Reset version when plan changes
watch(selectedPlanId, (newPlanId) => {
  if (newPlanId) {
    const plan = pricingPlansData.value.find((p) => p.id === newPlanId)
    if (plan) {
      const defaultVersion = plan.versions.find((v) => v.isDefault)
      selectedVersion.value = defaultVersion ? String(defaultVersion.version) : ''
    }
  } else {
    selectedVersion.value = ''
  }
})

function handleClose() {
  emit('update:visible', false)
}

function handleSave() {
  if (!props.subscriptionId || !canSave.value || !subscription.value) return

  const effectiveDate =
    timing.value === 'end_of_period' ? subscription.value.currentPeriodEnd : undefined

  changeSubscriptionPlan(
    props.subscriptionId,
    selectedPlanId.value,
    parseInt(selectedVersion.value),
    effectiveDate
  )

  toast({
    title: 'Plan changed',
    description: `Subscription migrated to ${selectedPlanName.value} v${selectedVersion.value}.`
  })

  handleClose()
}
</script>
