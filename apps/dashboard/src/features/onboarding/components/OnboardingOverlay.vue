<template>
  <Dialog :open="true">
    <DialogContent
      class="max-w-sm [&>button:last-child]:hidden"
      @pointer-down-outside.prevent
      @escape-key-down.prevent
      @interact-outside.prevent
    >
      <!-- Stepper + Back -->
      <div class="flex items-center justify-between">
        <div class="w-16">
          <Button
            v-if="step > 0"
            variant="ghost"
            size="sm"
            class="-ml-3 text-muted-foreground"
            @click="step--; showOtherInput = false; otherText = ''"
          >
            <ChevronLeft class="w-4 h-4 mr-0.5" />
            Back
          </Button>
        </div>
        <div class="flex items-center gap-2">
          <template v-for="i in 4" :key="i">
            <div
              class="rounded-full transition-all duration-200"
              :class="[
                i < step + 1 ? 'h-2.5 w-2.5 bg-primary' : '',
                i === step + 1 ? 'h-2.5 w-2.5 bg-primary ring-2 ring-primary/20' : '',
                i > step + 1 ? 'h-2 w-2 bg-muted' : ''
              ]"
            />
            <div v-if="i < 4" class="h-px w-4 bg-border" />
          </template>
        </div>
        <div class="w-16 flex justify-end">
          <Button
            v-if="step < 3"
            variant="ghost"
            size="sm"
            class="-mr-3 text-muted-foreground text-xs"
            :disabled="isPending"
            @click="skipIntake"
          >
            Skip
          </Button>
        </div>
      </div>

      <!-- Steps 1-2: Single-select intake questions -->
      <template v-if="step < 2">
        <DialogHeader>
          <DialogTitle>{{ stepConfig.title }}</DialogTitle>
          <DialogDescription>{{ stepConfig.description }}</DialogDescription>
        </DialogHeader>

        <div class="flex flex-wrap gap-2">
          <Button
            v-for="opt in stepConfig.options"
            :key="opt.value"
            variant="outline"
            size="sm"
            class="rounded-full"
            :class="showOtherInput && opt.value === 'OTHER' ? 'bg-primary text-primary-foreground' : ''"
            @click="selectOption(opt.value)"
          >
            {{ opt.label }}
          </Button>
        </div>

        <!-- Other text input -->
        <div v-if="showOtherInput" class="flex gap-2">
          <Input
            v-model="otherText"
            placeholder="Please specify..."
            class="text-sm"
            @keydown.enter="confirmOther"
          />
          <Button size="sm" :disabled="!otherText.trim()" @click="confirmOther">
            Next
          </Button>
        </div>

      </template>

      <!-- Step 3: Goal (single-select) -->
      <template v-if="step === 2">
        <DialogHeader>
          <DialogTitle>{{ stepConfig.title }}</DialogTitle>
          <DialogDescription>{{ stepConfig.description }}</DialogDescription>
        </DialogHeader>

        <div class="flex flex-wrap gap-2">
          <Button
            v-for="opt in stepConfig.options"
            :key="opt.value"
            variant="outline"
            size="sm"
            class="rounded-full"
            :class="showOtherInput && opt.value === 'OTHER' ? 'bg-primary text-primary-foreground' : ''"
            @click="selectGoal(opt.value as Goal)"
          >
            {{ opt.label }}
          </Button>
        </div>

        <!-- Other text input for goals -->
        <div v-if="showOtherInput" class="flex gap-2">
          <Input
            v-model="goalOther"
            placeholder="Please specify..."
            class="text-sm"
            @keydown.enter="confirmGoalOther"
          />
          <Button size="sm" :disabled="!goalOther.trim()" @click="confirmGoalOther">
            Next
          </Button>
        </div>

      </template>

      <!-- Step 4: Mode selection -->
      <template v-if="step === 3">
        <DialogHeader>
          <DialogTitle>How do you want to get started?</DialogTitle>
          <DialogDescription>You can upgrade anytime.</DialogDescription>
        </DialogHeader>

        <div class="flex flex-col gap-3">
          <Card
            class="cursor-pointer transition-all"
            :class="mode === 'OBSERVE'
              ? 'ring-2 ring-primary'
              : 'hover:ring-1 hover:ring-border'"
            @click="mode = 'OBSERVE'"
          >
            <CardHeader class="p-4">
              <CardTitle class="flex items-center justify-between text-sm font-medium">
                <span class="flex items-center gap-2">
                  <Eye class="w-4 h-4 text-amber-500" />
                  Tanso Observe
                </span>
                <Check v-if="mode === 'OBSERVE'" class="w-4 h-4 text-primary" />
              </CardTitle>
              <CardDescription class="text-xs">
                Track cost, revenue, and margin per customer. No billing setup needed.
              </CardDescription>
            </CardHeader>
          </Card>

          <Card
            class="cursor-pointer transition-all"
            :class="mode === 'FULL'
              ? 'ring-2 ring-primary'
              : 'hover:ring-1 hover:ring-border'"
            @click="mode = 'FULL'"
          >
            <CardHeader class="p-4">
              <CardTitle class="flex items-center justify-between text-sm font-medium">
                <span class="flex items-center gap-2">
                  <Zap class="w-4 h-4 text-green-500" />
                  Tanso Platform
                </span>
                <Check v-if="mode === 'FULL'" class="w-4 h-4 text-primary" />
              </CardTitle>
              <CardDescription class="text-xs">
                Everything in Observe, plus plans, entitlements, billing, and Stripe.
              </CardDescription>
            </CardHeader>
          </Card>
        </div>

        <DialogFooter class="pt-2">
          <Button class="w-full" :disabled="isPending" @click="submitMode">
            <Loader2 v-if="isPending" class="mr-2 h-4 w-4 animate-spin" />
            Continue with {{ mode === 'OBSERVE' ? 'Tanso Observe' : 'Tanso Platform' }}
          </Button>
        </DialogFooter>
      </template>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog'
