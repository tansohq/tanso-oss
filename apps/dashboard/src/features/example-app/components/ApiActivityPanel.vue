<template>
  <div
    :class="[
      'fixed bottom-4 right-4 w-96 bg-zinc-900 border border-zinc-700 rounded-lg shadow-2xl z-40 overflow-hidden transition-all',
      collapsed ? 'h-12' : 'max-h-[400px]'
    ]"
  >
    <!-- Header -->
    <div
      class="flex items-center justify-between px-3 py-2.5 bg-gradient-to-r from-zinc-800 to-zinc-800/80 border-b border-zinc-700"
    >
      <div class="flex items-center gap-2">
        <div class="p-1 rounded bg-violet-500/20">
          <Zap class="h-3.5 w-3.5 text-violet-400" />
        </div>
        <span class="text-xs font-semibold text-zinc-200">API Activity</span>
        <span
          v-if="logs.length > 0"
          class="text-[10px] text-violet-400 bg-violet-500/20 px-1.5 py-0.5 rounded-full font-medium"
        >
          {{ logs.length }}
        </span>
      </div>
      <div class="flex items-center gap-1">
        <Button
          variant="ghost"
          size="sm"
          class="h-6 w-6 p-0 text-zinc-400 hover:text-zinc-200 hover:bg-zinc-700"
          title="Clear logs"
          @click="clearLogs"
        >
          <Trash2 class="h-3.5 w-3.5" />
        </Button>
        <Button
          variant="ghost"
          size="sm"
          class="h-6 w-6 p-0 text-zinc-400 hover:text-zinc-200 hover:bg-zinc-700"
          @click="toggleCollapsed"
        >
          <ChevronUp v-if="collapsed" class="h-4 w-4" />
          <ChevronDown v-else class="h-4 w-4" />
        </Button>
      </div>
    </div>

    <!-- Log entries -->
    <div v-if="!collapsed" class="overflow-y-auto max-h-[352px]">
      <div v-if="logs.length === 0" class="py-8 text-center text-zinc-500 text-xs">
        <p>No API calls yet</p>
        <p class="mt-1 text-zinc-600">Interact with features to see API activity</p>
      </div>
      <div
        v-for="log in logs"
        :key="log.id"
        :class="[
          'border-b border-zinc-800 py-2.5 px-3 hover:bg-zinc-800/50 cursor-pointer transition-colors',
          log.status === 'error' ? 'bg-red-500/5' : ''
        ]"
        @click="openDetail(log)"
      >
        <div class="flex items-center gap-2">
          <Loader2
            v-if="log.status === 'pending'"
            class="h-3 w-3 animate-spin text-blue-400 shrink-0"
          />
          <Check
            v-else-if="log.status === 'success'"
            class="h-3 w-3 text-emerald-400 shrink-0"
          />
          <X v-else class="h-3 w-3 text-red-400 shrink-0" />
          <span
            :class="[
              'text-[10px] font-mono font-bold px-1.5 py-0.5 rounded',
              getMethodColor(log.method)
            ]"
          >
            {{ log.method }}
          </span>
          <span class="text-xs text-zinc-300 flex-1 truncate">
            {{ getEndpointDescription(log.endpoint, log.method) }}
          </span>
          <span v-if="log.duration !== undefined" class="text-[10px] text-zinc-500 font-mono">
            {{ log.duration }}ms
          </span>
        </div>

        <div class="mt-1 pl-5 text-[10px] text-zinc-500 font-mono truncate">
          {{ formatEndpoint(log.endpoint) }}
        </div>
      </div>
    </div>
  </div>

  <!-- Detail modal -->
  <Dialog v-model:open="dialogOpen">
    <DialogContent class="max-w-2xl max-h-[80vh] overflow-hidden flex flex-col bg-zinc-900 border-zinc-700 text-zinc-200">
      <DialogHeader>
        <DialogTitle class="flex items-center gap-2 text-zinc-200">
          <span
            :class="[
              'text-xs font-mono font-bold px-2 py-0.5 rounded',
              selectedLog ? getMethodColor(selectedLog.method) : ''
            ]"
          >
            {{ selectedLog?.method }}
          </span>
          <span class="text-sm font-mono truncate">
            {{ selectedLog ? formatEndpoint(selectedLog.endpoint) : '' }}
          </span>
        </DialogTitle>
        <DialogDescription class="text-zinc-400">
          {{ selectedLog ? getEndpointDescription(selectedLog.endpoint, selectedLog.method) : '' }}
        </DialogDescription>
      </DialogHeader>

      <div v-if="selectedLog" class="flex-1 overflow-y-auto space-y-4 pr-1">
        <!-- Metadata -->
        <div class="grid grid-cols-2 gap-3 text-xs">
          <div>
            <span class="text-zinc-500">Status</span>
            <div class="mt-0.5 flex items-center gap-1.5">
              <span
                v-if="selectedLog.statusCode"
                :class="[
                  'font-mono font-semibold',
                  selectedLog.statusCode >= 400 ? 'text-red-400' : 'text-emerald-400'
                ]"
              >
                {{ selectedLog.statusCode }}
              </span>
              <span v-else class="text-blue-400">Pending</span>
            </div>
          </div>
          <div>
            <span class="text-zinc-500">Duration</span>
            <div class="mt-0.5 font-mono">
              {{ selectedLog.duration !== undefined ? `${selectedLog.duration}ms` : '—' }}
            </div>
          </div>
          <div>
            <span class="text-zinc-500">Timestamp</span>
            <div class="mt-0.5 font-mono">
              {{ new Date(selectedLog.timestamp).toLocaleTimeString() }}
            </div>
          </div>
        </div>

        <!-- Request -->
        <div>
          <h4 class="text-xs font-semibold text-zinc-400 uppercase tracking-wider mb-2">Request</h4>

          <div
            v-if="selectedLog.requestHeaders && Object.keys(selectedLog.requestHeaders).length > 0"
            class="mb-2"
          >
            <span class="text-[10px] text-zinc-500 uppercase tracking-wider">Headers</span>
            <pre class="mt-1 p-3 bg-zinc-950 rounded-md text-[11px] text-zinc-300 font-mono overflow-x-auto whitespace-pre-wrap break-all">{{ formatJson(selectedLog.requestHeaders) }}</pre>
          </div>

          <div v-if="selectedLog.requestBody !== undefined">
            <span class="text-[10px] text-zinc-500 uppercase tracking-wider">Body</span>
            <pre class="mt-1 p-3 bg-zinc-950 rounded-md text-[11px] text-zinc-300 font-mono overflow-x-auto whitespace-pre-wrap break-all">{{ formatJson(selectedLog.requestBody) }}</pre>
          </div>

          <div
            v-if="!selectedLog.requestHeaders || Object.keys(selectedLog.requestHeaders).length === 0"
            class="text-xs text-zinc-600"
          >
            <span v-if="selectedLog.requestBody === undefined">No request body</span>
          </div>
        </div>

        <!-- Response -->
        <div>
          <h4 class="text-xs font-semibold text-zinc-400 uppercase tracking-wider mb-2">Response</h4>
          <div v-if="selectedLog.responseBody !== undefined">
            <pre class="p-3 bg-zinc-950 rounded-md text-[11px] text-zinc-300 font-mono overflow-x-auto whitespace-pre-wrap break-all">{{ formatJson(selectedLog.responseBody) }}</pre>
          </div>
          <div v-else class="text-xs text-zinc-600">
            {{ selectedLog.status === 'pending' ? 'Awaiting response...' : 'No response body' }}
          </div>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Zap, ChevronDown, ChevronUp, Loader2, Check, X, Trash2 } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { useApiActivityLog, type ApiLog } from '../composables/useApiActivityLog'

