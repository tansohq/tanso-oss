<template>
  <div class="bg-white rounded-xl shadow-xl w-full max-w-[400px] mx-4 p-8 min-h-[420px] flex flex-col">
    <!-- Stepper + Back -->
    <div class="flex items-center justify-between mb-6">
      <button
        type="button"
        class="text-sm text-slate-400 hover:text-slate-600 flex items-center gap-1"
        @click="emit('back')"
      >
        <ChevronLeft class="w-4 h-4" />
        Back
      </button>
      <div class="flex items-center gap-1.5">
        <div
          v-for="step in totalSteps"
          :key="step"
          class="h-1.5 rounded-full transition-all duration-300"
          :class="[
            step <= globalStep ? 'bg-slate-900' : 'bg-slate-200',
            step === globalStep ? 'w-6' : 'w-3'
          ]"
        />
      </div>
    </div>

    <h1 class="text-slate-900 text-xl font-bold text-center mb-1">How do you want to get started?</h1>
    <p class="text-slate-500 text-center text-sm mb-6">
      You can upgrade from Observe to Full at any time.
    </p>

    <div class="flex flex-col gap-3">
      <!-- Observe Card -->
      <button
        type="button"
        class="text-left p-5 rounded-lg border-2 transition-all flex flex-col"
        :class="
          selectedMode === 'OBSERVE'
            ? 'border-amber-500 bg-amber-50/50'
            : 'border-slate-200 hover:border-slate-300 bg-white'
        "
        @click="selectedMode = 'OBSERVE'"
      >
        <div class="flex items-center gap-2 mb-1">
          <Eye class="w-4 h-4 text-amber-600" />
          <h2 class="text-base font-semibold text-slate-900">Observe</h2>
        </div>
        <p class="text-slate-500 text-sm mb-4">See your unit economics before changing anything.</p>

        <ul class="space-y-1.5 text-sm text-slate-600 flex-1">
          <li class="flex items-start gap-2">
            <Check class="w-3.5 h-3.5 text-amber-500 mt-0.5 shrink-0" />
            <span>Cost, revenue, and margin per customer</span>
          </li>
          <li class="flex items-start gap-2">
            <Check class="w-3.5 h-3.5 text-amber-500 mt-0.5 shrink-0" />
            <span>SDK integration in minutes</span>
          </li>
          <li class="flex items-start gap-2">
            <Check class="w-3.5 h-3.5 text-amber-500 mt-0.5 shrink-0" />
            <span>No Stripe or billing setup needed</span>
          </li>
          <li class="flex items-start gap-2">
            <Check class="w-3.5 h-3.5 text-amber-500 mt-0.5 shrink-0" />
            <span>Analytics dashboard included</span>
          </li>
        </ul>
      </button>

      <!-- Full Card -->
      <button
        type="button"
        class="text-left p-5 rounded-lg border-2 transition-all flex flex-col"
        :class="
          selectedMode === 'FULL'
            ? 'border-green-500 bg-green-50/50'
            : 'border-slate-200 hover:border-slate-300 bg-white'
        "
        @click="selectedMode = 'FULL'"
      >
        <div class="flex items-center gap-2 mb-1">
          <Zap class="w-4 h-4 text-green-600" />
          <h2 class="text-base font-semibold text-slate-900">Full</h2>
        </div>
        <p class="text-slate-500 text-sm mb-4">Enforce limits, manage billing, control access.</p>

        <ul class="space-y-1.5 text-sm text-slate-600 flex-1">
          <li class="flex items-start gap-2">
            <Check class="w-3.5 h-3.5 text-green-500 mt-0.5 shrink-0" />
            <span>Everything in Observe</span>
          </li>
          <li class="flex items-start gap-2">
            <Check class="w-3.5 h-3.5 text-green-500 mt-0.5 shrink-0" />
            <span>Plans, subscriptions, entitlements</span>
          </li>
          <li class="flex items-start gap-2">
            <Check class="w-3.5 h-3.5 text-green-500 mt-0.5 shrink-0" />
            <span>Usage caps and credit enforcement</span>
          </li>
          <li class="flex items-start gap-2">
            <Check class="w-3.5 h-3.5 text-green-500 mt-0.5 shrink-0" />
            <span>Stripe integration and invoicing</span>
          </li>
        </ul>
      </button>
    </div>

    <div class="flex-1" />

    <Button
      :disabled="!selectedMode || isPending"
      class="w-full mt-6"
      @click="confirmSelection"
    >
      <Loader2 v-if="isPending" class="mr-2 h-4 w-4 animate-spin" />
      Continue with {{ selectedMode === 'OBSERVE' ? 'Observe' : 'Full' }}
    </Button>

    <p class="mt-3 text-center text-xs text-slate-400">
      Not sure? Start with Observe. You can upgrade anytime.
    </p>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { Button } from '@/components/ui/button'
import { Eye, Zap, Check, Loader2, ChevronLeft } from 'lucide-vue-next'
import { useIntakeDataQuery } from '../queries'
import { useUpdatePlatformModeMutation, useCompleteStepMutation } from '../mutations'
import type { PlatformMode } from '@/features/integrations/schemas'

defineProps<{
  globalStep: number
  totalSteps: number
}>()

const emit = defineEmits<{
  complete: []
  back: []
}>()

const selectedMode = ref<PlatformMode>('OBSERVE')

const { data: intakeData } = useIntakeDataQuery()

watch(
  () => intakeData.value?.data,
  (intake) => {
    if (
      intake &&
      (intake.goal === 'ENFORCE_LIMITS' ||
        intake.goal === 'STRIPE_INTEGRATION' ||
        intake.goal === 'PRICING_FLEXIBILITY')
    ) {
      selectedMode.value = 'FULL'
    }
  },
  { immediate: true }
)

const { mutate: updateSettings, isPending: isUpdatingMode } = useUpdatePlatformModeMutation()
const { mutate: completeStep, isPending: isCompletingStep } = useCompleteStepMutation()

const isPending = computed(() => isUpdatingMode.value || isCompletingStep.value)

function confirmSelection() {
  if (!selectedMode.value) return

  updateSettings(selectedMode.value, {
    onSuccess: () => {
      completeStep('mode_selected', {
        onSuccess: () => emit('complete')
      })
    }
  })
}
</script>
