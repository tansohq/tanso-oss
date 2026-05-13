<template>
  <div class="bg-card rounded-lg border shadow-sm p-5">
    <h3 class="text-sm font-semibold mb-4">Change Password</h3>

    <div v-if="isSandbox" class="relative">
      <div class="opacity-40 pointer-events-none select-none space-y-4" aria-hidden="true">
        <div class="flex flex-col gap-2">
          <Label class="font-medium">Current Password</Label>
          <Input disabled type="password" placeholder="Enter current password" />
        </div>
        <div class="flex flex-col gap-2">
          <Label class="font-medium">New Password</Label>
          <Input disabled type="password" placeholder="Enter new password (min 8 characters)" />
        </div>
        <div class="flex flex-col gap-2">
          <Label class="font-medium">Confirm New Password</Label>
          <Input disabled type="password" placeholder="Confirm new password" />
        </div>
        <Button disabled>Change Password</Button>
      </div>
      <div class="absolute inset-0 flex items-center justify-center">
        <div class="bg-card/90 border rounded-lg px-4 py-3 shadow-sm text-center max-w-sm">
          <p class="text-sm font-medium text-foreground">Password changes are not available in sandbox.</p>
          <p class="text-sm text-muted-foreground mt-1">Please make this change in the production dashboard.</p>
        </div>
      </div>
    </div>

    <form v-else @submit="onSubmit" class="space-y-4">
      <div class="flex flex-col gap-2">
        <Label for="currentPassword" class="font-medium">Current Password</Label>
        <div class="relative">
          <Input
            id="currentPassword"
            v-model="currentPassword"
            :type="showCurrentPassword ? 'text' : 'password'"
            :class="{ 'border-destructive focus-visible:ring-destructive': errors.currentPassword }"
            placeholder="Enter current password"
            class="pr-10"
          />
          <button
            type="button"
            class="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
            @click="showCurrentPassword = !showCurrentPassword"
          >
            <EyeOff v-if="showCurrentPassword" class="w-4 h-4" />
            <Eye v-else class="w-4 h-4" />
          </button>
        </div>
        <span v-if="errors.currentPassword" class="text-destructive text-sm">{{
          errors.currentPassword
        }}</span>
      </div>

      <div class="flex flex-col gap-2">
        <Label for="newPassword" class="font-medium">New Password</Label>
        <div class="relative">
          <Input
            id="newPassword"
            v-model="newPassword"
            :type="showNewPassword ? 'text' : 'password'"
            :class="{ 'border-destructive focus-visible:ring-destructive': errors.newPassword }"
            placeholder="Enter new password (min 8 characters)"
            class="pr-10"
          />
          <button
            type="button"
            class="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
            @click="showNewPassword = !showNewPassword"
          >
            <EyeOff v-if="showNewPassword" class="w-4 h-4" />
            <Eye v-else class="w-4 h-4" />
          </button>
        </div>
        <span v-if="errors.newPassword" class="text-destructive text-sm">{{
          errors.newPassword
        }}</span>
      </div>

      <div class="flex flex-col gap-2">
        <Label for="confirmNewPassword" class="font-medium">Confirm New Password</Label>
        <div class="relative">
          <Input
            id="confirmNewPassword"
            v-model="confirmNewPassword"
            :type="showConfirmPassword ? 'text' : 'password'"
            :class="{
              'border-destructive focus-visible:ring-destructive': errors.confirmNewPassword
            }"
            placeholder="Confirm new password"
            class="pr-10"
          />
          <button
            type="button"
            class="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
            @click="showConfirmPassword = !showConfirmPassword"
          >
            <EyeOff v-if="showConfirmPassword" class="w-4 h-4" />
            <Eye v-else class="w-4 h-4" />
          </button>
        </div>
        <span v-if="errors.confirmNewPassword" class="text-destructive text-sm">{{
          errors.confirmNewPassword
        }}</span>
      </div>

      <Alert v-if="successMessage" variant="default" class="border-green-200 bg-green-50 text-green-900">
        <CheckCircle2 class="h-4 w-4 text-green-600" />
        <AlertDescription>
          {{ successMessage }}
        </AlertDescription>
      </Alert>

      <Alert v-if="errorMessage" variant="destructive">
        <AlertCircle class="h-4 w-4" />
        <AlertDescription>
          {{ errorMessage }}
        </AlertDescription>
      </Alert>

      <Button type="submit" :disabled="isPending">
        <Loader2 v-if="isPending" class="mr-2 h-4 w-4 animate-spin" />
        Change Password
      </Button>
    </form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Eye, EyeOff, Loader2, AlertCircle, CheckCircle2 } from 'lucide-vue-next'
import { changePasswordSchema } from '@/features/account/schemas'
import { useChangePasswordMutation } from '@/features/account/mutations'
import { useTracking } from '@/lib/tracking'
import { useEnvironmentStore } from '@/stores/environment'

const { track } = useTracking()
const environmentStore = useEnvironmentStore()
const isSandbox = computed(() => environmentStore.isSandbox)

const showCurrentPassword = ref(false)
const showNewPassword = ref(false)
const showConfirmPassword = ref(false)
const successMessage = ref<string | null>(null)
const errorMessage = ref<string | null>(null)

const { errors, handleSubmit, defineField, resetForm } = useForm({
  validationSchema: toTypedSchema(changePasswordSchema)
})

const [currentPassword] = defineField('currentPassword')
const [newPassword] = defineField('newPassword')
const [confirmNewPassword] = defineField('confirmNewPassword')

const { mutate, isPending } = useChangePasswordMutation()

const onSubmit = handleSubmit((values) => {
  successMessage.value = null
  errorMessage.value = null

  mutate(
    {
      currentPassword: values.currentPassword,
      newPassword: values.newPassword
    },
    {
      onSuccess: () => {
        track('password_changed')
        successMessage.value = 'Password changed successfully'
        resetForm()
      },
      onError: (error) => {
        if (error instanceof Error) {
          errorMessage.value = error.message || 'Failed to change password'
        } else {
          errorMessage.value = 'An unexpected error occurred'
        }
      }
    }
  )
})
</script>