import { Card, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ChevronLeft, Eye, Zap, Check, Loader2 } from 'lucide-vue-next'
import { useTracking } from '@/lib/tracking'
import { useSaveIntakeMutation, useUpdatePlatformModeMutation, useCompleteStepMutation } from '../mutations'
import { useOnboardingProgressQuery } from '../queries'
import type { Role, BuildingType, Goal } from '../types'
import type { PlatformMode } from '@/features/integrations/schemas'

const queryClient = useQueryClient()
const { track } = useTracking()
const { data: progressData } = useOnboardingProgressQuery()

const step = ref(0)
const role = ref<Role | null>(null)
const roleOther = ref('')
const buildingType = ref<BuildingType | null>(null)
const buildingTypeOther = ref('')
const goal = ref<Goal | null>(null)
const goalOther = ref('')
const mode = ref<PlatformMode>('OBSERVE')
const showOtherInput = ref(false)
const otherText = ref('')

if (progressData.value) {
  const steps = progressData.value.data?.completedSteps ?? []
  if (steps.includes('intake_completed') || steps.includes('intake_skipped')) {
    step.value = 3
  }
}

const stepsConfig = [
  {
    title: "What's your role?",
    description: 'This helps us tailor your experience.',
    options: [
      { value: 'TECHNICAL_FOUNDER', label: 'Founder (technical)' },
      { value: 'NON_TECHNICAL_FOUNDER', label: 'Founder (non-technical)' },
      { value: 'ENGINEER', label: 'Engineer' },
      { value: 'PRODUCT', label: 'Product' },
      { value: 'GROWTH_OPS', label: 'Growth / Ops' },
      { value: 'OTHER', label: 'Other' }
    ]
  },
  {
    title: 'What are you building?',
    description: "We'll customize your setup based on this.",
    options: [
      { value: 'B2B_AI', label: 'B2B AI' },
      { value: 'B2C_AI', label: 'B2C AI' },
      { value: 'SAAS_PLATFORM', label: 'SaaS product' },
      { value: 'API_SERVICE', label: 'API product' },
      { value: 'MARKETPLACE', label: 'Marketplace' },
      { value: 'OTHER', label: 'Other' }
    ]
  },
  {
    title: 'What brings you to Tanso?',
    description: "We'll recommend the best way to get started.",
    options: [
      { value: 'MARGIN_ANALYTICS', label: 'Margin and cost analytics' },
      { value: 'STRIPE_INTEGRATION', label: 'Stripe integration' },
      { value: 'PRICING_FLEXIBILITY', label: 'Pricing model flexibility' },
      { value: 'ENFORCE_LIMITS', label: 'Enforce usage limits' },
      { value: 'OTHER', label: 'Other' }
    ]
  }
]

