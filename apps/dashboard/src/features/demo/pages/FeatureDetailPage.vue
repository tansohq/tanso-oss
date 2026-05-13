<template>
  <div class="p-6 pb-16">
    <!-- Breadcrumb -->
    <div class="flex items-center gap-2 text-sm text-muted-foreground mb-6">
      <router-link :to="isDemo ? '/demo/features' : '/features'" class="hover:text-foreground"
        >Features</router-link
      >
      <ChevronRight class="h-4 w-4" />
      <span class="text-foreground">{{ feature?.name || 'Feature' }}</span>
    </div>

    <div v-if="feature">
      <!-- Header -->
      <div class="flex items-center justify-between mb-6">
        <div>
          <h1 class="text-2xl font-semibold text-foreground">{{ feature.name }}</h1>
          <button
            class="flex items-center gap-1.5 text-sm text-muted-foreground font-mono hover:text-foreground transition-colors group mt-1"
            @click="copyKey(feature.key)"
          >
            {{ feature.key }}
            <Copy class="h-3.5 w-3.5 opacity-0 group-hover:opacity-100 transition-opacity" />
          </button>
        </div>
        <div class="flex items-center gap-2">
          <Button variant="outline" @click="showEditDialog = true">
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

      <div class="space-y-6 max-w-4xl">
        <!-- Basic Information -->
        <Card class="p-6">
          <h3 class="text-sm font-medium mb-4">Basic Information</h3>
          <div class="grid grid-cols-2 gap-6 text-sm">
            <div>
              <div class="text-muted-foreground mb-1">Name</div>
              <div class="font-medium">{{ feature.name }}</div>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Status</div>
              <Badge
                :class="
                  feature.isEnabled
                    ? 'bg-green-100 text-green-700 border-0'
                    : 'bg-gray-100 text-gray-700 border-0'
                "
              >
                {{ feature.isEnabled ? 'Enabled' : 'Disabled' }}
              </Badge>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Key</div>
              <button
                class="flex items-center gap-1.5 text-muted-foreground font-mono hover:text-foreground transition-colors group"
                @click="copyKey(feature.key)"
              >
                {{ feature.key }}
                <Copy class="h-3 w-3 opacity-0 group-hover:opacity-100 transition-opacity" />
              </button>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Plans Using Feature</div>
              <div class="font-medium">{{ feature.plansCount }}</div>
            </div>
            <div class="col-span-2" v-if="feature.description">
              <div class="text-muted-foreground mb-1">Description</div>
              <div>{{ feature.description }}</div>
            </div>
          </div>
        </Card>

        <!-- Plans Using This Feature -->
        <Card class="p-6">
          <h3 class="text-sm font-medium mb-4">Plans Using This Feature</h3>
          <Table v-if="plansUsingFeature.length > 0">
            <TableHeader>
              <TableRow>
                <TableHead>Plan Name</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow
                v-for="plan in plansUsingFeature"
                :key="plan.id"
              >
                <TableCell class="font-medium">{{ plan.name }}</TableCell>
              </TableRow>
            </TableBody>
          </Table>
          <div v-else class="text-sm text-muted-foreground text-center py-8">
            This feature is not used by any plans yet.
          </div>
        </Card>

        <!-- Metadata -->
        <Card class="p-6">
          <h3 class="text-sm font-medium mb-4">Metadata</h3>
          <div class="grid grid-cols-2 gap-6 text-sm">
            <div>
              <div class="text-muted-foreground mb-1">Created</div>
              <div>{{ formatDate(feature.createdAt) }}</div>
            </div>
            <div>
              <div class="text-muted-foreground mb-1">Last Modified</div>
              <div>{{ formatDate(feature.modifiedAt) }}</div>
            </div>
          </div>
        </Card>
      </div>
    </div>

    <!-- Not Found -->
    <Card v-else class="p-8 text-center">
      <AlertCircle class="h-8 w-8 text-muted-foreground mx-auto mb-4" />
      <h3 class="text-lg font-medium mb-2">Feature Not Found</h3>
      <p class="text-muted-foreground mb-4">The feature you're looking for doesn't exist.</p>
      <Button variant="outline" @click="router.push(isDemo ? '/demo/features' : '/features')">
        Back to Features
      </Button>
    </Card>

    <!-- Edit Feature Dialog -->
    <Dialog :open="showEditDialog" @update:open="showEditDialog = $event">
      <DialogContent class="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>Edit Feature</DialogTitle>
          <DialogDescription> Update the feature details </DialogDescription>
        </DialogHeader>

        <div class="flex flex-col gap-4 py-4">
          <!-- Name -->
          <div class="flex flex-col gap-1.5">
            <Label>Name *</Label>
            <Input v-model="editName" placeholder="e.g., Advanced Analytics" />
          </div>

          <!-- Key -->
          <div class="flex flex-col gap-1.5">
            <Label>Key *</Label>
            <Input v-model="editKey" placeholder="e.g., advanced_analytics" class="font-mono" />
            <p class="text-xs text-muted-foreground">
              Used to identify this feature in your code and API calls
            </p>
          </div>

          <!-- Description -->
          <div class="flex flex-col gap-1.5">
            <Label>Description</Label>
            <Input v-model="editDescription" placeholder="Optional description..." />
          </div>

          <!-- Status -->
          <div class="flex items-center gap-2">
            <Switch v-model:checked="editIsEnabled" />
            <Label>{{ editIsEnabled ? 'Enabled' : 'Disabled' }}</Label>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" @click="showEditDialog = false">Cancel</Button>
          <Button @click="handleSaveEdit" :disabled="!canSaveEdit">Save Changes</Button>
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
            Delete
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronRight, Copy, Pencil, Trash2, AlertCircle } from 'lucide-vue-next'
import { Card } from '@/components/ui/card'
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
import { useDemoState } from '../composables/useDemoState'
import { toast } from '@/components/ui/toast/use-toast'

const route = useRoute()
const router = useRouter()

const { featuresData, updateFeature, deleteFeature, getPlansUsingFeature } = useDemoState()

// Check if we're in demo mode
const isDemo = computed(() => route.path.startsWith('/demo'))

const feature = computed(() => {
  const id = route.params.id as string
  return featuresData.value.find((f) => f.id === id)
})

const plansUsingFeature = computed(() => {
  if (!feature.value) return []
  return getPlansUsingFeature(feature.value.id)
})

// Edit dialog state
const showEditDialog = ref(false)
const editName = ref('')
const editKey = ref('')
const editDescription = ref('')
const editIsEnabled = ref(true)

// Delete dialog state
const showDeleteDialog = ref(false)

// Initialize edit form when feature changes or dialog opens
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

function copyKey(key: string) {
  navigator.clipboard.writeText(key)
  toast({
    title: 'Copied to clipboard',
    description: `Feature key "${key}" has been copied.`
  })
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
}

function handleSaveEdit() {
  if (!feature.value || !canSaveEdit.value) return

  updateFeature(feature.value.id, {
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
}

function handleDelete() {
  if (!feature.value) return

  const featureName = feature.value.name
  deleteFeature(feature.value.id)

  toast({
    title: 'Feature deleted',
    description: `Feature "${featureName}" has been deleted.`
  })

  router.push(isDemo.value ? '/demo/features' : '/features')
}
</script>
