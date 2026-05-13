<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-semibold text-foreground">Features</h1>
        <p class="text-muted-foreground mt-1">Define what customers can access</p>
      </div>
      <Button @click="showCreateFeatureDialog = true">
        <Plus class="w-4 h-4 mr-2" />
        Create Feature
      </Button>
    </div>

    <div class="bg-card rounded-lg border shadow-sm">
      <div class="p-4 border-b">
        <div class="relative max-w-sm">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input v-model="featureSearchQuery" placeholder="Search features..." class="pl-9" />
        </div>
      </div>

      <div v-if="sortedFeatures.length > 0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead class="cursor-pointer hover:bg-muted/50" @click="toggleSort('name')">
                Name
                <component
                  :is="getSortIcon('name')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'name' }"
                />
              </TableHead>
              <TableHead class="cursor-pointer hover:bg-muted/50" @click="toggleSort('key')">
                Key
                <component
                  :is="getSortIcon('key')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'key' }"
                />
              </TableHead>
              <TableHead class="cursor-pointer hover:bg-muted/50" @click="toggleSort('createdAt')">
                Created At
                <component
                  :is="getSortIcon('createdAt')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'createdAt' }"
                />
              </TableHead>
              <TableHead class="cursor-pointer hover:bg-muted/50" @click="toggleSort('modifiedAt')">
                Modified At
                <component
                  :is="getSortIcon('modifiedAt')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'modifiedAt' }"
                />
              </TableHead>
              <TableHead class="w-[70px]">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="feature in paginatedFeatures"
              :key="feature.id"
              class="cursor-pointer hover:bg-muted/50"
              @click="navigateToFeature(feature.id)"
            >
              <TableCell>
                <div class="flex flex-col">
                  <span class="font-medium">{{ feature.name }}</span>
                  <span v-if="feature.description" class="text-xs text-muted-foreground">{{ feature.description }}</span>
                </div>
              </TableCell>
              <TableCell class="font-mono text-sm text-muted-foreground">{{ feature.key }}</TableCell>
              <TableCell>{{ formatDateTime(feature.createdAt) }}</TableCell>
              <TableCell>{{ formatDateTime(feature.modifiedAt) }}</TableCell>
              <TableCell>
                <DropdownMenu>
                  <DropdownMenuTrigger as-child @click.stop>
                    <Button variant="ghost" size="icon" class="h-8 w-8">
                      <MoreHorizontal class="h-4 w-4" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem @click.stop="navigateToFeature(feature.id)">
                      <Eye class="mr-2 h-4 w-4" />
                      View Details
                    </DropdownMenuItem>
                    <DropdownMenuItem @click.stop="onEditClick(feature)">
                      <Pencil class="mr-2 h-4 w-4" />
                      Edit
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>

        <div class="flex items-center justify-between px-4 py-4 border-t">
          <div class="flex items-center gap-4">
            <div class="text-sm text-muted-foreground">
              Showing {{ (currentPage - 1) * pageSize + 1 }} to
              {{ Math.min(currentPage * pageSize, sortedFeatures.length) }} of
              {{ sortedFeatures.length }} entries
            </div>
            <div class="flex items-center gap-2">
              <span class="text-sm text-muted-foreground">Rows per page:</span>
              <Select
                :model-value="String(pageSize)"
                @update:model-value="
                  (v) => {
                    pageSize = Number(v)
                    currentPage = 1
                  }
                "
              >
                <SelectTrigger class="w-[70px] h-8">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="10">10</SelectItem>
                  <SelectItem value="25">25</SelectItem>
                  <SelectItem value="50">50</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              :disabled="currentPage === 1"
              @click="currentPage--"
            >
              Previous
            </Button>
            <span class="text-sm text-muted-foreground"
              >Page {{ currentPage }} of {{ totalPages }}</span
            >
            <Button
              variant="outline"
              size="sm"
              :disabled="currentPage >= totalPages"
              @click="currentPage++"
            >
              Next
            </Button>
          </div>
        </div>
      </div>

      <div v-else class="flex flex-col items-center justify-center py-12 text-muted-foreground">
        <Inbox class="w-12 h-12 mb-4" />
        <p class="text-lg font-medium text-foreground mb-2">No features yet</p>
        <p class="text-sm mb-4">Create your first feature to get started</p>
        <Button @click="showCreateFeatureDialog = true">
          <Plus class="w-4 h-4 mr-2" />
          Create Feature
        </Button>
      </div>
    </div>

    <!-- Create Feature Dialog -->
    <Dialog :open="showCreateFeatureDialog" @update:open="showCreateFeatureDialog = $event">
      <DialogContent class="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>Create Feature</DialogTitle>
          <DialogDescription> Add a new feature that can be attached to plans </DialogDescription>
        </DialogHeader>

        <div class="flex flex-col gap-4 py-4">
          <!-- Name -->
          <div class="flex flex-col gap-1.5">
            <Label>Name *</Label>
            <Input
              v-model="newFeatureName"
              placeholder="e.g., Advanced Analytics"
              @input="onNameInput"
            />
          </div>

          <!-- Key -->
          <div class="flex flex-col gap-1.5">
            <Label>Key *</Label>
            <Input
              v-model="newFeatureKey"
              placeholder="e.g., advanced_analytics"
              class="font-mono"
            />
            <p class="text-xs text-muted-foreground">
              Used to identify this feature in your code and API calls
            </p>
          </div>

          <!-- Description -->
          <div class="flex flex-col gap-1.5">
            <Label>Description</Label>
            <Input v-model="newFeatureDescription" placeholder="Optional description..." />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" @click="showCreateFeatureDialog = false">Cancel</Button>
          <Button @click="handleCreateFeature" :disabled="!canCreateFeature">Create Feature</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Plus,
  Search,
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  MoreHorizontal,
  Eye,
  Pencil,
  Inbox
} from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu'
import { useDemoState } from '../composables/useDemoState'
import { formatDateTime } from '@/lib/formatters'
import { toast } from '@/components/ui/toast/use-toast'
import type { Feature } from '../types'

