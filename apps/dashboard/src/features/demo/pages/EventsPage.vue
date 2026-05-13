<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-semibold text-foreground">Events</h1>
        <p class="text-muted-foreground mt-1">Track customer activity and usage</p>
      </div>
      <div class="flex items-center gap-2">
        <Button variant="outline">
          <RefreshCw class="h-4 w-4 mr-2" />
          Refresh
        </Button>
        <Button variant="outline" @click="downloadCsv">
          <Download class="h-4 w-4 mr-2" />
          Download CSV
        </Button>
      </div>
    </div>

    <!-- Filter Bar -->
    <div class="flex flex-wrap items-end gap-3 mb-4">
      <div class="flex flex-col gap-1.5">
        <div class="flex gap-1">
          <Button
            v-for="p in periodOptions"
            :key="p"
            :variant="activePeriod === p ? 'default' : 'outline'"
            size="sm"
            class="h-8 px-3"
            @click="activePeriod = p"
          >
            {{ periodButtonLabels[p] }}
          </Button>
        </div>
      </div>

      <template v-if="activePeriod === 'custom'">
        <div class="flex flex-col gap-1.5">
          <label class="text-xs text-muted-foreground">Start date</label>
          <Input
            v-model="customStart"
            type="date"
            class="w-[150px] h-8"
          />
        </div>
        <div class="flex flex-col gap-1.5">
          <label class="text-xs text-muted-foreground">End date</label>
          <Input
            v-model="customEnd"
            type="date"
            class="w-[150px] h-8"
          />
        </div>
      </template>

      <Button
        variant="outline"
        size="sm"
        class="h-8"
        @click="showFilters = !showFilters"
      >
        <ListFilter class="h-4 w-4 mr-1.5" />
        Filters
        <Badge
          v-if="activeFilterCount > 0"
          class="ml-1.5 h-5 min-w-5 px-1.5 text-xs bg-primary text-primary-foreground rounded-full"
        >
          {{ activeFilterCount }}
        </Badge>
      </Button>

      <Button
        v-if="hasActiveFilters"
        variant="ghost"
        size="sm"
        class="h-8"
        @click="clearFilters"
      >
        <X class="h-4 w-4 mr-1" />
        Clear
      </Button>
    </div>

    <!-- Secondary Filters -->
    <div v-show="showFilters" class="flex flex-wrap items-end gap-3 mb-4">
      <div class="flex flex-col gap-1.5">
        <label class="text-xs text-muted-foreground">Event Type</label>
        <Select v-model="filterEventType">
          <SelectTrigger class="w-[200px] h-8">
            <SelectValue placeholder="All events" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">All Events</SelectItem>
            <SelectItem value="CLIENT_TRACKED">Usage Events</SelectItem>
            <SelectItem value="ENTITLEMENT_CHECKED">Entitlement Checks</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div class="flex flex-col gap-1.5">
        <label class="text-xs text-muted-foreground">Customer</label>
        <Select v-model="filterCustomer">
          <SelectTrigger class="w-[200px] h-8">
            <SelectValue placeholder="All customers" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">All customers</SelectItem>
            <SelectItem
              v-for="c in mockCustomers"
              :key="c.id"
              :value="c.id"
            >
              {{ c.name }}
            </SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div class="flex flex-col gap-1.5">
        <label class="text-xs text-muted-foreground">Plan</label>
        <Select v-model="filterPlan">
          <SelectTrigger class="w-[180px] h-8">
            <SelectValue placeholder="All plans" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">All plans</SelectItem>
            <SelectItem value="starter">Starter</SelectItem>
            <SelectItem value="growth">Growth</SelectItem>
            <SelectItem value="enterprise">Enterprise</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div class="flex flex-col gap-1.5">
        <label class="text-xs text-muted-foreground">Feature</label>
        <Select v-model="filterFeature">
          <SelectTrigger class="w-[180px] h-8">
            <SelectValue placeholder="All features" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__all__">All features</SelectItem>
            <SelectItem value="api_requests">API Requests</SelectItem>
            <SelectItem value="lead_scoring">Lead Scoring</SelectItem>
            <SelectItem value="export_contacts">Export Contacts</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <div class="flex flex-col gap-1.5">
        <label class="text-xs text-muted-foreground">Event Name</label>
        <Input
          v-model="filterEventName"
          placeholder="Filter by name..."
          class="w-[200px] h-8"
        />
      </div>
    </div>

    <!-- Chart Card -->
    <Card class="p-6 mb-6">
      <div class="flex items-center justify-between mb-4">
        <div>
          <div class="text-sm font-medium text-muted-foreground">Events Ingested</div>
          <div class="text-4xl font-semibold tabular-nums">
            {{ formatNumber(chartTotalEvents) }}
          </div>
          <div class="text-sm text-muted-foreground mt-1">Updated just now</div>
        </div>
        <div class="text-sm text-muted-foreground">{{ periodLabel }}</div>
      </div>
      <div class="h-16 flex items-end gap-[1px]" role="img" :aria-label="`Events ingested - ${periodLabel}`">
        <TooltipProvider>
          <Tooltip v-for="(bar, i) in computedBarHeights" :key="i">
            <TooltipTrigger as-child>
              <div
                class="flex-1 rounded-t bg-primary/20 hover:bg-primary/40 transition-colors cursor-default"
                :style="{ height: bar }"
              />
            </TooltipTrigger>
            <TooltipContent>
              <p class="text-xs tabular-nums">{{ chartLabels[i] ? `${chartLabels[i]}: ` : '' }}{{ chartDailyCounts[i].toLocaleString() }} events</p>
            </TooltipContent>
          </Tooltip>
        </TooltipProvider>
      </div>
      <div class="flex justify-between text-[10px] text-muted-foreground mt-1.5">
        <span v-for="(label, i) in chartLabels" :key="i" :class="{ 'invisible': !label }">{{ label || '.' }}</span>
      </div>
    </Card>

    <div class="bg-card rounded-lg border shadow-sm">
      <div class="p-4 border-b">
        <div class="relative max-w-sm">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            v-model="searchQuery"
            placeholder="Search by event name, customer, or type..."
            class="pl-9"
          />
        </div>
      </div>

      <div v-if="sortedEvents.length > 0" class="relative">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead
                class="cursor-pointer hover:bg-muted/50"
                @click="toggleSort('eventName')"
              >
                Event Name
                <component
                  :is="getSortIcon('eventName')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'eventName' }"
                />
              </TableHead>
              <TableHead
                class="cursor-pointer hover:bg-muted/50"
                @click="toggleSort('customerName')"
              >
                Customer
                <component
                  :is="getSortIcon('customerName')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'customerName' }"
                />
              </TableHead>
              <TableHead>
                <TooltipProvider :delay-duration="0">
                  <Tooltip>
                    <TooltipTrigger as-child>
                      <span class="inline-flex items-center gap-1.5 cursor-default px-1 -mx-1 py-0.5 rounded hover:bg-muted/50 transition-colors">
                        Type
                        <HelpCircle class="h-4 w-4 text-muted-foreground/60" />
                      </span>
                    </TooltipTrigger>
                    <TooltipContent class="max-w-xs">
                      <p class="text-xs"><strong>Usage</strong>: tracked activity (API calls, feature usage)</p>
                      <p class="text-xs mt-1"><strong>Entitled</strong>: access was granted for this feature</p>
                      <p class="text-xs mt-1"><strong>Denied</strong>: access was blocked (limit reached or not in plan)</p>
                    </TooltipContent>
                  </Tooltip>
                </TooltipProvider>
              </TableHead>
              <TableHead
                class="cursor-pointer hover:bg-muted/50"
                @click="toggleSort('occurredAt')"
              >
                Occurred At
                <component
                  :is="getSortIcon('occurredAt')"
                  class="ml-2 h-4 w-4 inline"
                  :class="{ 'text-primary': sortField === 'occurredAt' }"
                />
              </TableHead>
              <TableHead class="w-10" />
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="event in paginatedEvents"
              :key="event.id"
              class="cursor-pointer hover:bg-muted/50 group"
              @click="onRowClick(event)"
            >
              <TableCell>
                <div class="flex items-center gap-1.5">
                  <code class="text-sm font-mono">{{ event.eventName }}</code>
                  <AlertTriangle
                    v-if="event.ingestError"
                    class="h-4 w-4 text-amber-500 shrink-0"
                  />
                </div>
              </TableCell>
              <TableCell class="text-sm text-muted-foreground">
                {{ event.customerName }}
              </TableCell>
              <TableCell>
                <Badge
                  v-if="event.eventType === 'CLIENT_TRACKED'"
                  class="shadow-none bg-muted text-muted-foreground border-0 font-normal"
                >Usage</Badge>
                <Badge
                  v-else-if="event.eventType === 'ENTITLEMENT_CHECKED' && event.isEntitled === true"
                  class="shadow-none bg-emerald-50 text-emerald-700 border border-emerald-200/50 font-normal"
                >
                  <CheckCircle2 class="h-3 w-3 mr-1" />
                  Entitled
                </Badge>
                <Badge
                  v-else-if="event.eventType === 'ENTITLEMENT_CHECKED' && event.isEntitled === false"
                  class="shadow-none bg-red-50 text-red-700 border border-red-200/50 font-normal"
                >
                  <XCircle class="h-3 w-3 mr-1" />
                  Denied
                </Badge>
                <Badge
                  v-else
                  class="shadow-none bg-muted text-muted-foreground border-0 font-normal"
                >{{ event.eventType }}</Badge>
              </TableCell>
              <TableCell class="tabular-nums text-sm text-muted-foreground">
                <TooltipProvider>
                  <Tooltip>
                    <TooltipTrigger as-child>
                      <span>{{ formatRelativeTime(event.occurredAt) }}</span>
                    </TooltipTrigger>
                    <TooltipContent>
                      <p class="text-xs tabular-nums">{{ formatDateTime(event.occurredAt) }}</p>
                    </TooltipContent>
                  </Tooltip>
                </TooltipProvider>
              </TableCell>
              <TableCell>
                <ChevronRight class="h-4 w-4 text-muted-foreground/40 opacity-0 group-hover:opacity-100 transition-opacity" />
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>

        <div v-if="totalElements > 0" class="flex items-center justify-between px-4 py-3 border-t">
          <div class="flex items-center gap-4">
            <div class="text-sm text-muted-foreground">
              <template v-if="searchQuery.trim()">
                Showing {{ sortedEvents.length }} of {{ totalElements }} entries
              </template>
              <template v-else>
                Showing {{ currentPage * pageSize + 1 }} to
                {{ Math.min((currentPage + 1) * pageSize, totalElements) }} of
                {{ totalElements }} entries
              </template>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-sm text-muted-foreground">Rows per page:</span>
              <Select
                :model-value="String(pageSize)"
                @update:model-value="
                  (v) => {
                    pageSize = Number(v)
                    currentPage = 0
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
          <div v-if="!searchQuery.trim()" class="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              :disabled="currentPage === 0"
              @click="currentPage--"
            >
              Previous
            </Button>
            <span class="text-sm text-muted-foreground">
              Page {{ currentPage + 1 }} of {{ totalPages }}
            </span>
            <Button
              variant="outline"
              size="sm"
              :disabled="currentPage >= totalPages - 1"
              @click="currentPage++"
            >
              Next
            </Button>
          </div>
        </div>
      </div>

      <div v-else class="flex flex-col items-center justify-center py-12 text-muted-foreground">
        <Inbox class="w-12 h-12 mb-4" />
        <template v-if="hasActiveFilters">
          <p class="text-lg font-medium text-foreground mb-2">No events match your filters</p>
          <p class="text-sm mb-4">Try adjusting your search criteria or clearing filters</p>
          <Button variant="outline" @click="clearFilters">
            <X class="h-4 w-4 mr-2" />
            Clear filters
          </Button>
        </template>
        <template v-else>
          <p class="text-lg font-medium text-foreground mb-2">No events yet</p>
          <p class="text-sm">Events will appear here as they are tracked</p>
        </template>
      </div>
    </div>

    <!-- Event Detail Sheet -->
    <Sheet v-model:open="sheetOpen">
      <SheetContent class="w-full sm:max-w-lg overflow-y-auto">
        <SheetHeader>
          <SheetTitle>Event Details</SheetTitle>
        </SheetHeader>

        <div v-if="selectedEvent" class="space-y-6 mt-6">
          <div class="grid grid-cols-2 gap-4">
            <div class="flex flex-col gap-1">
              <label class="text-muted-foreground text-xs">Event Type</label>
              <Badge
                v-if="selectedEvent.eventType === 'CLIENT_TRACKED'"
                class="bg-muted text-muted-foreground border-0 shadow-none w-fit font-normal"
              >Usage</Badge>
              <Badge
                v-else
                class="bg-violet-50 text-violet-700 border border-violet-200/50 shadow-none w-fit font-normal"
              >Entitlement</Badge>
            </div>
            <div class="flex flex-col gap-1">
              <label class="text-muted-foreground text-xs">Event Name</label>
              <code class="text-sm font-mono">{{ selectedEvent.eventName }}</code>
            </div>
            <div class="flex flex-col gap-1">
              <label class="text-muted-foreground text-xs">Occurred At</label>
              <p class="text-sm tabular-nums">{{ formatDateTime(selectedEvent.occurredAt) }}</p>
            </div>
            <div class="flex flex-col gap-1">
              <label class="text-muted-foreground text-xs">Customer</label>
              <p class="text-sm font-medium">{{ selectedEvent.customerName }}</p>
            </div>
          </div>

          <div class="space-y-3">
            <div class="flex flex-col gap-1">
              <label class="text-muted-foreground text-xs">Event ID</label>
              <p class="text-sm font-mono bg-muted px-2 py-1 rounded truncate">{{ selectedEvent.id }}</p>
            </div>
            <div class="flex flex-col gap-1">
              <label class="text-muted-foreground text-xs">Customer ID</label>
              <p class="text-sm font-mono bg-muted px-2 py-1 rounded truncate">{{ selectedEvent.customerId }}</p>
            </div>
          </div>

          <div v-if="selectedEvent.isEntitled !== undefined" class="flex flex-col gap-1">
            <label class="text-muted-foreground text-xs">Entitled</label>
            <div v-if="selectedEvent.isEntitled" class="flex items-center gap-1.5 text-emerald-600">
              <CheckCircle2 class="h-4 w-4" />
              <span class="text-sm">Allowed</span>
            </div>
            <div v-else class="flex items-center gap-1.5 text-red-500">
              <XCircle class="h-4 w-4" />
              <span class="text-sm">Denied</span>
            </div>
          </div>

          <div class="flex flex-col gap-1">
            <label class="text-muted-foreground text-xs">Properties</label>
            <pre class="bg-muted p-4 rounded-lg text-xs font-mono overflow-x-auto">{{ formatJson(selectedEvent.properties) }}</pre>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
  Search,
  Inbox,
  AlertTriangle,
  RefreshCw,
  Download,
  ListFilter,
  X,
  CheckCircle2,
  XCircle,
  ChevronRight,
  HelpCircle
} from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
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
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger
} from '@/components/ui/tooltip'
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle
} from '@/components/ui/sheet'

