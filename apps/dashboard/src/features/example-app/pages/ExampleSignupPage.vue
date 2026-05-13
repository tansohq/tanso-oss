<template>
  <div class="min-h-[80vh] flex items-center justify-center px-6">
    <div class="w-full max-w-md">
      <div class="text-center mb-8">
        <div class="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-indigo-100 text-indigo-600 mb-4">
          <component :is="activeTab === 'signup' ? UserPlus : LogIn" class="w-6 h-6" />
        </div>
        <h1 class="text-3xl font-bold tracking-tight mb-2">
          {{ activeTab === 'signup' ? 'Create your account' : 'Welcome back' }}
        </h1>
        <p class="text-muted-foreground">
          {{ activeTab === 'signup' ? 'Sign up to get started with Acme App' : 'Log in as an existing customer' }}
        </p>
      </div>

      <Card>
        <CardContent class="pt-6">
          <Tabs v-model="activeTab">
            <TabsList class="grid w-full grid-cols-2 mb-6">
              <TabsTrigger value="signup">Sign Up</TabsTrigger>
              <TabsTrigger value="login">Log In</TabsTrigger>
            </TabsList>

            <TabsContent value="signup">
              <form @submit.prevent="onSubmit" class="flex flex-col gap-4">
                <div class="grid grid-cols-2 gap-4">
                  <div class="flex flex-col gap-2">
                    <Label for="firstName">First Name</Label>
                    <Input
                      id="firstName"
                      v-model="firstName"
                      :class="{ 'border-destructive': errors.firstName }"
                      placeholder="John"
                    />
                    <span v-if="errors.firstName" class="text-destructive text-xs">
                      {{ errors.firstName }}
                    </span>
                  </div>

                  <div class="flex flex-col gap-2">
                    <Label for="lastName">Last Name</Label>
                    <Input
                      id="lastName"
                      v-model="lastName"
                      :class="{ 'border-destructive': errors.lastName }"
                      placeholder="Doe"
                    />
                    <span v-if="errors.lastName" class="text-destructive text-xs">
                      {{ errors.lastName }}
                    </span>
                  </div>
                </div>

                <div class="flex flex-col gap-2">
                  <Label for="email">Email</Label>
                  <Input
                    id="email"
                    v-model="email"
                    type="email"
                    :class="{ 'border-destructive': errors.email }"
                    placeholder="john@example.com"
                  />
                  <span v-if="errors.email" class="text-destructive text-xs">
                    {{ errors.email }}
                  </span>
                </div>

                <Alert v-if="errorMessage" variant="destructive">
                  <AlertCircle class="h-4 w-4" />
                  <AlertDescription>{{ errorMessage }}</AlertDescription>
                </Alert>

                <Button type="submit" class="w-full mt-2" :disabled="isSubmitting">
                  <Loader2 v-if="isSubmitting" class="mr-2 h-4 w-4 animate-spin" />
                  Sign Up
                </Button>
              </form>
            </TabsContent>

            <TabsContent value="login">
              <div class="flex flex-col gap-4">
                <div v-if="customersLoading" class="flex justify-center py-8">
                  <Loader2 class="h-6 w-6 animate-spin text-muted-foreground" />
                </div>

                <template v-else-if="customers.length > 0">
                  <div class="flex flex-col gap-2">
                    <Label>Select a customer</Label>
                    <Select v-model="selectedCustomerRef">
                      <SelectTrigger>
                        <SelectValue placeholder="Choose a customer..." />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem
                          v-for="customer in customers"
                          :key="customer.referenceId"
                          :value="customer.referenceId"
                        >
                          {{ customer.firstName }} {{ customer.lastName }} ({{ customer.email }})
                        </SelectItem>
                      </SelectContent>
                    </Select>
                  </div>

                  <Button
                    class="w-full mt-2"
                    :disabled="!selectedCustomerRef || isLoggingIn"
                    @click="handleLogin"
                  >
                    <Loader2 v-if="isLoggingIn" class="mr-2 h-4 w-4 animate-spin" />
                    Log In
                  </Button>
                </template>

                <p v-else class="text-sm text-muted-foreground text-center py-8">
                  No customers found. Create one using the Sign Up tab.
                </p>
              </div>
            </TabsContent>
          </Tabs>
        </CardContent>
      </Card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { z } from 'zod'
