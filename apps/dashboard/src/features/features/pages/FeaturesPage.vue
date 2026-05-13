<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight text-foreground">Features</h1>
        <p class="text-muted-foreground mt-1">Define what customers can access</p>
      </div>
      <Button @click="showCreateModal = true">
        <Plus class="w-4 h-4 mr-2" />
        Create Feature
      </Button>
    </div>

    <div class="bg-card rounded-lg border shadow-sm">
      <div class="p-4 border-b">
        <div class="relative max-w-sm">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input v-model="searchQuery" placeholder="Search features..." class="pl-9" />
        </div>
      </div>

      <Table v-if="isLoading">
        <TableHeader>
          <TableRow>
            <TableHead>Name</TableHead>
            <TableHead>Key</TableHead>
            <TableHead>Modified At</TableHead>
            <TableHead class="w-[70px]">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow v-for="i in 5" :key="i">
            <TableCell><Skeleton class="h-4 w-28" /><Skeleton class="h-3 w-48 mt-1" /></TableCell>
            <TableCell><Skeleton class="h-4 w-24" /></TableCell>
            <TableCell><Skeleton class="h-4 w-28" /></TableCell>
            <TableCell><Skeleton class="h-8 w-8 rounded" /></TableCell>
          </TableRow>
        </TableBody>
      </Table>

      <div v-else-if="isError" class="flex flex-col items-center justify-center py-12">
        <AlertCircle class="w-12 h-12 text-destructive mb-4" />
        <p class="text-lg font-medium text-foreground mb-2">Unable to load features</p>
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

      <div v-else-if="features && features.length > 0">
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
              @click="onRowClick(feature)"
            >
              <TableCell>
                <div class="flex flex-col">
                  <span class="font-medium">{{ feature.name }}</span>
                  <span v-if="feature.description" class="text-xs text-muted-foreground">{{ feature.description }}</span>
                </div>
              </TableCell>
              <TableCell class="font-mono text-sm text-muted-foreground">{{ feature.key }}</TableCell>
              <TableCell>{{ formatDateTime(feature.modifiedAt) }}</TableCell>
              <TableCell>
                <DropdownMenu>
                  <DropdownMenuTrigger as-child @click.stop>
                    <Button variant="ghost" size="icon" class="h-8 w-8">
                      <MoreHorizontal class="h-4 w-4" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem @click.stop="onRowClick(feature)">
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
        <div class="flex gap-2">
          <Button @click="showCreateModal = true">
            <Plus class="w-4 h-4 mr-2" />
            Create Feature
          </Button>
          <Button variant="outline" @click="showStripeModal = true">
            Import from Stripe
          </Button>
        </div>
      </div>
    </div>

    <FeatureDetailsDrawer v-model:visible="showModal" :feature="selectedFeature" :initial-edit-mode="drawerInitialEditMode" />
    <CreateFeatureModal v-model:visible="showCreateModal" />
    <StripeConnectionModal v-model:visible="showStripeModal" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Plus,
  AlertCircle,
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  Search,
  Inbox,
  RefreshCw,
  LogOut,
  MoreHorizontal,
  Eye,
  Pencil
} from 'lucide-vue-next'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu'
import { useFeaturesQuery } from '../queries'
import { useAuthStore } from '@/stores/auth'
import FeatureDetailsDrawer from '../components/FeatureDetailsDrawer.vue'
import CreateFeatureModal from '../components/CreateFeatureModal.vue'
import StripeConnectionModal from '@/features/integrations/components/StripeConnectionModal.vue'
import { formatDateTime } from '@/lib/formatters'
import type { Feature } from '../types'
import { useTracking } from '@/lib/tracking'
import { useDemoPrefix } from '@/lib/useDemoPrefix'

const router = useRouter()
const { demoPath, isDemo } = useDemoPrefix()
const { track } = useTracking()

