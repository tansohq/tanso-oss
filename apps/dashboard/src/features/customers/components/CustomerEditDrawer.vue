<template>
  <Sheet :open="visible" @update:open="handleClose">
    <SheetContent class="w-full sm:max-w-lg flex flex-col h-full">
      <SheetHeader class="shrink-0">
        <SheetTitle>Edit Customer</SheetTitle>
        <SheetDescription v-if="customer">
          {{ customer.firstName }} {{ customer.lastName }}
        </SheetDescription>
      </SheetHeader>

      <form class="flex-1 overflow-y-auto mt-6 min-h-0 pb-4 -mx-1 px-1" @submit.prevent="onSubmit">
        <div class="space-y-6">
        <div class="grid grid-cols-2 gap-4">
          <div class="flex flex-col gap-2">
            <Label for="email" class="text-muted-foreground text-xs">Email *</Label>
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
            <Label for="firstName" class="text-muted-foreground text-xs">First Name *</Label>
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
            <Label for="lastName" class="text-muted-foreground text-xs">Last Name *</Label>
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
            <Label for="phoneNumber" class="text-muted-foreground text-xs">Phone Number</Label>
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
            <Label for="address" class="text-muted-foreground text-xs">Address</Label>
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
        </div>
      </form>

      <SheetFooter class="mt-6 shrink-0">
        <Button type="button" variant="outline" :disabled="isSubmitting" @click="handleClose(false)">
          Cancel
        </Button>
        <Button :disabled="isSubmitting" @click="onSubmit">
          <Loader2 v-if="isSubmitting" class="mr-2 h-4 w-4 animate-spin" />
          Save Changes
        </Button>
      </SheetFooter>
    </SheetContent>
  </Sheet>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { toast } from '@/components/ui/toast/use-toast'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle
} from '@/components/ui/sheet'
import { AlertCircle, Loader2 } from 'lucide-vue-next'
import { parseApiError } from '@/lib/parseApiError'
import { updateCustomerSchema } from '../schemas'
import { updateCustomer } from '../api'
import type { CustomerElement, UpdateCustomer } from '../types'

const props = defineProps<{
  visible: boolean
  customer: CustomerElement | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const queryClient = useQueryClient()
const errorMessage = ref<string | null>(null)

const { mutateAsync, isPending } = useMutation({
  mutationFn: (data: { customerId: string; customerData: UpdateCustomer }) =>
    updateCustomer(data.customerId, data.customerData),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['customers'] })
  }
})

const isSubmitting = computed(() => isPending.value)

const { defineField, handleSubmit, errors, setValues, setFieldError } = useForm({
  validationSchema: toTypedSchema(updateCustomerSchema)
})

const [firstName] = defineField('firstName')
const [lastName] = defineField('lastName')
const [email] = defineField('email')
const [phoneNumber] = defineField('phoneNumber')
const [address] = defineField('address')

watch(
  () => props.customer,
  (newCustomer) => {
    if (newCustomer) {
      setValues({
        firstName: newCustomer.firstName,
        lastName: newCustomer.lastName,
        email: newCustomer.email,
        phoneNumber: '',
        address: ''
      })
    }
  },
  { immediate: true }
)

watch(
  () => props.visible,
  (visible) => {
    if (!visible) {
      errorMessage.value = null
    }
  }
)

const onSubmit = handleSubmit(async (values) => {
  if (!props.customer?.id) {
    errorMessage.value = 'No customer selected'
    return
  }

  try {
    errorMessage.value = null
    await mutateAsync({ customerId: props.customer.id, customerData: values })
    toast({ title: 'Success', description: 'Customer updated successfully' })
    handleClose(false)
  } catch (error) {
    const parsedError = parseApiError(error)
    errorMessage.value = parsedError.message
    toast({ title: 'Error', description: parsedError.message, variant: 'destructive' })

    if (parsedError.type === 'duplicate') {
      const lowerMessage = parsedError.message.toLowerCase()
      if (lowerMessage.includes('email')) {
        setFieldError('email', parsedError.message)
      }
    }
  }
})

function handleClose(open: boolean) {
  if (!open) {
    errorMessage.value = null
  }
  emit('update:visible', open)
}
</script>