// ── Types ──

interface MockEvent {
  id: string
  eventName: string
  customerId: string
  customerName: string
  eventType: 'CLIENT_TRACKED' | 'ENTITLEMENT_CHECKED'
  occurredAt: string
  isEntitled?: boolean
  ingestError?: string
  properties: Record<string, unknown>
}

type PeriodPreset = '7d' | '30d' | '3m' | '12m' | 'custom'

// ── Mock Data ──

function daysAgo(n: number): string {
  const d = new Date()
  d.setDate(d.getDate() - n)
  d.setHours(Math.floor(Math.random() * 24), Math.floor(Math.random() * 60))
  return d.toISOString()
}

const mockCustomers = [
  { id: 'outbound-io', name: 'Outbound.io' },
  { id: 'prospectlab', name: 'ProspectLab' },
  { id: 'pipelineai', name: 'PipelineAI' },
  { id: 'revenuebot', name: 'RevenueBot' },
  { id: 'dealflow', name: 'DealFlow' }
]

const mockEvents: MockEvent[] = [
  { id: 'evt_a1b2c3d4', eventName: 'feature_used', customerId: 'outbound-io', customerName: 'Outbound.io', eventType: 'CLIENT_TRACKED', occurredAt: daysAgo(0), properties: { featureKey: 'api_requests', quantity: 1, endpoint: 'process' } },
  { id: 'evt_e5f6g7h8', eventName: 'enrichment_executed', customerId: 'prospectlab', customerName: 'ProspectLab', eventType: 'CLIENT_TRACKED', occurredAt: daysAgo(0), properties: { featureKey: 'lead_scoring', quantity: 1, ai: { provider: 'openai', model: 'gpt-4.1-mini' } } },
  { id: 'evt_ent_001', eventName: 'api_requests', customerId: 'pipelineai', customerName: 'PipelineAI', eventType: 'ENTITLEMENT_CHECKED', occurredAt: daysAgo(0), isEntitled: true, properties: { isEntitled: true, featureKey: 'api_requests' } },
  { id: 'evt_i9j0k1l2', eventName: 'feature_used', customerId: 'pipelineai', customerName: 'PipelineAI', eventType: 'CLIENT_TRACKED', occurredAt: daysAgo(1), properties: { featureKey: 'export_contacts', quantity: 1 } },
  { id: 'evt_ent_002', eventName: 'lead_scoring', customerId: 'revenuebot', customerName: 'RevenueBot', eventType: 'ENTITLEMENT_CHECKED', occurredAt: daysAgo(1), isEntitled: false, properties: { isEntitled: false, featureKey: 'lead_scoring' } },
  { id: 'evt_m3n4o5p6', eventName: 'feature_used', customerId: 'revenuebot', customerName: 'RevenueBot', eventType: 'CLIENT_TRACKED', occurredAt: daysAgo(1), properties: { featureKey: 'proposal_generation', quantity: 2 } },
  { id: 'evt_q7r8s9t0', eventName: 'feature_used', customerId: 'dealflow', customerName: 'DealFlow', eventType: 'CLIENT_TRACKED', occurredAt: daysAgo(2), properties: { featureKey: 'api_requests', quantity: 1, endpoint: 'transfer' } },
  { id: 'evt_ent_003', eventName: 'export_contacts', customerId: 'outbound-io', customerName: 'Outbound.io', eventType: 'ENTITLEMENT_CHECKED', occurredAt: daysAgo(2), isEntitled: true, properties: { isEntitled: true, featureKey: 'export_contacts' } },
  { id: 'evt_u1v2w3x4', eventName: 'enrichment_executed', customerId: 'outbound-io', customerName: 'Outbound.io', eventType: 'CLIENT_TRACKED', occurredAt: daysAgo(3), properties: { featureKey: 'lead_scoring', quantity: 1, ai: { provider: 'openai', model: 'gpt-4.1-mini' } } },
  { id: 'evt_y5z6a7b8', eventName: 'feature_used', customerId: 'prospectlab', customerName: 'ProspectLab', eventType: 'CLIENT_TRACKED', occurredAt: daysAgo(3), properties: { featureKey: 'api_requests', quantity: 1, endpoint: 'validate' } },
  { id: 'evt_c9d0e1f2', eventName: 'feature_used', customerId: 'pipelineai', customerName: 'PipelineAI', eventType: 'CLIENT_TRACKED', occurredAt: daysAgo(4), properties: { featureKey: 'sequence_runs', quantity: 0.75 } },
  { id: 'evt_ent_004', eventName: 'proposal_generation', customerId: 'dealflow', customerName: 'DealFlow', eventType: 'ENTITLEMENT_CHECKED', occurredAt: daysAgo(4), isEntitled: true, properties: { isEntitled: true, featureKey: 'proposal_generation' } },
  { id: 'evt_g3h4i5j6', eventName: 'feature_used', customerId: 'revenuebot', customerName: 'RevenueBot', eventType: 'CLIENT_TRACKED', occurredAt: daysAgo(5), properties: { featureKey: 'api_requests', quantity: 1, endpoint: 'process' } },
  { id: 'evt_k7l8m9n0', eventName: 'feature_used', customerId: 'dealflow', customerName: 'DealFlow', eventType: 'CLIENT_TRACKED', occurredAt: daysAgo(5), properties: { featureKey: 'proposal_generation', quantity: 5 } },
  { id: 'evt_ent_005', eventName: 'api_requests', customerId: 'prospectlab', customerName: 'ProspectLab', eventType: 'ENTITLEMENT_CHECKED', occurredAt: daysAgo(6), isEntitled: false, properties: { isEntitled: false, featureKey: 'api_requests' } },
  { id: 'evt_z1a2b3c4', eventName: 'feature_used', customerId: 'outbound-io', customerName: 'Outbound.io', eventType: 'CLIENT_TRACKED', occurredAt: daysAgo(6), properties: { featureKey: 'api_requests', quantity: 3, endpoint: 'batch' } }
]

