<template>
  <div class="p-6 pb-16">
    <!-- Breadcrumb -->
    <div class="mb-6">
      <nav class="flex items-center gap-2 text-sm text-muted-foreground">
        <router-link :to="demoPath('/features')" class="hover:text-foreground transition-colors">Features</router-link>
        <ChevronRight class="h-4 w-4" />
        <span class="text-foreground">{{ feature?.name || 'Feature' }}</span>
      </nav>
    </div>

    <!-- Loading State -->
    <div v-if="isLoading" class="flex items-center justify-center py-12">
      <Loader2 class="h-8 w-8 animate-spin text-muted-foreground" />
    </div>

    <!-- Error State -->
    <div v-else-if="isError" class="flex flex-col items-center justify-center py-12 bg-card rounded-lg border">
      <AlertCircle class="w-12 h-12 text-destructive mb-4" />
      <p class="text-lg font-medium text-foreground mb-2">Unable to load feature</p>
      <p class="text-sm text-muted-foreground mb-4">The feature could not be found or an error occurred.</p>
      <div class="flex gap-2">
        <Button variant="outline" @click="refetch">
          <RefreshCw class="w-4 h-4 mr-2" />
          Try Again
        </Button>
        <Button variant="outline" @click="router.push(demoPath('/features'))">
          <ArrowLeft class="w-4 h-4 mr-2" />
          Back to Features
        </Button>
      </div>
    </div>

    <div v-else-if="feature">
      <!-- Header -->
      <div class="flex items-center justify-between mb-6">
        <div>
          <h1 class="text-2xl font-semibold tracking-tight text-foreground">{{ feature.name }}</h1>
          <div class="flex items-center gap-1.5 mt-1">
            <span class="text-sm text-muted-foreground font-mono">{{ feature.key }}</span>
            <CopyButton :value="feature.key" label="Feature key" />
          </div>
        </div>
        <div class="flex items-center gap-2">
          <Button variant="outline" @click="openEditDialog">
            <Pencil class="h-4 w-4 mr-2" />
            Edit
          </Button>
          <Button
            variant="outline"
            class="text-destructive hover:text-destructive"
            @click="showDeleteDialog = true"
          >
            <Trash2 class="h-4 w-4 mr-2" />
            Delete
          </Button>
        </div>
      </div>

      <div class="space-y-6 max-w-5xl">
        <!-- Overview Card -->
        <Card>
          <CardHeader>
            <CardTitle class="text-base">Overview</CardTitle>
          </CardHeader>
          <CardContent>
            <dl class="grid grid-cols-2 gap-6 text-sm">
              <div>
                <dt class="text-muted-foreground">Key</dt>
                <dd class="font-mono">{{ feature.key }}</dd>
              </div>
              <div>
                <dt class="text-muted-foreground">Enabled</dt>
                <dd>
                  <Badge
                    :class="
                      feature.isEnabled
                        ? 'bg-emerald-50 text-emerald-700 border border-emerald-200/50'
                        : 'bg-gray-50 text-gray-600 border border-gray-200/50'"
                    class="shadow-none"
                  >
                    {{ feature.isEnabled ? 'Enabled' : 'Disabled' }}
                  </Badge>
                </dd>
              </div>
              <div class="col-span-2">
                <dt class="text-muted-foreground">Description</dt>
                <dd>{{ feature.description || '\u2014' }}</dd>
              </div>
              <div>
                <dt class="text-muted-foreground">Created At</dt>
                <dd class="tabular-nums">{{ formatDate(feature.createdAt) }}</dd>
              </div>
              <div>
                <dt class="text-muted-foreground">Modified At</dt>
                <dd class="tabular-nums">{{ formatDate(feature.modifiedAt) }}</dd>
              </div>
            </dl>
          </CardContent>
        </Card>

        <!-- Plans Using This Feature -->
        <Card>
          <CardHeader>
            <CardTitle class="text-base">Plans Using This Feature</CardTitle>
          </CardHeader>
          <CardContent>
            <div v-if="isPlansLoading" class="flex items-center justify-center py-8">
              <Loader2 class="h-6 w-6 animate-spin text-muted-foreground" />
            </div>

            <Table v-else-if="plansUsingFeature.length > 0">
              <TableHeader>
                <TableRow>
                  <TableHead>Plan Name</TableHead>
                  <TableHead>Feature Pricing</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow
                  v-for="planEntry in plansUsingFeature"
                  :key="planEntry.plan.id"
                  :class="{ 'cursor-pointer hover:bg-muted/50': !isDemo }"
                  @click="!isDemo && router.push(demoPath(`/plans/${planEntry.plan.id}`))"
                >
                  <TableCell class="font-medium">{{ planEntry.plan.name }}</TableCell>
                  <TableCell>
                    <Badge class="bg-gray-50 text-gray-600 border border-gray-200/50 shadow-none">
                      {{ planEntry.pricingLabel }}
                    </Badge>
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>

            <p v-else class="text-sm text-muted-foreground text-center py-8">
              This feature isn't used by any plans.
            </p>
          </CardContent>
        </Card>
      </div>
    </div>

    <!-- Edit Feature Dialog -->
    <Dialog :open="showEditDialog" @update:open="showEditDialog = $event">
      <DialogContent class="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>Edit Feature</DialogTitle>
          <DialogDescription>Update the feature details</DialogDescription>
        </DialogHeader>

        <div class="flex flex-col gap-4 py-4">
          <div class="flex flex-col gap-1.5">
            <Label>Name *</Label>
            <Input v-model="editName" placeholder="e.g., Advanced Analytics" />
          </div>

          <div class="flex flex-col gap-1.5">
            <Label>Key *</Label>
            <Input v-model="editKey" placeholder="e.g., advanced_analytics" class="font-mono" />
            <p class="text-xs text-muted-foreground">
              Used to identify this feature in your code and API calls
            </p>
          </div>

          <div class="flex flex-col gap-1.5">
            <Label>Description</Label>
            <Input v-model="editDescription" placeholder="Optional description..." />
          </div>

          <div class="flex items-center gap-2">
            <Switch v-model:checked="editIsEnabled" />
            <Label>{{ editIsEnabled ? 'Enabled' : 'Disabled' }}</Label>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" @click="showEditDialog = false">Cancel</Button>
          <Button @click="handleSaveEdit" :disabled="!canSaveEdit || isUpdating">
            <Loader2 v-if="isUpdating" class="h-4 w-4 mr-2 animate-spin" />
            Save Changes
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <!-- Delete Confirmation Dialog -->
    <AlertDialog :open="showDeleteDialog" @update:open="showDeleteDialog = $event">
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete Feature</AlertDialogTitle>
          <AlertDialogDescription>
            Are you sure you want to delete "{{ feature?.name }}"? This action cannot be undone.
            <span v-if="plansUsingFeature.length > 0" class="block mt-2 text-amber-600">
              Warning: This feature is currently used by {{ plansUsingFeature.length }} plan(s).
            </span>
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction
            @click="handleDelete"
            class="bg-destructive text-destructive-foreground hover:bg-destructive/90"
          >
            <Loader2 v-if="isDeleting" class="h-4 w-4 mr-2 animate-spin" />
            Delete
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ChevronRight,
  Pencil,
  Trash2,
  AlertCircle,
  RefreshCw,
  ArrowLeft,
  Loader2
} from 'lucide-vue-next'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from '@/components/ui/alert-dialog'
import CopyButton from '@/components/CopyButton.vue'
import { useFeatureQuery } from '../queries'
import { useUpdateFeatureMutation, useDeleteFeatureMutation } from '../mutations'
import { usePlansQuery } from '@/features/plans/queries'
import { getPlanFeatures } from '@/features/plans/api'
import type { Plan } from '@/features/plans/types'
import type { Feature } from '../types'
import { formatDate } from '@/lib/formatters'
import { useDemoPrefix } from '@/lib/useDemoPrefix'
import { toast } from '@/components/ui/toast/use-toast'

