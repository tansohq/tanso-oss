<template>
  <div class="flex items-center justify-center min-h-screen bg-background py-10">
    <div class="w-full max-w-4xl px-4">
      <div class="text-center mb-8">
        <h1 class="text-3xl font-bold text-foreground mb-2">Select Your Plan</h1>
        <p class="text-muted-foreground">Choose a plan to get started with Tanso</p>
      </div>

      <div v-if="isLoadingPlans" class="flex justify-center">
        <Loader2 class="h-8 w-8 animate-spin text-muted-foreground" />
      </div>

      <div v-else-if="plansError" class="text-center">
        <Alert variant="destructive" class="max-w-md mx-auto">
          <AlertCircle class="h-4 w-4" />
          <AlertDescription>
            Failed to load plans. Please try again later.
          </AlertDescription>
        </Alert>
      </div>

      <div v-else class="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card
          v-for="plan in plans"
          :key="plan.id"
          class="cursor-pointer transition-all hover:shadow-lg"
          :class="{
            'ring-2 ring-primary': selectedPlanId === plan.id,
            'hover:ring-1 hover:ring-border': selectedPlanId !== plan.id
          }"
          @click="selectedPlanId = plan.id"
        >
          <CardHeader>
            <CardTitle class="flex items-center justify-between">
              {{ plan.name }}
              <Check
                v-if="selectedPlanId === plan.id"
                class="h-5 w-5 text-primary"
              />
            </CardTitle>
            <CardDescription>{{ plan.description }}</CardDescription>
          </CardHeader>
          <CardContent>
            <div class="text-3xl font-bold">
              <template v-if="plan.priceAmount === 0">Free</template>
              <template v-else>
                ${{ plan.priceAmount.toFixed(0) }}
                <span class="text-sm font-normal text-muted-foreground">/month</span>
              </template>
            </div>
          </CardContent>
        </Card>
      </div>

      <Alert v-if="subscribeError" variant="destructive" class="max-w-md mx-auto mt-6">
        <AlertCircle class="h-4 w-4" />
        <AlertDescription>
          {{ subscribeErrorMessage }}
        </AlertDescription>
      </Alert>

      <div class="flex justify-center mt-8">
        <Button
          size="lg"
          :disabled="!selectedPlanId || isSubscribing"
          @click="handleSubscribe"
        >
          <Loader2 v-if="isSubscribing" class="mr-2 h-4 w-4 animate-spin" />
          Continue with {{ selectedPlan?.name || 'Selected Plan' }}
        </Button>
      </div>

      <div class="flex justify-center mt-4">
        <Button variant="ghost" @click="handleLogout">
          <LogOut class="w-4 h-4 mr-2" />
          Sign out and use a different account
        </Button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Button } from '@/components/ui/button'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Loader2, AlertCircle, Check, LogOut } from 'lucide-vue-next'
import { useOnboardingPlansQuery } from '@/features/auth/queries'
import { useSubscribeToPlanMutation } from '@/features/account/mutations'
import { useAuthStore } from '@/stores/auth'

const selectedPlanId = ref<string | null>(null)
const subscribeErrorMessage = ref<string | null>(null)

const authStore = useAuthStore()

const { data: plansData, isLoading: isLoadingPlans, error: plansError } = useOnboardingPlansQuery()
const { mutate: subscribe, isPending: isSubscribing, error: subscribeError } = useSubscribeToPlanMutation()

const plans = computed(() => {
  const data = plansData.value?.data ?? []
  return [...data]
    .filter((plan) => {
      const isVisible = plan.metadata?.isVisible
      return isVisible === true || isVisible === undefined || isVisible === null
    })
    .sort((a, b) => {
      const orderA = typeof a.metadata?.order === 'number' ? a.metadata.order : 0
      const orderB = typeof b.metadata?.order === 'number' ? b.metadata.order : 0
      return orderA - orderB
    })
})

const selectedPlan = computed(() => plans.value.find((p) => p.id === selectedPlanId.value))

function handleSubscribe() {
  if (!selectedPlanId.value) return

  subscribeErrorMessage.value = null

  subscribe(
    { planId: selectedPlanId.value },
    {
      onError: (error) => {
        if (error instanceof Error) {
          subscribeErrorMessage.value = error.message || 'Failed to subscribe. Please try again.'
        } else {
          subscribeErrorMessage.value = 'An unexpected error occurred. Please try again.'
        }
      }
    }
  )
}

function handleLogout() {
  authStore.logout()
}
</script>
