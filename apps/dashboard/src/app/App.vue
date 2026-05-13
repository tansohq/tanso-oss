<template>
  <!-- Mobile gate — only block the dashboard after onboarding, not auth/onboarding/demo -->
  <div v-if="isMobile && showNav && !showOnboarding" class="flex min-h-svh flex-col items-center justify-center px-6 text-center bg-background">
    <Monitor class="w-12 h-12 text-muted-foreground/40 mb-6" />
    <h1 class="text-xl font-semibold tracking-tight mb-2">Tanso is built for desktop</h1>
    <p class="text-sm text-muted-foreground max-w-xs mb-8">
      Head to your computer to manage plans, subscriptions, and billing.
    </p>
    <a
      href="https://cal.com/katrina-laszlo/30-minute-meeting"
      target="_blank"
      rel="noopener noreferrer"
      class="inline-flex items-center justify-center gap-2 rounded-md bg-foreground text-background px-5 py-2.5 text-sm font-medium shadow hover:bg-foreground/90 transition-colors"
    >
      <Calendar class="w-4 h-4" />
      Book a Call With Us
    </a>
  </div>

  <!-- Desktop app -->
  <TooltipProvider v-else>
    <Toaster />
    <template v-if="isReady">
      <!-- Auth transition: show loading screen so nav + content appear together -->
      <div
        v-if="isAuthTransitioning"
        class="flex items-center justify-center min-h-screen bg-background"
      >
        <Loader2 class="w-6 h-6 animate-spin text-muted-foreground" />
      </div>
      <SidebarProvider v-else-if="showNav">
        <SideNav />
        <SidebarInset>
          <header class="flex h-14 items-center gap-2 px-3 border-b md:hidden bg-sidebar text-sidebar-foreground">
            <SidebarTrigger />
          </header>
          <RouterView />
        </SidebarInset>

        <!-- Onboarding overlay -->
        <OnboardingOverlay v-if="showOnboarding" />
      </SidebarProvider>
      <div v-else class="min-h-screen bg-background">
        <RouterView />
      </div>
    </template>
  </TooltipProvider>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick, onMounted } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import SideNav from '@/shared/components/SideNav.vue'
import OnboardingOverlay from '@/features/onboarding/components/OnboardingOverlay.vue'
import { Toaster } from '@/components/ui/toast'
import { SidebarProvider, SidebarInset, SidebarTrigger } from '@/components/ui/sidebar'
import { TooltipProvider } from '@/components/ui/tooltip'
import { Loader2, Monitor, Calendar } from 'lucide-vue-next'
import { useMediaQuery } from '@vueuse/core'
import { useOnboardingStatusQuery } from '@/features/onboarding/queries'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isMobile = useMediaQuery('(max-width: 768px)')
const isReady = ref(false)
const isAuthTransitioning = ref(false)

onMounted(async () => {
  await router.isReady()
  isReady.value = true
})

// When auth state flips from false -> true during the session (login/signup),
// briefly hold a loading screen so the nav and content render together.
// Only activate transition during an actual login/signup, not on page-load token restore.
// On restore, the user is already on an authenticated route -- no navigation follows.
watch(() => authStore.isAuthenticated, (isAuth, wasAuth) => {
  if (isAuth && !wasAuth) {
    const currentRoute = router.currentRoute.value.name
    if (currentRoute === 'login' || currentRoute === 'signup') {
      isAuthTransitioning.value = true
      const unregister = router.afterEach((to) => {
        // Only clear once we've landed on the actual destination, not an intermediate redirect
        if (to.name !== 'login' && to.name !== 'signup') {
          unregister()
          nextTick(() => {
            isAuthTransitioning.value = false
          })
        }
      })
    }
  }
})

const showNav = computed(() => {
  const isDemoRoute = route.path.startsWith('/demo') || route.path.startsWith('/example-app')
  if (isDemoRoute) return false
  return authStore.isAuthenticated && route.name !== 'login' && route.name !== 'signup'
})

const { data: onboardingStatus, isLoading: isLoadingOnboarding, isError: isOnboardingError } = useOnboardingStatusQuery(
  computed(() => showNav.value)
)

const showOnboarding = computed(() => {
  if (!showNav.value) return false
  // Don't show overlay on error - expired session should redirect to login, not block UI
  if (isOnboardingError.value) return false
  if (isLoadingOnboarding.value) return false
  if (!onboardingStatus.value) return false
  const steps = onboardingStatus.value.data?.completedSteps ?? []
  const intakeDone = steps.includes('intake_completed') || steps.includes('intake_skipped')
  const modeDone = steps.includes('mode_selected')
  return !intakeDone || !modeDone
})
</script>