const chartDailyCounts = [58243, 61245, 59872, 60123, 62456, 58912, 57213]
const chartTotalEvents = 418064

// ── Period ──

const periodOptions: PeriodPreset[] = ['7d', '30d', '3m', '12m', 'custom']
const periodButtonLabels: Record<PeriodPreset, string> = {
  '7d': '7D',
  '30d': '30D',
  '3m': '3M',
  '12m': '12M',
  'custom': 'Custom'
}

const activePeriod = ref<PeriodPreset>('7d')
const customStart = ref('')
const customEnd = ref('')

const periodLabel = computed(() => {
  if (activePeriod.value === 'custom' && customStart.value && customEnd.value) {
    return `${customStart.value} – ${customEnd.value}`
  }
  switch (activePeriod.value) {
    case '7d': return 'Last 7 days'
    case '30d': return 'Last 30 days'
    case '3m': return 'Last 3 months'
    case '12m': return 'Last 12 months'
    default: return 'Last 7 days'
  }
})

// ── Chart ──

function generateChartLabels(): string[] {
  const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
  const labels: string[] = []
  for (let i = 6; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    labels.push(days[d.getDay()])
  }
  return labels
}

const chartLabels = generateChartLabels()

const computedBarHeights = computed(() => {
  const max = Math.max(...chartDailyCounts, 1)
  return chartDailyCounts.map((c) => {
    const pct = (c / max) * 100
    return pct > 0 ? `${Math.max(pct, 6)}%` : '4px'
  })
})