// In demo mode, skip API calls and use mock data
const { data, isLoading, isError, refetch } = isDemo.value
  ? { data: ref(null), isLoading: ref(false), isError: ref(false), refetch: () => {} }
  : useFeaturesQuery()
const authStore = isDemo.value ? { logout: () => {} } : useAuthStore()

const DEMO_FEATURES: Feature[] = [
  { id: 'feat_1', name: 'API Calls', key: 'api_calls', description: 'Track and meter API usage', isEnabled: true, createdAt: '2025-06-15T10:00:00Z', modifiedAt: '2025-09-01T10:00:00Z' },
  { id: 'feat_2', name: 'Storage', key: 'storage_gb', description: 'Cloud storage allocation in GB', isEnabled: true, createdAt: '2025-06-15T10:00:00Z', modifiedAt: '2025-08-20T10:00:00Z' },
  { id: 'feat_3', name: 'Analytics Dashboard', key: 'analytics', description: 'Access to analytics and reporting', isEnabled: true, createdAt: '2025-07-01T10:00:00Z', modifiedAt: '2025-09-10T10:00:00Z' },
  { id: 'feat_4', name: 'Webhooks', key: 'webhooks', description: 'Real-time event notifications', isEnabled: true, createdAt: '2025-07-15T10:00:00Z', modifiedAt: '2025-07-15T10:00:00Z' },
  { id: 'feat_5', name: 'SSO Integration', key: 'sso', description: 'Single sign-on for enterprise customers', isEnabled: false, createdAt: '2025-08-01T10:00:00Z', modifiedAt: '2025-08-01T10:00:00Z' },
]

const features = computed(() => {
  if (isDemo.value) return DEMO_FEATURES
  if (!data.value) return []
  if (data.value.data) {
    // Paginated format: { data: { items: [...] } }
    if ('items' in data.value.data && Array.isArray(data.value.data.items)) {
      return data.value.data.items
    }
    // Direct array format: { data: [...] }
    if (Array.isArray(data.value.data)) {
      return data.value.data
    }
  }
  return []
})

const showModal = ref(false)
const selectedFeature = ref<Feature | null>(null)
const showCreateModal = ref(false)
const showStripeModal = ref(false)
const drawerInitialEditMode = ref(false)

const currentPage = ref(1)
const pageSize = ref(10)
const sortField = ref<string | null>(null)
const sortOrder = ref<'asc' | 'desc'>('asc')
const searchQuery = ref('')

watch(searchQuery, () => {
  currentPage.value = 1
})

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

const filteredFeatures = computed(() => {
  if (!searchQuery.value.trim()) return features.value
  const query = searchQuery.value.toLowerCase()
  return features.value.filter(
    (feature) =>
      feature.key?.toLowerCase().includes(query) ||
      feature.name?.toLowerCase().includes(query) ||
      feature.description?.toLowerCase().includes(query)
  )
})

const sortedFeatures = computed(() => {
  if (!sortField.value) return filteredFeatures.value
  return [...filteredFeatures.value].sort((a, b) => {
    const aVal = a[sortField.value as keyof Feature]
    const bVal = b[sortField.value as keyof Feature]
    if (aVal === null || aVal === undefined) return 1
    if (bVal === null || bVal === undefined) return -1
    if (aVal < bVal) return sortOrder.value === 'asc' ? -1 : 1
    if (aVal > bVal) return sortOrder.value === 'asc' ? 1 : -1
    return 0
  })
})

const totalPages = computed(() => Math.ceil(sortedFeatures.value.length / pageSize.value))

const paginatedFeatures = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return sortedFeatures.value.slice(start, end)
})

function onRowClick(feature: Feature) {
  track('feature_detail_opened', { featureId: feature.id })
  router.push(demoPath(`/features/${feature.id}`))
}

function onEditClick(feature: Feature) {
  track('feature_edit_opened', { featureId: feature.id })
  selectedFeature.value = feature
  drawerInitialEditMode.value = true
  showModal.value = true
}

</script>