const stepConfig = computed(() => stepsConfig[step.value] ?? stepsConfig[0])

const { mutate: saveIntake, isPending: isSaving } = useSaveIntakeMutation()
const { mutate: updateSettings, isPending: isUpdating } = useUpdatePlatformModeMutation()
const { mutate: completeStep, isPending: isCompleting } = useCompleteStepMutation()

const isPending = computed(() => isSaving.value || isUpdating.value || isCompleting.value)

function selectOption(value: string) {
  if (value === 'OTHER') {
    showOtherInput.value = true
    otherText.value = ''
    if (step.value === 0) role.value = 'OTHER'
    else if (step.value === 1) buildingType.value = 'OTHER'
    return
  }

  showOtherInput.value = false
  if (step.value === 0) {
    role.value = value as Role
    track('onboarding_role_selected', { role: value })
    step.value = 1
  } else if (step.value === 1) {
    buildingType.value = value as BuildingType
    track('onboarding_building_type_selected', { buildingType: value })
    step.value = 2
  }
}

function confirmOther() {
  if (!otherText.value.trim()) return
  showOtherInput.value = false

  if (step.value === 0) {
    roleOther.value = otherText.value.trim()
    track('onboarding_role_selected', { role: 'OTHER' })
    step.value = 1
  } else if (step.value === 1) {
    buildingTypeOther.value = otherText.value.trim()
    track('onboarding_building_type_selected', { buildingType: 'OTHER' })
    step.value = 2
  }
  otherText.value = ''
}

function selectGoal(value: Goal) {
  if (value === 'OTHER') {
    showOtherInput.value = true
    goal.value = 'OTHER'
    return
  }
  showOtherInput.value = false
  goal.value = value
  track('onboarding_goal_selected', { goal: value })
  submitIntake()
}

function confirmGoalOther() {
  if (!goalOther.value.trim()) return
  showOtherInput.value = false
  track('onboarding_goal_selected', { goal: 'OTHER' })
  submitIntake()
}

function submitIntake() {
  if (!role.value || !buildingType.value || !goal.value) return
  saveIntake(
    {
      role: role.value,
      roleOther: roleOther.value || undefined,
      buildingType: buildingType.value,
      buildingTypeOther: buildingTypeOther.value || undefined,
      goal: goal.value,
      goalOther: goalOther.value || undefined
    },
    {
      onSuccess: () => {
        track('onboarding_intake_submitted', {
          role: role.value,
          buildingType: buildingType.value,
          goal: goal.value
        })
        completeStep('intake_completed', {
          onSuccess: () => { step.value = 3 }
        })
      }
    }
  )
}

function skipIntake() {
  track('onboarding_intake_skipped')
  completeStep('intake_skipped', {
    onSuccess: () => { step.value = 3 }
  })
}

function submitMode() {
  track('onboarding_mode_selected', { mode: mode.value })
  const isObserve = mode.value !== 'FULL'
  try { localStorage.setItem('tanso_last_observe_mode', String(isObserve)) } catch { /* ignore */ }

  const finalize = () => {
    completeStep('mode_selected', {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: ['onboarding', 'progress'] })
        queryClient.invalidateQueries({ queryKey: ['account-settings'] })
      }
    })
  }

  // Default is now OBSERVE. Only call updateSettings when switching to FULL.
  if (mode.value === 'FULL') {
    updateSettings(mode.value, { onSuccess: finalize })
  } else {
    finalize()
  }
}
</script>