// ── Filters ──

const route = useRoute()

const showFilters = ref(false)
const filterEventType = ref<string | undefined>(undefined)
const filterCustomer = ref<string | undefined>(undefined)
const filterPlan = ref<string | undefined>(undefined)
const filterFeature = ref<string | undefined>(undefined)
const filterEventName = ref('')
const searchQuery = ref('')

// Map demo analytics feature UUIDs to event feature keys
const featureIdToKey: Record<string, string> = {
  'f6a7b8c9-d0e1-4234-f012-345678901234': 'api_requests',
  'a7b8c9d0-e1f2-4345-0123-456789012345': 'lead_scoring',
  'b8c9d0e1-f2a3-4456-1234-567890123456': 'export_contacts',
  'c9d0e1f2-a3b4-4567-2345-678901234567': 'lead_scoring',
  'd0e1f2a3-b4c5-4678-3456-789012345678': 'lead_scoring',
  'e1f2a3b4-c5d6-4789-4567-890123456789': 'api_requests',
}

// Pre-populate filters from query params (e.g. linked from analytics)
onMounted(() => {
  const q = route.query
  if (typeof q.featureId === 'string') {
    const key = featureIdToKey[q.featureId]
    if (key) {
      filterFeature.value = key
      showFilters.value = true
    }
  }
  if (typeof q.eventType === 'string') {
    filterEventType.value = q.eventType
    showFilters.value = true
  }
  if (typeof q.customerId === 'string') {
    filterCustomer.value = q.customerId
    showFilters.value = true
  }
})

