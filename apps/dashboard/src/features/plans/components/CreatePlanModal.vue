<template>
  <Dialog :open="visible" @update:open="emit('update:visible', $event)">
    <DialogContent class="sm:max-w-[500px]">
      <DialogHeader>
        <DialogTitle>Create New Plan</DialogTitle>
        <DialogDescription>
          Plans are created as drafts. Set pricing and add features on the next page.
        </DialogDescription>
      </DialogHeader>

      <form @submit.prevent="onSubmit" class="flex flex-col gap-6">
        <div class="grid grid-cols-2 gap-5">
          <div class="flex flex-col gap-2">
            <Label for="name">Name *</Label>
            <Input
              id="name"
              v-model="name"
              :class="{ 'border-destructive': errors.name }"
              placeholder="e.g., Basic Plan"
            />
            <span v-if="errors.name" class="text-destructive text-xs">{{ errors.name }}</span>
          </div>

          <div class="flex flex-col gap-2">
            <Label for="key">Key *</Label>
            <Input
              id="key"
              v-model="key"
              :class="{ 'border-destructive': errors.key }"
              placeholder="e.g., plan_basic"
              @input="keyManuallyEdited = true"
            />
            <span v-if="errors.key" class="text-destructive text-xs">{{ errors.key }}</span>
            <span v-else class="text-xs text-muted-foreground">Locked once the plan is activated.</span>
          </div>

          <div class="flex flex-col gap-2 col-span-2">
            <Label for="description">Description *</Label>
            <Textarea
              id="description"
              v-model="description"
              :class="{ 'border-destructive': errors.description }"
              placeholder="Plan description"
              rows="2"
            />
            <span v-if="errors.description" class="text-destructive text-xs">{{
              errors.description
            }}</span>
          </div>

          <div class="flex flex-col gap-2 col-span-2">
            <Label for="intervalMonths">Billing Interval *</Label>
            <Select v-model="intervalMonths">
              <SelectTrigger :class="{ 'border-destructive': errors.intervalMonths }">
                <SelectValue placeholder="Select interval" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="1">Monthly</SelectItem>
                <SelectItem value="3">Quarterly</SelectItem>
                <SelectItem value="6">Semi-annual</SelectItem>
                <SelectItem value="12">Annual</SelectItem>
              </SelectContent>
            </Select>
            <span v-if="errors.intervalMonths" class="text-destructive text-xs">{{
              errors.intervalMonths
            }}</span>
          </div>
        </div>

        <Collapsible v-model:open="showMetadata">
          <CollapsibleTrigger class="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground">
            <ChevronRight class="h-4 w-4 transition-transform" :class="{ 'rotate-90': showMetadata }" />
            Custom Fields
          </CollapsibleTrigger>
          <CollapsibleContent class="pt-3">
            <MetadataEditor v-model="metadata" label="" />
          </CollapsibleContent>
        </Collapsible>

        <Alert v-if="errorMessage" variant="destructive">
          <AlertCircle class="h-4 w-4" />
          <AlertDescription>{{ errorMessage }}</AlertDescription>
        </Alert>
      </form>

      <DialogFooter>
        <Button variant="outline" @click="close" :disabled="isCreatingPlan"> Cancel </Button>
        <Button @click="onSubmit" :disabled="isCreatingPlan">
          <Loader2 v-if="isCreatingPlan" class="mr-2 h-4 w-4 animate-spin" />
          {{ isCreatingPlan ? 'Creating...' : 'Create Plan' }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import { Alert, AlertDescription } from '@/components/ui/alert'
import {
  Collapsible,
  CollapsibleTrigger,
  CollapsibleContent
} from '@/components/ui/collapsible'
import { AlertCircle, ChevronRight, Loader2 } from 'lucide-vue-next'
import { parseApiError } from '@/lib/parseApiError'
import { useTracking } from '@/lib/tracking'
import { createPlanSchema } from '../schemas'
import { useCreatePlanMutation } from '../mutations'
import MetadataEditor from '@/shared/components/MetadataEditor.vue'

defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'create:success': [plan: { id: string; name: string }]
}>()

const { track } = useTracking()
const { mutateAsync: createPlanApi, isPending: isCreatingPlan } = useCreatePlanMutation()

const errorMessage = ref<string | null>(null)
const keyManuallyEdited = ref(false)
const showMetadata = ref(false)
const metadata = ref<Record<string, unknown> | null>(null)

const { defineField, handleSubmit, errors, resetForm, setFieldError } = useForm({
  validationSchema: toTypedSchema(createPlanSchema)
})

const [key] = defineField('key')
const [name] = defineField('name')
const [description] = defineField('description')
const [intervalMonths] = defineField('intervalMonths')

const onSubmit = handleSubmit(async (values) => {
  try {
    errorMessage.value = null

    const planData = {
      name: values.name,
      key: values.key,
      description: values.description,
      intervalMonths: values.intervalMonths,
      status: 'draft',
      // Default values - base price configured on plan detail page
      priceAmount: 0,
      billingTiming: 'IN_ADVANCE',
      metadata: metadata.value
    }
    const response = await createPlanApi(planData as unknown as typeof values)

    // API returns { data: { uuid, name, ... }, success: true } - handle both uuid and id
    const responseData =
      (response as { data?: Record<string, unknown> })?.data ||
      (response as Record<string, unknown>)
    const planId = (responseData?.uuid || responseData?.id) as string | undefined
    const planName = responseData?.name as string | undefined

    track('plan_created')
    toast({ title: 'Success', description: 'Plan created successfully' })

    if (planId) {
      emit('create:success', { id: planId, name: planName || values.name })
    } else {
      console.error('Plan created but response did not include an ID:', response)
    }

    close()
  } catch (error) {
    console.error('Create Plan error:', error)
    const parsedError = parseApiError(error)
    errorMessage.value = parsedError.message

    // Highlight the key field for duplicate errors
    if (parsedError.type === 'duplicate') {
      setFieldError('key', parsedError.message)
    }
  }
})

function close() {
  resetForm()
  errorMessage.value = null
  keyManuallyEdited.value = false
  showMetadata.value = false
  metadata.value = null
  emit('update:visible', false)
}
</script>