interface PlanWithPricing {
  plan: Plan
  pricingLabel: string
}

const route = useRoute()
const router = useRouter()
const { demoPath, isDemo } = useDemoPrefix()

const featureId = computed(() => route.params.id as string)

const { data: featureData, isLoading, isError, refetch } = isDemo.value
  ? { data: ref(null), isLoading: ref(false), isError: ref(false), refetch: () => {} }
  : useFeatureQuery(featureId)

const DEMO_FEATURE: Feature = {
  id: 'feat_1', name: 'API Calls', key: 'api_calls', description: 'Track and meter API usage per customer',
  isEnabled: true, createdAt: '2025-06-15T10:00:00Z', modifiedAt: '2025-09-01T10:00:00Z'
}

const feature = computed<Feature | null>(() => {
  if (isDemo.value) return DEMO_FEATURE
  return featureData.value?.data || null
})

// Plans query
const { data: plansData, isLoading: isPlansQueryLoading } = isDemo.value
  ? { data: ref(null), isLoading: ref(false) }
  : usePlansQuery()

const allPlans = computed<Plan[]>(() => {
  if (!plansData.value) return []
  if (plansData.value.data) {
    if ('items' in plansData.value.data && Array.isArray(plansData.value.data.items)) {
      return plansData.value.data.items
    }
    if (Array.isArray(plansData.value.data)) {
      return plansData.value.data
    }
  }
  return []
})