const route = useRoute()
const router = useRouter()

const { filteredFeatures, featureSearchQuery, createFeature } = useDemoState()

// Check if we're in demo mode
const isDemo = computed(() => route.path.startsWith('/demo'))

function navigateToFeature(featureId: string) {
  router.push(isDemo.value ? `/demo/features/${featureId}` : `/features/${featureId}`)
}

// Sorting state
const sortField = ref<string | null>(null)
const sortOrder = ref<'asc' | 'desc'>('asc')

// Pagination state
const currentPage = ref(1)
const pageSize = ref(10)

// Reset page when search changes
watch(featureSearchQuery, () => {
  currentPage.value = 1
})

// Sorting functions
function toggleSort(field: string) {
  if (sortField.value === field) {
    sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortField.value = field
    sortOrder.value = 'asc'
  }
}

function getSortIcon(field: string) {
  if (sortField.value !== field) return ArrowUpDown
  return sortOrder.value === 'asc' ? ArrowUp : ArrowDown
}

// Sorted features
const sortedFeatures = computed(() => {
  if (!sortField.value) return filteredFeatures.value
  return [...filteredFeatures.value].sort((a, b) => {
    const aVal = a[sortField.value as keyof typeof a]
    const bVal = b[sortField.value as keyof typeof b]
    if (aVal === null || aVal === undefined) return 1
    if (bVal === null || bVal === undefined) return -1
    if (aVal < bVal) return sortOrder.value === 'asc' ? -1 : 1
    if (aVal > bVal) return sortOrder.value === 'asc' ? 1 : -1
    return 0
  })
})

// Pagination
const totalPages = computed(() => Math.ceil(sortedFeatures.value.length / pageSize.value))

const paginatedFeatures = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return sortedFeatures.value.slice(start, end)
})

// Edit click (non-functional in demo, just navigates to detail)
function onEditClick(feature: Feature) {
  navigateToFeature(feature.id)
}

// Create feature dialog state
const showCreateFeatureDialog = ref(false)
const newFeatureName = ref('')
const newFeatureKey = ref('')
const newFeatureDescription = ref('')
const newFeatureKeyManuallyEdited = ref(false)

// Validation
const canCreateFeature = computed(() => {
  return !!(newFeatureName.value.trim() && newFeatureKey.value.trim())
})

function generateKey(name: string): string {
  return name
    .toLowerCase()
    .replace(/[^a-z0-9\s-]/g, '')
    .replace(/\s+/g, '_')
    .replace(/-+/g, '_')
    .replace(/^_+|_+$/g, '')
}

function onNameInput() {
  if (!newFeatureKeyManuallyEdited.value) {
    newFeatureKey.value = generateKey(newFeatureName.value)
  }
}

function resetForm() {
  newFeatureName.value = ''
  newFeatureKey.value = ''
  newFeatureDescription.value = ''
  newFeatureKeyManuallyEdited.value = false
}

function handleCreateFeature() {
  if (!canCreateFeature.value) return

  createFeature({
    name: newFeatureName.value.trim(),
    key: newFeatureKey.value.trim(),
    description: newFeatureDescription.value.trim(),
    isEnabled: true
  })

  toast({
    title: 'Feature created',
    description: `Feature "${newFeatureName.value}" has been created.`
  })

  resetForm()
  showCreateFeatureDialog.value = false
}
</script>
