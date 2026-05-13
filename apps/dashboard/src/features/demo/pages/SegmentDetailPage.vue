<template>
  <div class="p-6 pb-16">
    <!-- Breadcrumb -->
    <div class="mb-6">
      <nav class="flex items-center gap-2 text-sm text-muted-foreground mb-2">
        <router-link
          :to="isDemo ? '/demo/segments' : '/segments'"
          class="hover:text-foreground transition-colors"
        >
          Segments
        </router-link>
        <ChevronRight class="h-4 w-4" />
        <span class="text-foreground">{{ segment?.name }}</span>
      </nav>
      <div class="flex items-center justify-between">
        <h1 class="text-2xl font-semibold text-foreground">{{ segment?.name }}</h1>
        <Button v-if="segment" variant="outline" size="sm" @click="editSegment(segment.id)">
          <Pencil class="h-4 w-4 mr-2" />
          Edit
        </Button>
      </div>
    </div>

    <div v-if="segment" class="space-y-6 max-w-4xl">
      <!-- Suggested Simulation Alert -->
      <SuggestedSimulationAlert :segment="segment" />

      <!-- Summary Stats -->
      <div class="grid grid-cols-2 gap-4">
        <Card class="p-4">
          <div class="text-sm text-muted-foreground">Accounts</div>
          <div class="text-2xl font-semibold">{{ segment.customerCount.toLocaleString() }}</div>
          <div class="text-sm text-muted-foreground">${{ formatCurrency(segment.mrr) }} MRR</div>
        </Card>
        <Card class="p-4">
          <div class="text-sm text-muted-foreground">Avg Margin</div>
          <div class="text-2xl font-semibold" :class="marginClass">
            {{ segment.grossMarginPct !== null ? `${segment.grossMarginPct}%` : '—' }}
          </div>
          <TrendIndicator
            v-if="segment.marginTrend !== 'stable' && segment.marginTrendValue"
            :direction="segment.marginTrend"
            :value="segment.marginTrendValue"
            suffix="pp"
            class="text-sm"
          />
        </Card>
      </div>

      <!-- Conditions -->
      <Card>
        <CardHeader>
          <CardTitle class="text-base">Conditions</CardTitle>
        </CardHeader>
        <CardContent>
          <div class="space-y-2">
            <div
              v-for="rule in segment.rules"
              :key="rule.id"
              class="flex items-center gap-2 text-sm p-2 bg-muted/50 rounded"
            >
              <span class="text-muted-foreground">{{ rule.fieldLabel }}</span>
              <span>{{ rule.operatorLabel }}</span>
              <span class="font-medium">{{ rule.valueLabel }}</span>
            </div>
            <div v-if="segment.rules.length === 0" class="text-sm text-muted-foreground">
              No conditions defined
            </div>
          </div>
        </CardContent>
      </Card>

      <!-- Customer Table -->
      <Card>
        <CardHeader>
          <CardTitle class="text-base">Customers</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Customer</TableHead>
                <TableHead class="text-right">MRR</TableHead>
                <TableHead class="text-right">Margin</TableHead>
                <TableHead class="text-right">Trend</TableHead>
                <TableHead>In Segment</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow
                v-for="customer in segment.customers"
                :key="customer.id"
                class="cursor-pointer hover:bg-muted/50"
                @click="goToCustomer(customer.id)"
              >
                <TableCell class="font-medium">{{ customer.name }}</TableCell>
                <TableCell class="text-right tabular-nums"
                  >${{ customer.mrr.toLocaleString() }}</TableCell
                >
                <TableCell class="text-right">
                  <MarginBadge :margin="customer.grossMarginPct" />
                </TableCell>
                <TableCell class="text-right">
                  <TrendIndicator
                    v-if="customer.marginTrend !== null && customer.marginTrend !== 0"
                    :direction="customer.marginTrend < 0 ? 'down' : 'up'"
                    :value="Math.abs(customer.marginTrend)"
                    suffix="pp"
                  />
                  <span v-else class="text-muted-foreground">—</span>
                </TableCell>
                <TableCell class="text-muted-foreground">{{ customer.inSegmentSince }}</TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>

    <!-- Loading state -->
    <div v-else class="text-muted-foreground">Loading segment...</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronRight, Pencil } from 'lucide-vue-next'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import { useDemoState } from '../composables/useDemoState'
import MarginBadge from '../components/shared/MarginBadge.vue'
import TrendIndicator from '../components/shared/TrendIndicator.vue'
import SuggestedSimulationAlert from '../components/segments/SuggestedSimulationAlert.vue'

const route = useRoute()
const router = useRouter()
const { segmentsData, editSegment } = useDemoState()

// Check if we're in demo mode
const isDemo = computed(() => route.path.startsWith('/demo'))

function goToCustomer(id: string) {
  const basePath = isDemo.value ? '/demo/customer' : '/customer'
  router.push({
    path: `${basePath}/${id}`,
    query: {
      from: 'segment',
      segmentId: segment.value?.id,
      segmentName: segment.value?.name
    }
  })
}

const segment = computed(() => {
  const id = route.params.id as string
  return segmentsData.value.find((c) => c.id === id) ?? null
})

const marginClass = computed(() => {
  if (!segment.value || segment.value.grossMarginPct === null) return ''
  if (segment.value.grossMarginPct < 0) return 'text-red-600'
  if (segment.value.grossMarginPct < 50) return 'text-amber-600'
  return 'text-green-600'
})

function formatCurrency(value: number): string {
  if (value >= 1000000) return (value / 1000000).toFixed(1) + 'M'
  if (value >= 1000) return (value / 1000).toFixed(1) + 'K'
  return value.toLocaleString()
}
</script>
