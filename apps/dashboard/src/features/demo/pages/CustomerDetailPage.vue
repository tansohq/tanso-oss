<template>
  <div class="p-6 pb-16">
    <!-- Breadcrumb -->
    <div class="mb-6">
      <nav class="flex items-center gap-2 text-sm text-muted-foreground mb-2">
        <router-link to="/demo/customers" class="hover:text-foreground transition-colors">
          Customers
        </router-link>
        <ChevronRight class="h-4 w-4" />
        <span class="text-foreground">{{ customer?.name }}</span>
      </nav>
    </div>

    <div v-if="customer" class="space-y-6 max-w-5xl">
      <!-- Header -->
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-2xl font-semibold text-foreground">{{ customer.name }}</h1>
          <p v-if="customerSubscriptions.length > 0" class="text-muted-foreground">
            {{ activeSubCount }} active {{ activeSubCount === 1 ? 'subscription' : 'subscriptions' }}
          </p>
        </div>
        <Button variant="outline" size="sm">
          <Pencil class="w-3.5 h-3.5 mr-1" />
          Edit
        </Button>
      </div>

      <!-- Overview -->
      <Card>
        <CardHeader>
          <CardTitle class="text-base">Overview</CardTitle>
        </CardHeader>
        <CardContent>
          <dl class="grid grid-cols-2 lg:grid-cols-3 gap-6 text-sm">
            <div>
              <dt class="text-muted-foreground">Email</dt>
              <dd class="truncate" :title="customer.email">{{ customer.email }}</dd>
            </div>
            <div>
              <dt class="text-muted-foreground">Created</dt>
              <dd class="tabular-nums">{{ customer.customerSince }}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      <!-- Subscriptions -->
      <Card v-if="customerSubscriptions.length > 0">
        <CardHeader>
          <CardTitle class="text-base">Subscriptions</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Plan</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Period End</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow
                v-for="sub in customerSubscriptions"
                :key="sub.id"
                class="cursor-pointer hover:bg-muted/50"
                @click="router.push(`/demo/subscriptions/${sub.id}`)"
              >
                <TableCell class="font-medium">{{ sub.planName }}</TableCell>
                <TableCell>
                  <Badge
                    :class="sub.status === 'active' ? 'bg-emerald-50 text-emerald-700 border border-emerald-200/50' : 'bg-gray-50 text-gray-600 border border-gray-200/50'"
                    class="shadow-none"
                  >
                    {{ sub.status === 'active' ? 'Active' : 'Inactive' }}
                  </Badge>
                </TableCell>
                <TableCell class="tabular-nums text-muted-foreground">{{ sub.currentPeriodEnd }}</TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Card v-else>
        <CardContent class="py-8">
          <p class="text-sm text-muted-foreground text-center">No subscriptions yet</p>
        </CardContent>
      </Card>

      <!-- Credit Pools -->
      <Card>
        <CardHeader>
          <CardTitle class="text-base">Credit Pools</CardTitle>
        </CardHeader>
        <CardContent>
          <div v-if="customerCredits.length > 0">
            <div
              v-for="pool in customerCredits"
              :key="pool.name"
              class="border rounded-lg p-4 mb-3 last:mb-0"
            >
              <div class="flex items-center justify-between mb-2">
                <div class="flex items-center gap-2">
                  <Coins class="h-4 w-4 text-muted-foreground" />
                  <span class="text-sm font-medium">{{ pool.name }}</span>
                  <Badge class="bg-emerald-50 text-emerald-700 border border-emerald-200/50 shadow-none">Active</Badge>
                </div>
              </div>
              <div class="text-2xl font-semibold tabular-nums">
                {{ pool.balance.toLocaleString() }}
                <span class="text-sm font-normal text-muted-foreground">credits</span>
              </div>
            </div>
          </div>
          <div v-else class="text-center py-6 text-muted-foreground">
            <Coins class="w-10 h-10 mx-auto mb-2" />
            <p class="text-sm">No credit pools for this customer.</p>
          </div>
        </CardContent>
      </Card>
    </div>

    <div v-else class="text-muted-foreground">Customer not found</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronRight, Pencil, Coins } from 'lucide-vue-next'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
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

const route = useRoute()
const router = useRouter()
const { customers, subscriptionsData } = useDemoState()

const customer = computed(() => {
  const id = route.params.id as string
  return customers.find((c) => c.id === id) ?? null
})

const customerSubscriptions = computed(() => {
  if (!customer.value) return []
  return subscriptionsData.value.filter((s) => s.customerId === customer.value!.id)
})

const activeSubCount = computed(() =>
  customerSubscriptions.value.filter((s) => s.status === 'active').length
)

const customerCredits = computed(() => {
  if (!customer.value) return []
  // Mock credit pools per customer
  const creditMap: Record<string, { name: string; balance: number }[]> = {
    cust_1: [{ name: 'API Credits', balance: 8420 }],
    cust_2: [{ name: 'API Credits', balance: 2150 }, { name: 'Enrichment Credits', balance: 500 }],
    cust_3: [{ name: 'API Credits', balance: 45000 }],
    cust_4: [],
    cust_5: [{ name: 'API Credits', balance: 12800 }]
  }
  return creditMap[customer.value.id] ?? []
})

</script>