const activeEventTypeFilter = computed(() =>
  filterEventType.value && filterEventType.value !== '__all__' ? filterEventType.value : undefined
)
const activeCustomerFilter = computed(() =>
  filterCustomer.value && filterCustomer.value !== '__all__' ? filterCustomer.value : undefined
)

const activeFilterCount = computed(() => {
  let count = 0
  if (activeCustomerFilter.value) count++
  if (filterPlan.value && filterPlan.value !== '__all__') count++
  if (filterFeature.value && filterFeature.value !== '__all__') count++
  if (activeEventTypeFilter.value) count++
  if (filterEventName.value.trim()) count++
  return count
})

const hasActiveFilters = computed(
  () =>
    activeCustomerFilter.value ||
    (filterPlan.value && filterPlan.value !== '__all__') ||
    (filterFeature.value && filterFeature.value !== '__all__') ||
    activeEventTypeFilter.value ||
    filterEventName.value.trim() ||
    activePeriod.value !== '7d'
)

function clearFilters() {
  filterCustomer.value = undefined
  filterPlan.value = undefined
  filterFeature.value = undefined
  filterEventType.value = undefined
  filterEventName.value = ''
  activePeriod.value = '7d'
  customStart.value = ''
  customEnd.value = ''
  currentPage.value = 0
}

