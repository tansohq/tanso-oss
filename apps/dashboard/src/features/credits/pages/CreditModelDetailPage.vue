<template>
  <div class="p-6 pb-16">
    <!-- Breadcrumb -->
    <div class="mb-6">
      <nav class="flex items-center gap-2 text-sm text-muted-foreground mb-2">
        <router-link :to="demoPath('/credits')" class="hover:text-foreground transition-colors">
          Credit Models
        </router-link>
        <ChevronRight class="h-4 w-4" />
        <span class="text-foreground">{{ creditModel?.name ?? 'Loading...' }}</span>
      </nav>
    </div>

    <!-- Loading -->
    <div v-if="isLoading" class="flex items-center justify-center py-12">
      <Loader2 class="h-8 w-8 animate-spin text-muted-foreground" />
    </div>

    <!-- Error -->
    <div v-else-if="isError" class="flex flex-col items-center justify-center py-12 bg-card rounded-lg border">
      <AlertCircle class="w-12 h-12 text-destructive mb-4" />
      <p class="text-lg font-medium text-foreground mb-2">Unable to load credit model</p>
      <p class="text-sm text-muted-foreground mb-4">The credit model could not be found or an error occurred.</p>
      <div class="flex gap-2">
        <Button variant="outline" @click="refetch">
          <RefreshCw class="w-4 h-4 mr-2" />
          Try Again
        </Button>
        <Button variant="outline" @click="router.push(demoPath('/credits'))">
          <ArrowLeft class="w-4 h-4 mr-2" />
          Back to Credit Models
        </Button>
      </div>
    </div>

    <!-- Content -->
    <div v-else-if="creditModel" class="space-y-6 max-w-5xl">
      <!-- Header -->
      <div>
        <h1 class="text-2xl font-semibold tracking-tight text-foreground">{{ creditModel.name }}</h1>
        <div class="flex items-center gap-3 mt-1">
          <Badge class="bg-blue-50 text-blue-700 border border-blue-200/50 shadow-none">
            {{ creditModel.denomination }}
          </Badge>
          <span class="text-sm text-muted-foreground font-mono">{{ creditModel.id }}</span>
          <CopyButton :value="creditModel.id" />
        </div>
      </div>

      <!-- Details Card -->
      <Card>
        <CardHeader>
          <CardTitle class="text-base">Details</CardTitle>
        </CardHeader>
        <CardContent>
          <dl class="grid grid-cols-2 lg:grid-cols-3 gap-6 text-sm">
            <div>
              <dt class="text-muted-foreground">Name</dt>
              <dd>{{ creditModel.name }}</dd>
            </div>
            <div>
              <dt class="text-muted-foreground">Denomination</dt>
              <dd>{{ creditModel.denomination }}</dd>
            </div>
            <div>
              <dt class="text-muted-foreground">Hard Limit</dt>
              <dd>{{ creditModel.hardLimit ? 'Yes' : 'No' }}</dd>
            </div>
            <div>
              <dt class="text-muted-foreground">Rollover Policy</dt>
              <dd>{{ creditModel.rolloverPolicy ?? 'NONE' }}</dd>
            </div>
            <div v-if="creditModel.description" class="col-span-2">
              <dt class="text-muted-foreground">Description</dt>
              <dd>{{ creditModel.description }}</dd>
            </div>
            <div>
              <dt class="text-muted-foreground">Created</dt>
              <dd class="tabular-nums">{{ formatDateTime(creditModel.createdAt) }}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      <!-- Plan Allocations -->
      <Card>
        <CardHeader>
          <CardTitle class="text-base">Plan Allocations</CardTitle>
        </CardHeader>
        <CardContent>
          <div v-if="allocationsLoading" class="py-4">
            <Skeleton class="h-10 w-full mb-2" />
            <Skeleton class="h-10 w-full" />
          </div>

          <div v-else-if="allocations.length === 0" class="text-center py-8 text-muted-foreground">
            <p class="text-sm">No plans are using this credit model yet.</p>
            <p class="text-xs mt-1">Attach this credit model to a plan from the plan detail page.</p>
          </div>

          <Table v-else>
            <TableHeader>
              <TableRow>
                <TableHead>Plan</TableHead>
                <TableHead>Credit Amount</TableHead>
                <TableHead>Grant Expiry</TableHead>
                <TableHead>Created</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="allocation in allocations" :key="allocation.id">
                <TableCell>
                  <router-link
                    :to="demoPath(`/plans/${allocation.planId}`)"
                    class="text-primary hover:underline"
                  >
                    {{ allocation.planId }}
                  </router-link>
                </TableCell>
                <TableCell class="font-mono">{{ allocation.creditAmount }}</TableCell>
                <TableCell>
                  {{ allocation.grantExpiresMonths ? `${allocation.grantExpiresMonths} months` : 'Never' }}
                </TableCell>
                <TableCell>{{ formatDateTime(allocation.createdAt) }}</TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from '@/components/ui/table'
import { ChevronRight, Loader2, AlertCircle, RefreshCw, ArrowLeft } from 'lucide-vue-next'
import CopyButton from '@/components/CopyButton.vue'
import { formatDateTime } from '@/lib/formatters'
import { useCreditModelQuery } from '../queries'
import { usePlanCreditAllocationsQuery } from '../queries'
import { useDemoPrefix } from '@/lib/useDemoPrefix'
import type { CreditModel, PlanCreditAllocation } from '../types'

const route = useRoute()
const router = useRouter()
const { isDemo, demoPath } = useDemoPrefix()
const creditModelId = computed(() => route.params.id as string)

const DEMO_CREDIT_MODELS: Record<string, CreditModel> = {
  cm_1: { id: 'cm_1', name: 'API Credits', denomination: 'api_credits', description: 'Standard credits for API usage across all endpoints', hardLimit: true, rolloverPolicy: 'NONE', createdAt: '2026-01-14T00:00:00Z', modifiedAt: '2026-01-14T00:00:00Z' } as CreditModel,
  cm_2: { id: 'cm_2', name: 'Enrichment Credits', denomination: 'enrichment_credits', description: 'Credits consumed when enriching contact or company data', hardLimit: false, rolloverPolicy: 'FULL', createdAt: '2026-02-01T00:00:00Z', modifiedAt: '2026-02-01T00:00:00Z' } as CreditModel,
  cm_3: { id: 'cm_3', name: 'Email Send Credits', denomination: 'email_sends', description: 'Credits consumed per email sent through sequences', hardLimit: true, rolloverPolicy: 'CAPPED', rolloverCap: 1000, createdAt: '2026-02-14T00:00:00Z', modifiedAt: '2026-02-14T00:00:00Z' } as CreditModel
}

const { data, isLoading, isError, refetch } = isDemo.value
  ? { data: ref(null), isLoading: ref(false), isError: ref(false), refetch: () => {} }
  : useCreditModelQuery(creditModelId)

const creditModel = computed<CreditModel | null>(() => {
  if (isDemo.value) return DEMO_CREDIT_MODELS[creditModelId.value] ?? null
  return data.value?.data ?? null
})

const { data: allocationsData, isLoading: allocationsLoading } = isDemo.value
  ? { data: ref(null), isLoading: ref(false) }
  : usePlanCreditAllocationsQuery(creditModelId)

const allocations = computed<PlanCreditAllocation[]>(() => {
  if (isDemo.value) return []
  if (!allocationsData.value?.data) return []
  return Array.isArray(allocationsData.value.data) ? allocationsData.value.data : []
})
</script>
