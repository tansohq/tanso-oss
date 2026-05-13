<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
    <div class="bg-white p-10 rounded-lg shadow-xl w-full max-w-[550px] mx-4">
      <!-- Progress -->
      <div class="flex items-center justify-between mb-6">
        <button
          v-if="currentStep > 0"
          type="button"
          class="text-sm text-slate-400 hover:text-slate-600 flex items-center gap-1"
          @click="currentStep--"
        >
          <ChevronLeft class="w-4 h-4" />
          Back
        </button>
        <div v-else />
        <span class="text-sm text-slate-400">{{ currentStep + 1 }} of {{ steps.length }}</span>
      </div>

      <!-- Step 1: Role -->
      <div v-if="currentStep === 0">
        <h1 class="m-0 mb-2 text-slate-900 text-xl font-bold">What's your role?</h1>
        <p class="text-sm text-slate-500 mb-6">This helps us tailor your experience.</p>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="option in roleOptions"
            :key="option.value"
            type="button"
            class="px-4 py-2 rounded-full text-sm border transition-colors"
            :class="
              role === option.value
                ? 'bg-slate-900 text-white border-slate-900'
                : 'bg-white text-slate-600 border-slate-200 hover:border-slate-400'
            "
            @click="selectRole(option.value)"
          >
            {{ option.label }}
          </button>
        </div>
      </div>

      <!-- Step 2: Building type -->
      <div v-if="currentStep === 1">
        <h1 class="m-0 mb-2 text-slate-900 text-xl font-bold">What are you building?</h1>
        <p class="text-sm text-slate-500 mb-6">We'll customize your setup based on this.</p>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="option in buildingTypeOptions"
            :key="option.value"
            type="button"
            class="px-4 py-2 rounded-full text-sm border transition-colors"
            :class="
              buildingType === option.value
                ? 'bg-slate-900 text-white border-slate-900'
                : 'bg-white text-slate-600 border-slate-200 hover:border-slate-400'
            "
            @click="selectBuildingType(option.value)"
          >
            {{ option.label }}
          </button>
        </div>
      </div>

      <!-- Step 3: Goal -->
      <div v-if="currentStep === 2">
        <h1 class="m-0 mb-2 text-slate-900 text-xl font-bold">What brings you to Tanso?</h1>
        <p class="text-sm text-slate-500 mb-6">We'll recommend the best way to get started.</p>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="option in goalOptions"
            :key="option.value"
            type="button"
            class="px-4 py-2 rounded-full text-sm border transition-colors"
            :class="
              goal === option.value
                ? 'bg-slate-900 text-white border-slate-900'
                : 'bg-white text-slate-600 border-slate-200 hover:border-slate-400'
            "
            @click="selectGoal(option.value)"
          >
            {{ option.label }}
          </button>
        </div>
      </div>

      <!-- Skip -->
      <button
        type="button"
        :disabled="isPending"
        class="mt-6 w-full text-center text-sm text-slate-400 hover:text-slate-600 disabled:opacity-50 disabled:cursor-not-allowed"
        @click="skipIntake"
      >
        Skip for now
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ChevronLeft } from 'lucide-vue-next'
import { useSaveIntakeMutation, useCompleteStepMutation } from '../mutations'
import { useTracking } from '@/lib/tracking'
import type { IntakeData, Role, BuildingType, Goal } from '../types'

const router = useRouter()

const currentStep = ref(0)
const role = ref<Role | null>(null)
const buildingType = ref<BuildingType | null>(null)
const goal = ref<Goal | null>(null)

const steps = ['role', 'buildingType', 'goal']

const roleOptions: { value: Role; label: string }[] = [
  { value: 'TECHNICAL_FOUNDER', label: 'Founder (technical)' },
  { value: 'NON_TECHNICAL_FOUNDER', label: 'Founder (non-technical)' },
  { value: 'ENGINEER', label: 'Engineer' },
  { value: 'PRODUCT', label: 'Product' },
  { value: 'GROWTH_OPS', label: 'Growth / Ops' },
  { value: 'OTHER', label: 'Other' }
]

const buildingTypeOptions: { value: BuildingType; label: string }[] = [
  { value: 'B2B_AI', label: 'B2B AI' },
  { value: 'B2C_AI', label: 'B2C AI' },
  { value: 'SAAS_PLATFORM', label: 'SaaS product' },
  { value: 'API_SERVICE', label: 'API product' },
  { value: 'MARKETPLACE', label: 'Marketplace' },
  { value: 'OTHER', label: 'Other' }
]

const goalOptions: { value: Goal; label: string }[] = [
  { value: 'MARGIN_ANALYTICS', label: 'Margin and cost analytics' },
  { value: 'STRIPE_INTEGRATION', label: 'Stripe integration' },
  { value: 'PRICING_FLEXIBILITY', label: 'Pricing model flexibility' },
  { value: 'ENFORCE_LIMITS', label: 'Enforce usage limits' }
]

const { track } = useTracking()
const { mutate: saveIntake, isPending: isSaving } = useSaveIntakeMutation()
const { mutate: completeStep, isPending: isCompletingStep } = useCompleteStepMutation()

const isPending = computed(() => isSaving.value || isCompletingStep.value)

function selectRole(value: Role) {
  role.value = value
  track('onboarding_role_selected', { role: value })
  currentStep.value = 1
}

function selectBuildingType(value: BuildingType) {
  buildingType.value = value
  track('onboarding_building_type_selected', { buildingType: value })
  currentStep.value = 2
}

function selectGoal(value: Goal) {
  goal.value = value
  track('onboarding_goal_selected', { goal: value })
  submitIntake()
}

function submitIntake() {
  if (!role.value || !buildingType.value || !goal.value) return

  const data: IntakeData = {
    role: role.value,
    buildingType: buildingType.value,
    goal: goal.value
  }

  saveIntake(data, {
    onSuccess: () => {
      track('onboarding_intake_submitted', {
        role: data.role,
        buildingType: data.buildingType,
        goal: data.goal
      })
      completeStep('intake_completed', {
        onSuccess: () => {
          router.push({ name: 'select-mode' })
        }
      })
    }
  })
}

function skipIntake() {
  track('onboarding_intake_skipped')
  completeStep('intake_skipped', {
    onSuccess: () => {
      router.push({ name: 'select-mode' })
    }
  })
}
</script>