const { logs, collapsed, clearLogs, toggleCollapsed } = useApiActivityLog()
const dialogOpen = ref(false)
const selectedLog = ref<ApiLog | null>(null)

function openDetail(log: ApiLog) {
  selectedLog.value = log
  dialogOpen.value = true
}

function formatJson(data: unknown): string {
  if (typeof data === 'string') return data
  try {
    return JSON.stringify(data, null, 2)
  } catch {
    return String(data)
  }
}

function getMethodColor(method: string): string {
  switch (method) {
    case 'GET':
      return 'bg-emerald-500/20 text-emerald-400'
    case 'POST':
      return 'bg-blue-500/20 text-blue-400'
    case 'PUT':
      return 'bg-amber-500/20 text-amber-400'
    case 'DELETE':
      return 'bg-red-500/20 text-red-400'
    default:
      return 'bg-gray-500/20 text-gray-400'
  }
}

function formatEndpoint(endpoint: string): string {
  return endpoint
    .replace(/^https?:\/\/[^/]+/, '')
    .replace(
      /\/([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})/gi,
      '/{id}'
    )
    .replace(/\/([a-f0-9]{20,})/gi, '/{id}')
}

function getEndpointDescription(endpoint: string, method: string): string {
  const patterns: [RegExp, string][] = [
    [/\/client\/entitlements\//, 'Checking feature entitlement'],
    [/\/client\/events$/, method === 'POST' ? 'Tracking usage event' : 'API request']
  ]

  for (const [pattern, desc] of patterns) {
    if (pattern.test(endpoint)) return desc
  }
  return 'API request'
}
</script>