// ── Table state ──

const currentPage = ref(0)
const pageSize = ref(25)
const sortField = ref<string | null>('occurredAt')
const sortOrder = ref<'asc' | 'desc'>('desc')
const sheetOpen = ref(false)
const selectedEvent = ref<MockEvent | null>(null)

// ── Filtering & sorting ──

const filteredEvents = computed(() => {
  let result = mockEvents

  if (activeEventTypeFilter.value) {
    result = result.filter((e) => e.eventType === activeEventTypeFilter.value)
  }
  if (activeCustomerFilter.value) {
    result = result.filter((e) => e.customerId === activeCustomerFilter.value)
  }
  if (filterFeature.value && filterFeature.value !== '__all__') {
    result = result.filter((e) => e.properties?.featureKey === filterFeature.value)
  }
  if (filterEventName.value.trim()) {
    const nameFilter = filterEventName.value.toLowerCase()
    result = result.filter((e) => e.eventName.toLowerCase().includes(nameFilter))
  }
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(
      (e) =>
        e.eventType.toLowerCase().includes(query) ||
        e.eventName.toLowerCase().includes(query) ||
        e.customerName.toLowerCase().includes(query)
    )
  }

  return result
})

const sortedEvents = computed(() => {
  if (!sortField.value) return filteredEvents.value
  return [...filteredEvents.value].sort((a, b) => {
    const aVal = a[sortField.value as keyof MockEvent]
    const bVal = b[sortField.value as keyof MockEvent]
    if (aVal === null || aVal === undefined) return 1
    if (bVal === null || bVal === undefined) return -1
    if (aVal < bVal) return sortOrder.value === 'asc' ? -1 : 1
    if (aVal > bVal) return sortOrder.value === 'asc' ? 1 : -1
    return 0
  })
})