// Fetch plan features to find which plans use this feature
const planFeatureMap = ref<Map<string, PlanWithPricing>>(new Map())
const isFetchingPlanFeatures = ref(false)
let planFeaturesFetchId = 0

watch(
  [allPlans, featureId],
  async ([plans, fId]) => {
    if (isDemo.value) {
      planFeatureMap.value = new Map([
        ['plan_1', { plan: { id: 'plan_1', key: 'pro_monthly', name: 'Pro', description: null, priceAmount: 4900, intervalMonths: '1', billingTiming: 'IN_ADVANCE', createdAt: '2025-06-01T00:00:00Z', modifiedAt: '2025-08-01T00:00:00Z' } as Plan, pricingLabel: 'Per Unit' }],
        ['plan_2', { plan: { id: 'plan_2', key: 'enterprise_monthly', name: 'Enterprise', description: null, priceAmount: 24900, intervalMonths: '1', billingTiming: 'IN_ADVANCE', createdAt: '2025-06-01T00:00:00Z', modifiedAt: '2025-08-01T00:00:00Z' } as Plan, pricingLabel: 'Graduated' }],
      ])
      isFetchingPlanFeatures.value = false
      return
    }
    if (!plans.length || !fId) return

    const fetchId = ++planFeaturesFetchId
    isFetchingPlanFeatures.value = true
    const newMap = new Map<string, PlanWithPricing>()

    const promises = plans.map(async (plan) => {
      try {
        const response = await getPlanFeatures(plan.id)
        const features = response?.data?.features || []
        const match = features.find((f: { id: string }) => f.id === fId)
        if (match) {
          const model = (match as { value?: { model?: string } }).value?.model
          let pricingLabel = 'Included'
          if (model === 'usage') pricingLabel = 'Per Unit'
          else if (model === 'graduated') pricingLabel = 'Graduated'

          newMap.set(plan.id, { plan, pricingLabel })
        }
      } catch {
        // Plan features fetch failed, skip
      }
    })

    await Promise.all(promises)
    // Only apply results if this is still the latest fetch
    if (fetchId === planFeaturesFetchId) {
      planFeatureMap.value = newMap
      isFetchingPlanFeatures.value = false
    }
  },
  { immediate: true }
)

const isPlansLoading = computed(() => isPlansQueryLoading.value || isFetchingPlanFeatures.value)

const plansUsingFeature = computed<PlanWithPricing[]>(() => {
  return Array.from(planFeatureMap.value.values())
})

// Edit dialog state
const showEditDialog = ref(false)
const editName = ref('')
const editKey = ref('')
const editDescription = ref('')
const editIsEnabled = ref(true)

// Delete dialog state
const showDeleteDialog = ref(false)

// Mutations
const deleteMutation = isDemo.value
  ? { isPending: ref(false), mutateAsync: async () => {} }
  : useDeleteFeatureMutation()
const isDeleting = computed(() => deleteMutation.isPending.value)

// Update mutation needs a reactive featureId; we create it fresh on save
const isUpdating = ref(false)

// Initialize edit form when dialog opens
watch(
  [feature, showEditDialog],
  ([newFeature, isOpen]) => {
    if (newFeature && isOpen) {
      editName.value = newFeature.name
      editKey.value = newFeature.key
      editDescription.value = newFeature.description || ''
      editIsEnabled.value = newFeature.isEnabled
    }
  },
  { immediate: true }
)

const canSaveEdit = computed(() => {
  return !!(editName.value.trim() && editKey.value.trim())
})

function openEditDialog() {
  showEditDialog.value = true
}

async function handleSaveEdit() {
  if (!feature.value || !canSaveEdit.value) return

  isUpdating.value = true
  const mutation = useUpdateFeatureMutation(feature.value.id)

  try {
    await mutation.mutateAsync({
      name: editName.value.trim(),
      key: editKey.value.trim(),
      description: editDescription.value.trim(),
      isEnabled: editIsEnabled.value
    })

    toast({
      title: 'Feature updated',
      description: `Feature "${editName.value}" has been updated.`
    })

    showEditDialog.value = false
  } catch (error) {
    toast({
      title: 'Error',
      description: 'Failed to update feature. Please try again.',
      variant: 'destructive'
    })
  } finally {
    isUpdating.value = false
  }
}

async function handleDelete() {
  if (!feature.value) return

  const featureName = feature.value.name

  try {
    await deleteMutation.mutateAsync(feature.value.id)

    toast({
      title: 'Feature deleted',
      description: `Feature "${featureName}" has been deleted.`
    })

    router.push(demoPath('/features'))
  } catch (error) {
    toast({
      title: 'Error',
      description: 'Failed to delete feature. Please try again.',
      variant: 'destructive'
    })
  }
}
</script>
