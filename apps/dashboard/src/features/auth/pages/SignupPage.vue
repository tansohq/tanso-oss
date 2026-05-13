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

    <!-- Right: Signup form -->
    <div class="flex-1 flex flex-col items-center justify-center px-6 py-10 bg-white relative">
      <div class="w-full max-w-[400px]">
        <div class="lg:hidden mb-8">
          <h1 class="text-xl font-bold text-slate-900">Tanso</h1>
        </div>

        <h2 class="text-2xl font-bold text-slate-900 mb-2">Create your account</h2>
        <p class="text-slate-500 text-sm mb-8">Free to get started. No credit card required.</p>

        <form @submit.prevent="onSubmit" class="flex flex-col gap-5">
          <div class="flex flex-col gap-2 min-h-[68px]">
            <Label for="email" class="font-medium text-slate-900">Email</Label>
            <Input
              id="email"
              v-model="email"
              type="email"
              autocomplete="email"
              :class="{ 'border-destructive focus-visible:ring-destructive': errors.email }"
              placeholder="you@company.com"
            />
            <span v-if="errors.email" class="text-destructive text-sm">
              {{ errors.email }}
            </span>
          </div>

          <div class="flex flex-col gap-2 min-h-[68px]">
            <Label for="password" class="font-medium text-slate-900">Password</Label>
            <div class="relative">
              <Input
                id="password"
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="new-password"
                :class="{ 'border-destructive focus-visible:ring-destructive': errors.password }"
                placeholder="Create a password"
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
            <span v-if="errors.password" class="text-destructive text-sm">
              {{ errors.password }}
            </span>
          </div>

          <Alert v-if="signupErrorMessage" variant="destructive">
            <AlertCircle class="h-4 w-4" />
            <AlertDescription>
              {{ signupErrorMessage }}
            </AlertDescription>
          </Alert>

          <Button type="submit" :disabled="isPending" class="w-full mt-2 h-11">
            <Loader2 v-if="isPending" class="mr-2 h-4 w-4 animate-spin" />
            Start free
          </Button>
        </form>

        <p class="mt-6 text-center text-sm text-slate-500">
          Already have an account?
          <router-link :to="{ path: '/login', query: email && email.includes('@') ? { email } : {} }" class="text-primary hover:underline font-medium">
            Sign in
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
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Eye, EyeOff, Loader2, AlertCircle, Check } from 'lucide-vue-next'
import { signupSchema } from '../schemas'
import { useSignupMutation } from '../mutations'
import type { SignupRequest } from '../types'
import { useTracking } from '@/lib/tracking'

const route = useRoute()

const { track } = useTracking()
const showPassword = ref(false)

const signupErrorMessage = ref<string | null>(null)

const { errors, handleSubmit, defineField, setFieldError } = useForm({
  validationSchema: toTypedSchema(signupSchema),
  validateOnMount: false,
  initialValues: {
    email: typeof route.query.email === 'string' ? route.query.email : ''
  }
})

const validateOnBlur = { validateOnModelUpdate: false, validateOnBlur: true }

const [email] = defineField('email', validateOnBlur)
const [password] = defineField('password', validateOnBlur)


const { mutate, isPending } = useSignupMutation()

const onSubmit = handleSubmit((values) => {
  track('signup_submitted')
  signupErrorMessage.value = null

  const request: SignupRequest = {
    customerDetails: {
      email: values.email
    },
    password: values.password
  }

  mutate(request, {
    onError: (error) => {
      if (error instanceof Error) {
        const message = error.message.toLowerCase()
        const status = (error as Error & { response?: { status: number } }).response?.status
        if (status === 409 || message.includes('already exists') || message.includes('duplicate')) {
          signupErrorMessage.value = 'An account with this email already exists.'
          setFieldError('email', 'This email is already registered')
        } else if (message.includes('network') || message.includes('fetch')) {
          signupErrorMessage.value = 'Unable to connect to server. Please check your connection.'
        } else {
          signupErrorMessage.value = 'Signup failed. Please try again.'
        }
      } else {
        signupErrorMessage.value = 'An unexpected error occurred. Please try again.'
      }
    }
  })
})
</script>
