<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm">
    <div class="w-full max-w-[750px] px-4">
      <h1 class="text-white text-2xl font-bold text-center mb-2">How do you want to get started?</h1>
      <p class="text-slate-300 text-center text-sm mb-8">
        You can upgrade from Observe to Full at any time.
      </p>

      <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <!-- Observe Card -->
        <button
          type="button"
          class="text-left p-6 rounded-lg border-2 transition-all"
          :class="
            selectedMode === 'OBSERVE'
              ? 'bg-white border-amber-500 shadow-lg shadow-amber-500/10'
              : 'bg-white/95 border-transparent hover:border-slate-300'
          "
          @click="selectedMode = 'OBSERVE'"
        >
          <div class="flex items-center gap-2 mb-3">
            <div class="w-8 h-8 rounded-lg bg-amber-100 flex items-center justify-center">
              <Eye class="w-4 h-4 text-amber-600" />
            </div>
            <h2 class="text-lg font-semibold text-slate-900">Observe</h2>
            <Badge variant="outline" class="text-amber-600 border-amber-300 text-xs">Recommended</Badge>
          </div>

          <p class="text-slate-900 font-medium text-sm mb-3">
            Know exactly what your AI costs per customer.
          </p>

          <ul class="space-y-2 text-sm text-slate-600">
            <li class="flex items-start gap-2">
              <Check class="w-4 h-4 text-amber-500 mt-0.5 shrink-0" />
              <span>Track cost, revenue, and margin per customer</span>
            </li>
            <li class="flex items-start gap-2">
              <Check class="w-4 h-4 text-amber-500 mt-0.5 shrink-0" />
              <span>Integrate via SDK in minutes</span>
            </li>
            <li class="flex items-start gap-2">
              <Check class="w-4 h-4 text-amber-500 mt-0.5 shrink-0" />
              <span>No billing setup or Stripe required</span>
            </li>
            <li class="flex items-start gap-2">
              <Check class="w-4 h-4 text-amber-500 mt-0.5 shrink-0" />
              <span>Analytics dashboard out of the box</span>
            </li>
          </ul>

          <p class="mt-4 text-xs text-slate-400">Best for: Understanding your unit economics first</p>
        </button>

        <!-- Full Card -->
        <button
          type="button"
          class="text-left p-6 rounded-lg border-2 transition-all"
          :class="
            selectedMode === 'FULL'
              ? 'bg-white border-green-500 shadow-lg shadow-green-500/10'
              : 'bg-white/95 border-transparent hover:border-slate-300'
          "
          @click="selectedMode = 'FULL'"
        >
          <div class="flex items-center gap-2 mb-3">
            <div class="w-8 h-8 rounded-lg bg-green-100 flex items-center justify-center">
              <Zap class="w-4 h-4 text-green-600" />
            </div>
            <h2 class="text-lg font-semibold text-slate-900">Full</h2>
          </div>

          <p class="text-slate-900 font-medium text-sm mb-3">
            Enforce limits, manage billing, and control access.
          </p>

          <ul class="space-y-2 text-sm text-slate-600">
            <li class="flex items-start gap-2">
              <Check class="w-4 h-4 text-green-500 mt-0.5 shrink-0" />
              <span>Everything in Observe</span>
            </li>
            <li class="flex items-start gap-2">
              <Check class="w-4 h-4 text-green-500 mt-0.5 shrink-0" />
              <span>Plans, subscriptions, and entitlements</span>
            </li>
            <li class="flex items-start gap-2">
              <Check class="w-4 h-4 text-green-500 mt-0.5 shrink-0" />
              <span>Usage caps and credit enforcement</span>
            </li>
            <li class="flex items-start gap-2">
              <Check class="w-4 h-4 text-green-500 mt-0.5 shrink-0" />
              <span>Stripe integration and invoicing</span>
            </li>
          </ul>

          <p class="mt-4 text-xs text-slate-400">Best for: Ready to manage billing infrastructure</p>
        </button>
      </div>

      <div class="mt-6 flex justify-center">
        <Button
          :disabled="!selectedMode || isPending"
          class="min-w-[200px]"
          @click="confirmSelection"
        >
          <Loader2 v-if="isPending" class="mr-2 h-4 w-4 animate-spin" />
          Continue with {{ selectedMode === 'OBSERVE' ? 'Observe' : 'Full' }}
        </Button>
      </div>

      <p class="mt-3 text-center text-xs text-slate-400">
        Not sure? Start with Observe. You can upgrade anytime.
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Eye, Zap, Check, Loader2 } from 'lucide-vue-next'
import { useIntakeDataQuery } from '../queries'
import { useUpdatePlatformModeMutation, useCompleteStepMutation } from '../mutations'
import type { PlatformMode } from '@/features/integrations/schemas'

const router = useRouter()
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
        onSuccess: () => {
          if (selectedMode.value === 'OBSERVE') {
            router.push({ name: 'home' })
          } else {
            router.push({ name: 'select-plan' })
          }
        }
      })
    }
  })
}
</script>