import { toast } from '@/components/ui/toast/use-toast'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent } from '@/components/ui/card'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { AlertCircle, Loader2, UserPlus, LogIn } from 'lucide-vue-next'
import { parseApiError } from '@/lib/parseApiError'
import { useCreateCustomerMutation } from '@/features/customers/mutations'
import { useCustomersQuery } from '@/features/customers/queries'
import { fetchCustomers } from '@/features/customers/api'
import { fetchCustomerSubscriptions } from '@/features/subscriptions/api'
import { useExampleAppState } from '../composables/useExampleAppState'

const router = useRouter()
const { setCustomer, setSubscription } = useExampleAppState()
const { mutateAsync, isPending } = useCreateCustomerMutation()

const isSubmitting = computed(() => isPending.value)
const isLoggingIn = ref(false)
const errorMessage = ref<string | null>(null)

// Tabs
const activeTab = ref('signup')

// Login state
const selectedCustomerRef = ref<string>('')
const { data: customersData, isLoading: customersLoading } = useCustomersQuery()
const customers = computed(() => customersData.value?.data?.customers ?? [])

const signupSchema = z.object({
  firstName: z.string().min(1, 'Please enter your first name'),
  lastName: z.string().min(1, 'Please enter your last name'),
  email: z.string().email('Please enter a valid email address'),
})

const { defineField, handleSubmit, errors, setFieldError } = useForm({
  validationSchema: toTypedSchema(signupSchema),
})

const [firstName] = defineField('firstName')
const [lastName] = defineField('lastName')
const [email] = defineField('email')

const onSubmit = handleSubmit(async (values) => {
  try {
    errorMessage.value = null
    const referenceId = `example_${Date.now()}`
    await mutateAsync({
      customerReferenceId: referenceId,
      firstName: values.firstName,
      lastName: values.lastName,
      email: values.email,
    })

    // Fetch customers to get the internal UUID for the newly created customer
    let uuid: string | undefined
    try {
      const allCustomers = await fetchCustomers()
      const created = allCustomers.data?.customers?.find(c => c.referenceId === referenceId)
      uuid = created?.id
    } catch {
      // Continue without UUID — subscription creation will still work via login
    }

    setCustomer(referenceId, `${values.firstName} ${values.lastName}`, values.email, uuid)
    toast({ title: 'Account created', description: 'Welcome to Acme App!' })
    router.push({ name: 'example-pricing' })
  } catch (error) {
    const parsedError = parseApiError(error)
    errorMessage.value = parsedError.message
    if (parsedError.type === 'duplicate' && parsedError.message.toLowerCase().includes('email')) {
      setFieldError('email', parsedError.message)
    }
  }
})

async function handleLogin() {
  const customer = customers.value.find(c => c.referenceId === selectedCustomerRef.value)
  if (!customer) return

  isLoggingIn.value = true
  try {
    setCustomer(
      customer.referenceId,
      `${customer.firstName} ${customer.lastName}`,
      customer.email,
      customer.id
    )

    // Check if this customer already has an active subscription
    try {
      const subsResponse = await fetchCustomerSubscriptions(customer.id)
      const activeSub = (subsResponse.data ?? []).find(s => s.isActive)
      if (activeSub) {
        setSubscription(
          activeSub.id,
          activeSub.plan.id,
          activeSub.plan.name,
          activeSub.plan.priceAmount ?? 0,
          'USD'
        )
        toast({ title: 'Logged in', description: `Welcome back, ${customer.firstName}! Your ${activeSub.plan.name} subscription is active.` })
        router.push({ name: 'example-dashboard' })
        return
      }
    } catch {
      // No subscriptions or fetch failed — continue to pricing
    }

    toast({ title: 'Logged in', description: `Welcome back, ${customer.firstName}!` })
    router.push({ name: 'example-pricing' })
  } finally {
    isLoggingIn.value = false
  }
}
</script>
