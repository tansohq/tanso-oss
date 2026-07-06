<template>
  <div class="flex min-h-screen">
    <!-- Left panel -->
    <div class="hidden lg:flex lg:w-[55%] bg-[#0a0d12] text-white flex-col justify-center gap-16 p-12 relative overflow-hidden">
      <!-- Carbon grid -->
      <div
        class="absolute inset-0 pointer-events-none"
        style="background-image: linear-gradient(to right, hsl(172 40% 30% / 0.12) 1px, transparent 1px), linear-gradient(to bottom, hsl(172 40% 30% / 0.12) 1px, transparent 1px); background-size: 56px 56px; mask-image: radial-gradient(ellipse 80% 60% at center, black 0%, transparent 70%); -webkit-mask-image: radial-gradient(ellipse 80% 60% at center, black 0%, transparent 70%)"
      />
      <!-- Gradient glow -->
      <div class="absolute inset-0 pointer-events-none" style="background: radial-gradient(ellipse 80% 60% at 50% 0%, rgba(45,212,170,0.08) 0%, transparent 60%)" />

      <div class="relative z-10">
        <h1 class="text-xl font-bold tracking-tight text-white/90">Tanso</h1>
      </div>

      <div class="relative z-10 space-y-10 max-w-md">
        <div class="space-y-4">
          <h2 class="text-[2.5rem] font-bold leading-[1.1] tracking-[-0.03em] text-white">
            See what your AI costs. Control what it earns.
          </h2>
          <p class="text-white/60 text-lg leading-relaxed">
            Per-customer, per-feature, per-model.
          </p>
        </div>

        <div class="space-y-3.5">
          <div class="flex items-center gap-3">
            <Check class="w-4 h-4 text-[#2dd4aa] flex-shrink-0" />
            <p class="text-[15px] text-white/80">Know what every customer costs you</p>
          </div>
          <div class="flex items-center gap-3">
            <Check class="w-4 h-4 text-[#2dd4aa] flex-shrink-0" />
            <p class="text-[15px] text-white/80">See which features destroy margin</p>
          </div>
          <div class="flex items-center gap-3">
            <Check class="w-4 h-4 text-[#2dd4aa] flex-shrink-0" />
            <p class="text-[15px] text-white/80">Set usage limits, manage billing, control access</p>
          </div>
        </div>
      </div>

      <!-- Example prompts -->
      <div class="relative z-10 space-y-4">
        <p class="text-xs font-medium uppercase tracking-wider text-white/60">From insights to action</p>
        <div class="space-y-2.5">
          <div class="rounded-lg border border-white/[0.06] bg-white/[0.03] px-4 py-3">
            <p class="text-sm text-white/60 leading-relaxed">"Show me cost and margin per feature for the last 30 days."</p>
          </div>
          <div class="rounded-lg border border-white/[0.06] bg-white/[0.03] px-4 py-3">
            <p class="text-sm text-white/60 leading-relaxed">"Set a usage cap on our Pro tier and bill overages at $0.002 per request."</p>
          </div>
        </div>
        <div class="flex items-center gap-2 pt-1">
          <div class="w-6 h-6 rounded-md border border-white/[0.08] flex items-center justify-center bg-white/[0.03]">
            <img src="/images/claude-color.png" alt="Claude" class="w-3.5 h-3.5 object-contain" />
          </div>
          <div class="w-6 h-6 rounded-md border border-white/[0.08] flex items-center justify-center bg-white/[0.03]">
            <img src="/images/cursor-logo.png" alt="Cursor" class="w-3.5 h-3.5 object-contain" />
          </div>
          <div class="w-6 h-6 rounded-md border border-white/[0.08] flex items-center justify-center bg-white/[0.03]">
            <img src="/images/windsurf-logo.png" alt="Windsurf" class="w-5 h-5 object-contain invert" />
          </div>
          <span class="text-xs text-white/50 ml-1">via MCP</span>
        </div>
      </div>
    </div>

    <!-- Right: Login form -->
    <div class="flex-1 flex flex-col items-center justify-center px-6 py-10 bg-white relative">
      <div class="w-full max-w-[400px]">
        <div class="lg:hidden mb-8">
          <h1 class="text-xl font-bold text-slate-900">Tanso</h1>
        </div>

        <h2 class="text-2xl font-bold text-slate-900 mb-2">Welcome back</h2>
        <p class="text-slate-500 text-sm mb-8">Sign in to your account</p>

        <form @submit.prevent="onSubmit" class="flex flex-col gap-5">
          <div class="flex flex-col gap-2 min-h-[68px]">
            <Label for="username" class="font-medium text-slate-900">Email</Label>
            <Input
              id="username"
              v-model="username"
              type="email"
              autocomplete="email"
              :class="{ 'border-destructive focus-visible:ring-destructive': errors.username }"
              placeholder="you@company.com"
            />
            <span v-if="errors.username" class="text-destructive text-sm">{{ errors.username }}</span>
          </div>

          <div class="flex flex-col gap-2 min-h-[68px]">
            <Label for="password" class="font-medium text-slate-900">Password</Label>
            <div class="relative">
              <Input
                id="password"
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                :class="{ 'border-destructive focus-visible:ring-destructive': errors.password }"
                placeholder="Enter password"
                class="pr-10"
              />
              <button
                type="button"
                class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                @click="showPassword = !showPassword"
              >
                <EyeOff v-if="showPassword" class="w-4 h-4" />
                <Eye v-else class="w-4 h-4" />
              </button>
            </div>
            <p v-if="!errors.password" class="text-xs text-muted-foreground -mt-0.5">Must be at least 8 characters</p>
            <span v-if="errors.password" class="text-destructive text-sm">{{ errors.password }}</span>
          </div>

          <Alert v-if="loginErrorMessage" variant="destructive">
            <AlertCircle class="h-4 w-4" />
            <AlertDescription>
              {{ loginErrorMessage }}
            </AlertDescription>
          </Alert>

          <Button type="submit" :disabled="isPending" class="w-full mt-2 h-11">
            <Loader2 v-if="isPending" class="mr-2 h-4 w-4 animate-spin" />
            Sign in
          </Button>
        </form>

        <p class="mt-6 text-center text-sm text-slate-500">
          Don't have an account?
          <a
            v-if="isSandbox"
            :href="PRODUCTION_SIGNUP_URL"
            class="text-primary hover:underline font-medium"
          >
            Sign up
          </a>
          <router-link v-else :to="{ path: '/signup', query: username && username.includes('@') ? { email: username } : {} }" class="text-primary hover:underline font-medium">
            Sign up
          </router-link>
        </p>
      </div>

      <footer class="absolute bottom-0 left-0 right-0 py-4 text-center text-xs text-slate-400">
        <a href="#" target="_blank" class="hover:text-slate-500">Terms of Service</a>
        <span class="mx-2">&middot;</span>
        <a href="#" target="_blank" class="hover:text-slate-500">Privacy Policy</a>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Eye, EyeOff, Loader2, AlertCircle, Check } from 'lucide-vue-next'
