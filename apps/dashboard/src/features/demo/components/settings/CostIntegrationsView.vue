<template>
  <div class="space-y-6">
    <!-- Stats -->
    <div class="grid grid-cols-4 gap-5">
      <Card class="p-6">
        <div class="flex items-center gap-5">
          <div class="rounded-xl bg-primary/10 p-2.5">
            <Clock class="h-5 w-5 text-primary" />
          </div>
          <div>
            <p class="text-sm text-muted-foreground">Last Sync</p>
            <p class="text-2xl font-semibold tracking-tight">
              {{ integrationSummaryStats.lastUpload }}
            </p>
          </div>
        </div>
      </Card>
      <Card class="p-6">
        <div class="flex items-center gap-5">
          <div class="rounded-xl bg-blue-500/10 p-2.5">
            <Database class="h-5 w-5 text-blue-500" />
          </div>
          <div>
            <p class="text-sm text-muted-foreground">Cost Records</p>
            <p class="text-2xl font-semibold tracking-tight">
              {{ integrationSummaryStats.costRecords.toLocaleString() }}
            </p>
          </div>
        </div>
      </Card>
      <Card class="p-6">
        <div class="flex items-center gap-5">
          <div class="rounded-xl bg-green-500/10 p-2.5">
            <PieChart class="h-5 w-5 text-green-500" />
          </div>
          <div>
            <p class="text-sm text-muted-foreground">Coverage</p>
            <p class="text-2xl font-semibold tracking-tight">
              {{ integrationSummaryStats.coverage }}%
            </p>
          </div>
        </div>
      </Card>
      <Card class="p-6">
        <div class="flex items-center gap-5">
          <div class="rounded-xl bg-amber-500/10 p-2.5">
            <Sparkles class="h-5 w-5 text-amber-500" />
          </div>
          <div>
            <p class="text-sm text-muted-foreground">Data Quality</p>
            <p class="text-2xl font-semibold tracking-tight text-green-600">
              {{ integrationSummaryStats.dataQuality }}
            </p>
          </div>
        </div>
      </Card>
    </div>

    <!-- Integration Cards -->
    <div class="space-y-4">
      <Card v-for="integration in integrations" :key="integration.id">
        <CardHeader>
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              <div
                class="rounded-lg p-2"
                :class="integration.status === 'connected' ? 'bg-green-100' : 'bg-muted'"
              >
                <component
                  :is="getIntegrationIcon(integration.id)"
                  class="h-5 w-5"
                  :class="
                    integration.status === 'connected' ? 'text-green-600' : 'text-muted-foreground'
                  "
                />
              </div>
              <div>
                <CardTitle class="text-base flex items-center gap-2">
                  {{ integration.name }}
                  <span
                    v-if="integration.status === 'connected'"
                    class="h-2 w-2 rounded-full bg-green-500"
                  />
                </CardTitle>
                <CardDescription class="mt-0.5">{{ integration.description }}</CardDescription>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <Badge
                :class="
                  integration.status === 'connected'
                    ? 'bg-green-100 text-green-700'
                    : 'bg-gray-100 text-gray-600'
                "
              >
                {{ integration.status === 'connected' ? 'Connected' : 'Not Connected' }}
              </Badge>
              <Button
                v-if="integration.status === 'connected'"
                variant="outline"
                size="sm"
                @click="configureIntegration(integration)"
              >
                Configure
              </Button>
              <Button v-else size="sm" @click="connectIntegration(integration)"> Connect </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent v-if="integration.lastSync">
          <p class="text-sm text-muted-foreground">
            Last sync: {{ integration.lastSync }}
            <span v-if="integration.customerCount">
              · {{ integration.customerCount.toLocaleString() }} customers</span
            >
            <span v-if="integration.mtdCost">
              · ${{ integration.mtdCost.toLocaleString() }} MTD</span
            >
            <span v-if="integration.recordCount">
              · {{ integration.recordCount.toLocaleString() }} records</span
            >
          </p>
        </CardContent>
      </Card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useToast } from '@/components/ui/toast'
import {
  Clock,
  Database,
  PieChart,
  Sparkles,
  CreditCard,
  Bot,
  Cloud,
  FileUp,
  type LucideIcon
} from 'lucide-vue-next'
import { useDemoState } from '../../composables/useDemoState'
import type { Integration } from '../../types'

const { integrations, integrationSummaryStats } = useDemoState()
const { toast } = useToast()

const integrationIconMap: Record<string, LucideIcon> = {
  stripe: CreditCard,
  openai: Sparkles,
  anthropic: Bot,
  aws: Cloud,
  csv: FileUp
}

function getIntegrationIcon(id: string): LucideIcon {
  return integrationIconMap[id] || Database
}

function connectIntegration(integration: Integration) {
  toast({
    title: 'Connect Integration',
    description: `Would start OAuth flow for ${integration.name}`
  })
}

function configureIntegration(integration: Integration) {
  toast({
    title: 'Configure Integration',
    description: `Would open settings for ${integration.name}`
  })
}
</script>
