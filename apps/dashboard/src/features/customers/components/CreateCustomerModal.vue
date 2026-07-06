<template>
  <Dialog :open="visible" @update:open="emit('update:visible', $event)">
    <DialogContent class="sm:max-w-[600px]">
      <DialogHeader>
        <DialogTitle>Create New Customer</DialogTitle>
        <DialogDescription>Register a customer you can subscribe to plans and bill.</DialogDescription>
      </DialogHeader>

      <form @submit.prevent="onSubmit" class="flex flex-col gap-6">
        <div class="grid grid-cols-2 gap-5">
          <div class="flex flex-col gap-2">
            <Label for="customerReferenceId">Reference ID *</Label>
            <Input
              id="customerReferenceId"
              v-model="customerReferenceId"
              :class="{ 'border-destructive': errors.customerReferenceId }"
              placeholder="e.g., cust_12345"
            />
            <span v-if="errors.customerReferenceId" class="text-destructive text-xs">
              {{ errors.customerReferenceId }}
            </span>
          </div>

          <div class="flex flex-col gap-2">
            <Label for="email">Email *</Label>
            <Input
              id="email"
              v-model="email"
              type="email"
              :class="{ 'border-destructive': errors.email }"
              placeholder="e.g., john@example.com"
            />
            <span v-if="errors.email" class="text-destructive text-xs">{{ errors.email }}</span>
          </div>

          <div class="flex flex-col gap-2">
            <Label for="firstName">First Name *</Label>
            <Input
              id="firstName"
              v-model="firstName"
              :class="{ 'border-destructive': errors.firstName }"
              placeholder="e.g., John"
            />
            <span v-if="errors.firstName" class="text-destructive text-xs">{{
              errors.firstName
            }}</span>
          </div>

          <div class="flex flex-col gap-2">
            <Label for="lastName">Last Name *</Label>
            <Input
              id="lastName"
              v-model="lastName"
              :class="{ 'border-destructive': errors.lastName }"
              placeholder="e.g., Doe"
            />
            <span v-if="errors.lastName" class="text-destructive text-xs">{{
              errors.lastName
            }}</span>
          </div>

          <div class="flex flex-col gap-2">
            <Label for="phoneNumber">Phone Number</Label>
            <Input
              id="phoneNumber"
              v-model="phoneNumber"
              :class="{ 'border-destructive': errors.phoneNumber }"
              placeholder="e.g., +1 555-0123"
            />
            <span v-if="errors.phoneNumber" class="text-destructive text-xs">{{
              errors.phoneNumber
            }}</span>
          </div>

          <div class="flex flex-col gap-2 col-span-2">
            <Label for="address">Address</Label>
            <Textarea
              id="address"
              v-model="address"
              :class="{ 'border-destructive': errors.address }"
              placeholder="Customer address"
              rows="2"
            />
            <span v-if="errors.address" class="text-destructive text-xs">{{ errors.address }}</span>
          </div>
        </div>

        <Alert v-if="errorMessage" variant="destructive">
          <AlertCircle class="h-4 w-4" />
          <AlertDescription>{{ errorMessage }}</AlertDescription>
        </Alert>
      </form>

      <DialogFooter>
        <Button variant="outline" @click="close" :disabled="isSubmitting"> Cancel </Button>
        <Button @click="onSubmit" :disabled="isSubmitting">
          <Loader2 v-if="isSubmitting" class="mr-2 h-4 w-4 animate-spin" />
          Create Customer
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { toast } from '@/components/ui/toast/use-toast'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { AlertCircle, Loader2 } from 'lucide-vue-next'
import { parseApiError } from '@/lib/parseApiError'
import { useTracking } from '@/lib/tracking'
import { createCustomerSchema } from '../schemas'
import { useCreateCustomerMutation } from '../mutations'

defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const { track } = useTracking()
const { mutateAsync, isPending } = useCreateCustomerMutation()
const isSubmitting = computed(() => isPending.value)
const errorMessage = ref<string | null>(null)

const { defineField, handleSubmit, errors, resetForm, setFieldError } = useForm({
  validationSchema: toTypedSchema(createCustomerSchema)
})

const [customerReferenceId] = defineField('customerReferenceId')
const [firstName] = defineField('firstName')
const [lastName] = defineField('lastName')
const [email] = defineField('email')
const [phoneNumber] = defineField('phoneNumber')
const [address] = defineField('address')

const onSubmit = handleSubmit(async (values) => {
  try {
    errorMessage.value = null
    await mutateAsync(values)
    track('customer_created')
    toast({ title: 'Success', description: 'Customer created successfully' })
    resetForm()
    close()
  } catch (error) {
    const parsedError = parseApiError(error)
    errorMessage.value = parsedError.message
    toast({ title: 'Error', description: parsedError.message, variant: 'destructive' })

    // Highlight the specific field for duplicate errors
    if (parsedError.type === 'duplicate') {
      const lowerMessage = parsedError.message.toLowerCase()
      if (lowerMessage.includes('email')) {
        setFieldError('email', parsedError.message)
      } else if (lowerMessage.includes('reference')) {
        setFieldError('customerReferenceId', parsedError.message)
      }
    }
  }
})

function close() {
  resetForm()
  errorMessage.value = null
  emit('update:visible', false)
}
</script>