import { loginSchema } from '../schemas'
import { useLoginMutation } from '../mutations'
import { useEnvironmentStore } from '@/stores/environment'
import { useTracking } from '@/lib/tracking'

const PRODUCTION_SIGNUP_URL = '/signup'
const route = useRoute()
const environmentStore = useEnvironmentStore()
const isSandbox = computed(() => environmentStore.isSandbox)

const { track } = useTracking()
const showPassword = ref(false)
const loginErrorMessage = ref<string | null>(null)

const { errors, handleSubmit, defineField } = useForm({
  validationSchema: toTypedSchema(loginSchema),
  initialValues: {
    username: typeof route.query.email === 'string' ? route.query.email : ''
  }
})

const [username] = defineField('username')
const [password] = defineField('password')

const { mutate, isPending } = useLoginMutation()

const onSubmit = handleSubmit((values) => {
  track('login_submitted')
  loginErrorMessage.value = null
  mutate(values, {
    onError: (error) => {
      if (error instanceof Error) {
        const msg = error.message.toLowerCase()
        const status = (error as Error & { response?: { status: number } }).response?.status
        if (status === 401 || msg.includes('unauthorized') || msg.includes('invalid credentials')) {
          loginErrorMessage.value = 'Incorrect username or password. Please try again.'
        } else if (msg.includes('network') || msg.includes('fetch') || msg.includes('failed to fetch')) {
          loginErrorMessage.value = 'Unable to connect to server. Please check your connection.'
        } else {
          loginErrorMessage.value = 'Something went wrong. Please try again.'
        }
      } else {
        loginErrorMessage.value = 'Something went wrong. Please try again.'
      }
    }
  })
})
</script>
