<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight text-foreground">Credit Models</h1>
        <p class="text-muted-foreground mt-1">Define credit denominations for usage-based billing</p>
      </div>
      <Button @click="showCreateModal = true">
        <Plus class="w-4 h-4 mr-2" />
        Create Credit Model
      </Button>
    </div>

    <div v-if="isLoading" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <div v-for="i in 3" :key="i" class="bg-card rounded-lg border p-5">
        <Skeleton class="h-5 w-32 mb-2" />
        <Skeleton class="h-4 w-20 mb-4" />
        <Skeleton class="h-3 w-28" />
      </div>
    </div>

    <div v-else-if="isError" class="flex flex-col items-center justify-center py-12 bg-card rounded-lg border">
      <AlertCircle class="w-12 h-12 text-destructive mb-4" />
      <p class="text-lg font-medium text-foreground mb-2">Unable to load credit models</p>
      <p class="text-sm text-muted-foreground mb-4">
        This is usually fixed by logging out and back in.
      </p>
      <div class="flex gap-2">
        <Button variant="outline" @click="refetch">
          <RefreshCw class="w-4 h-4 mr-2" />
          Try Again
        </Button>
        <Button variant="outline" @click="authStore.logout()">
          <LogOut class="w-4 h-4 mr-2" />
          Log out
        </Button>
      </div>
    </div>

    <div
      v-else-if="creditModels.length > 0"
      class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"
    >
      <CreditModelCard
        v-for="model in creditModels"
        :key="model.id"
        :model="model"
        @click="router.push(demoPath(`/credits/${model.id}`))"
        @delete="confirmDelete(model)"
      />
    </div>

    <div v-else class="flex flex-col items-center justify-center py-12 text-muted-foreground">
      <Inbox class="w-12 h-12 mb-4" />
      <p class="text-lg font-medium text-foreground mb-2">No credit models yet</p>
      <p class="text-sm mb-4">Create a credit model to start using credit-based billing</p>
      <Button @click="showCreateModal = true">
        <Plus class="w-4 h-4 mr-2" />
        Create Credit Model
      </Button>
    </div>

    <CreateCreditModelModal v-model:visible="showCreateModal" />

    <AlertDialog :open="showDeleteDialog" @update:open="showDeleteDialog = $event">
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete Credit Model</AlertDialogTitle>
          <AlertDialogDescription>
            Are you sure you want to delete "{{ modelToDelete?.name }}"? This action cannot be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <AlertDialogAction
            class="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            @click="handleDelete"
          >
            Delete
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
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
import { Plus, AlertCircle, RefreshCw, LogOut, Inbox } from 'lucide-vue-next'
import { toast } from '@/components/ui/toast/use-toast'
import { parseApiError } from '@/lib/parseApiError'
import { useAuthStore } from '@/stores/auth'
import { useCreditModelsQuery } from '../queries'
import { useDeleteCreditModelMutation } from '../mutations'
import CreditModelCard from '../components/CreditModelCard.vue'
import CreateCreditModelModal from '../components/CreateCreditModelModal.vue'
import type { CreditModel } from '../types'
import { useTracking } from '@/lib/tracking'
import { useDemoPrefix } from '@/lib/useDemoPrefix'

const router = useRouter()
const { isDemo, demoPath } = useDemoPrefix()
const { track } = useTracking()

// In demo mode, skip API calls and use mock data
const { data, isLoading, isError, refetch } = isDemo.value
  ? { data: ref(null), isLoading: ref(false), isError: ref(false), refetch: () => {} }
  : useCreditModelsQuery()
const { mutateAsync: deleteMutate } = isDemo.value
  ? { mutateAsync: async () => {} }
  : useDeleteCreditModelMutation()
const authStore = isDemo.value ? { logout: () => {} } : useAuthStore()

const DEMO_CREDIT_MODELS: CreditModel[] = [
  { id: 'cm_1', name: 'API Credits', denomination: 'API calls', description: 'Credits for API usage metering', createdAt: '2025-06-15T00:00:00Z' },
  { id: 'cm_2', name: 'Storage Credits', denomination: 'GB', description: 'Credits for cloud storage allocation', createdAt: '2025-07-01T00:00:00Z' },
  { id: 'cm_3', name: 'Compute Credits', denomination: 'compute units', description: 'Credits for serverless compute', createdAt: '2025-08-01T00:00:00Z' },
]

const showCreateModal = ref(false)
const showDeleteDialog = ref(false)
const modelToDelete = ref<CreditModel | null>(null)

const creditModels = computed<CreditModel[]>(() => {
  if (isDemo.value) return DEMO_CREDIT_MODELS
  if (!data.value?.data) return []
  return Array.isArray(data.value.data) ? data.value.data : []
})

function confirmDelete(model: CreditModel) {
  track('credit_model_delete_started', { modelId: model.id })
  modelToDelete.value = model
  showDeleteDialog.value = true
}

async function handleDelete() {
  if (!modelToDelete.value) return
  track('credit_model_deleted', { modelId: modelToDelete.value.id })
  try {
    await deleteMutate(modelToDelete.value.id)
    toast({ title: 'Success', description: 'Credit model deleted' })
  } catch (error) {
    const parsedError = parseApiError(error)
    toast({ title: 'Error', description: parsedError.message, variant: 'destructive' })
  } finally {
    showDeleteDialog.value = false
    modelToDelete.value = null
  }
}
</script>