const totalElements = computed(() => filteredEvents.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(totalElements.value / pageSize.value)))

const paginatedEvents = computed(() => {
  if (searchQuery.value.trim()) return sortedEvents.value
  const start = currentPage.value * pageSize.value
  return sortedEvents.value.slice(start, start + pageSize.value)
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

watch(searchQuery, () => {
  currentPage.value = 0
})

// ── Actions ──

function onRowClick(event: MockEvent) {
  selectedEvent.value = event
  sheetOpen.value = true
}

function formatNumber(num: number): string {
  if (num >= 1000000000) return (num / 1000000000).toFixed(1) + 'B'
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toLocaleString()
}

function formatRelativeTime(dateStr: string | null | undefined): string {
  if (!dateStr) return '\u2014'
  const now = new Date()
  const date = new Date(dateStr)
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return 'Just now'
  if (diffMins < 60) return `${diffMins}m ago`
  if (diffHours < 24) return `${diffHours}h ago`
  if (diffDays < 7) return `${diffDays}d ago`
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

function formatDateTime(dateStr: string | null | undefined): string {
  if (!dateStr) return '\u2014'
  const date = new Date(dateStr)
  return date.toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'UTC'
  })
}

function formatJson(data: Record<string, unknown>): string {
  return JSON.stringify(data, null, 2)
}

function downloadCsv() {
  const headers = ['occurred_at', 'event_type', 'event_name', 'customer', 'properties']
  const rows = mockEvents.map((e) => [
    e.occurredAt,
    e.eventType,
    e.eventName,
    e.customerName,
    JSON.stringify(e.properties)
  ])
  const csv = [headers.join(','), ...rows.map((r) => r.join(','))].join('\n')
  const blob = new Blob([csv], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `events-demo-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 100)
}
</script>
