<template>
  <div class="p-6 pb-16 max-w-3xl">
    <div class="mb-6">
      <h1 class="text-2xl font-semibold tracking-tight text-foreground">Getting Started</h1>
      <p class="text-sm text-muted-foreground mt-1">
        Connect Tanso to your AI tools in under a minute.
      </p>
    </div>

    <div class="space-y-6">
      <!-- Step 1: Connect MCP -->
      <Card class="p-6">
        <div class="flex items-start gap-4">
          <div
            class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full border-2 border-foreground/20 text-muted-foreground mt-0.5"
          >
            <span class="text-xs font-semibold">1</span>
          </div>
          <div class="min-w-0 flex-1 space-y-4">
            <div>
              <div class="flex items-center gap-2 mb-1">
                <h3 class="text-base font-medium">Connect your MCP server</h3>
                <Badge
                  variant="outline"
                  :class="
                    isSandbox
                      ? 'text-amber-600 border-amber-200 bg-amber-50'
                      : 'text-emerald-600 border-emerald-200 bg-emerald-50'
                  "
                >
                  {{ isSandbox ? 'Sandbox' : 'Production' }}
                </Badge>
              </div>
              <p class="text-sm text-muted-foreground">
                Pick your tool. Your API key is pre-filled.
              </p>
            </div>

            <div
              v-if="isLoadingApiKey"
              class="flex items-center gap-2 text-sm text-muted-foreground py-4"
            >
              <Loader2 class="w-4 h-4 animate-spin" />
              Loading...
            </div>
            <div v-else-if="apiKeyData?.data?.apiKey" class="space-y-4">
              <!-- Tool selector -->
              <div class="flex gap-4 sm:gap-6 border-b -mx-0 overflow-x-auto">
                <button
                  v-for="tool in tools"
                  :key="tool.id"
                  class="pb-2.5 text-sm font-medium transition-colors relative whitespace-nowrap"
                  :class="
                    selectedTool === tool.id
                      ? 'text-foreground'
                      : 'text-muted-foreground hover:text-foreground'
                  "
                  @click="selectedTool = tool.id"
                >
                  {{ tool.label }}
                  <span
                    v-if="selectedTool === tool.id"
                    class="absolute bottom-0 left-0 right-0 h-[2.5px] bg-foreground rounded-full"
                  />
                </button>
              </div>

              <!-- Claude Code -->
              <div v-if="selectedTool === 'claude-code'" class="space-y-2">
                <p class="text-xs text-muted-foreground">Run this in your terminal:</p>
                <div class="flex items-center gap-2">
                  <div
                    class="flex-1 bg-zinc-950 text-zinc-100 px-3 py-2 rounded-md font-mono text-xs border border-zinc-800 overflow-x-auto"
                  >
                    <span class="text-zinc-500 select-none">$ </span>{{ cliCommandDisplay }}
                  </div>
                  <Button
                    variant="ghost"
                    size="icon"
                    class="h-8 w-8 shrink-0"
                    :aria-label="showApiKey ? 'Hide API key' : 'Show API key'"
                    @click="showApiKey = !showApiKey"
                  >
                    <EyeOff v-if="showApiKey" class="w-3.5 h-3.5" />
                    <Eye v-else class="w-3.5 h-3.5" />
                  </Button>
                  <CopyButton :value="cliCommand" label="Command" />
                </div>
              </div>

              <!-- Cursor -->
              <div v-if="selectedTool === 'cursor'" class="space-y-2">
                <p class="text-xs text-muted-foreground">
                  Add this to
                  <code class="bg-muted px-1 py-0.5 rounded text-[11px]">~/.cursor/mcp.json</code>
                </p>
                <div class="relative">
                  <pre
                    class="bg-zinc-950 text-zinc-100 rounded-md border border-zinc-800 p-3 text-xs font-mono overflow-x-auto leading-relaxed"
                  ><code>{{ mcpConfigDisplayText }}</code></pre>
                  <div class="absolute top-2 right-2 flex items-center gap-1">
                    <Button
                      variant="ghost"
                      size="icon"
                      class="h-7 w-7"
                      :aria-label="showApiKey ? 'Hide API key' : 'Show API key'"
                      @click="showApiKey = !showApiKey"
                    >
                      <EyeOff v-if="showApiKey" class="w-3.5 h-3.5" />
                      <Eye v-else class="w-3.5 h-3.5" />
                    </Button>
                    <CopyButton :value="mcpConfigText" label="JSON config" />
                  </div>
                </div>
              </div>

              <!-- VS Code -->
              <div v-if="selectedTool === 'vscode'" class="space-y-2">
                <p class="text-xs text-muted-foreground">
                  Add this to
                  <code class="bg-muted px-1 py-0.5 rounded text-[11px]">.vscode/mcp.json</code>
                  in your workspace
                </p>
                <div class="relative">
                  <pre
                    class="bg-zinc-950 text-zinc-100 rounded-md border border-zinc-800 p-3 text-xs font-mono overflow-x-auto leading-relaxed"
                  ><code>{{ vscodeConfigDisplayText }}</code></pre>
                  <div class="absolute top-2 right-2 flex items-center gap-1">
                    <Button
                      variant="ghost"
                      size="icon"
                      class="h-7 w-7"
                      :aria-label="showApiKey ? 'Hide API key' : 'Show API key'"
                      @click="showApiKey = !showApiKey"
                    >
                      <EyeOff v-if="showApiKey" class="w-3.5 h-3.5" />
                      <Eye v-else class="w-3.5 h-3.5" />
                    </Button>
                    <CopyButton :value="vscodeConfigText" label="JSON config" />
                  </div>
                </div>
              </div>

              <!-- Windsurf -->
              <div v-if="selectedTool === 'windsurf'" class="space-y-2">
                <p class="text-xs text-muted-foreground">
                  Add this to
                  <code class="bg-muted px-1 py-0.5 rounded text-[11px]"
                    >~/.codeium/windsurf/mcp_config.json</code
                  >
                </p>
                <div class="relative">
                  <pre
                    class="bg-zinc-950 text-zinc-100 rounded-md border border-zinc-800 p-3 text-xs font-mono overflow-x-auto leading-relaxed"
                  ><code>{{ mcpConfigDisplayText }}</code></pre>
                  <div class="absolute top-2 right-2 flex items-center gap-1">
                    <Button
                      variant="ghost"
                      size="icon"
                      class="h-7 w-7"
                      :aria-label="showApiKey ? 'Hide API key' : 'Show API key'"
                      @click="showApiKey = !showApiKey"
                    >
                      <EyeOff v-if="showApiKey" class="w-3.5 h-3.5" />
                      <Eye v-else class="w-3.5 h-3.5" />
                    </Button>
                    <CopyButton :value="mcpConfigText" label="JSON config" />
                  </div>
                </div>
              </div>

              <!-- Codex -->
              <div v-if="selectedTool === 'codex'" class="space-y-2">
                <p class="text-xs text-muted-foreground">
                  Add this to
                  <code class="bg-muted px-1 py-0.5 rounded text-[11px]">~/.codex/config.toml</code>
                </p>
                <div class="relative">
                  <pre
                    class="bg-zinc-950 text-zinc-100 rounded-md border border-zinc-800 p-3 text-xs font-mono overflow-x-auto leading-relaxed"
                  ><code>{{ codexConfigDisplayText }}</code></pre>
                  <div class="absolute top-2 right-2 flex items-center gap-1">
                    <Button
                      variant="ghost"
                      size="icon"
                      class="h-7 w-7"
                      :aria-label="showApiKey ? 'Hide API key' : 'Show API key'"
                      @click="showApiKey = !showApiKey"
                    >
                      <EyeOff v-if="showApiKey" class="w-3.5 h-3.5" />
                      <Eye v-else class="w-3.5 h-3.5" />
                    </Button>
                    <CopyButton :value="codexConfigText" label="TOML config" />
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="flex items-center gap-2 text-sm">
              <span class="text-destructive">Failed to load API key</span>
              <Button variant="outline" size="sm" @click="refetchApiKey">Try again</Button>
            </div>
          </div>
        </div>
      </Card>

      <!-- Step 2: Paste the context file -->
      <Card class="p-6">
        <div class="flex items-start gap-4">
          <div
            class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full border-2 border-foreground/20 text-muted-foreground mt-0.5"
          >
            <span class="text-xs font-semibold">2</span>
          </div>
          <div class="min-w-0 flex-1 space-y-4">
            <div>
              <h3 class="text-base font-medium mb-1">Paste the context file</h3>
              <p class="text-sm text-muted-foreground">
                Paste this URL into your first conversation so your AI knows how to use Tanso's MCP
                tools.
              </p>
            </div>
            <div class="flex items-center gap-2">
              <div class="flex-1 bg-muted px-3 py-2 rounded-md font-mono text-xs border truncate">
                {{ llmsUrl }}
              </div>
              <CopyButton :value="llmsUrl" label="URL" />
            </div>
          </div>
        </div>
      </Card>

      <!-- Step 3: Connect data sources -->
      <Card class="p-6">
        <div class="flex items-start gap-4">
          <div
            class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full border-2 border-foreground/20 text-muted-foreground mt-0.5"
          >
            <span class="text-xs font-semibold">3</span>
          </div>
          <div class="min-w-0 flex-1 space-y-4">
            <div>
              <div class="flex items-center gap-2 mb-1">
                <h3 class="text-base font-medium">Connect Stripe</h3>
                <Badge variant="outline" class="text-muted-foreground">Optional</Badge>
                <Badge
                  v-if="stripeConnected"
                  variant="outline"
                  class="text-emerald-600 border-emerald-200 bg-emerald-50"
                  >Connected</Badge
                >
              </div>
              <p class="text-sm text-muted-foreground">
                Connect Stripe before creating plans in Tanso. Your products, customers, and
                subscriptions will sync automatically — no need to recreate them.
              </p>
            </div>
            <Button variant="outline" size="sm" as-child>
              <router-link to="/settings?tab=integrations"> Go to Integrations </router-link>
            </Button>
          </div>
        </div>
      </Card>

      <!-- Try asking -->
      <div class="pt-4">
        <p class="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-3">
          Try asking your AI
        </p>
        <div class="space-y-2">
          <div class="rounded-lg border bg-muted/30 p-3">
            <p class="text-xs text-muted-foreground italic">
              "Set up two plans: Starter at $29/mo and Pro at $99/mo with usage-based API access."
            </p>
          </div>
          <div class="rounded-lg border bg-muted/30 p-3">
            <p class="text-xs text-muted-foreground italic">
              "Show me all my customers and their current subscription status."
            </p>
          </div>
          <div class="rounded-lg border bg-muted/30 p-3">
            <p class="text-xs text-muted-foreground italic">
              "Which customers are close to hitting their usage limits this month?"
            </p>
          </div>
        </div>
      </div>

      <!-- Resources -->
      <div class="pt-2">
        <p class="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-3">
          Resources
        </p>
        <div class="flex flex-wrap gap-x-6 gap-y-2">
          <a
            href="https://tanso-core.readme.io/"
            target="_blank"
            rel="noopener noreferrer"
            class="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors py-1"
          >
            <BookOpen class="w-3.5 h-3.5" /> REST API Docs <ArrowUpRight class="w-3 h-3" />
          </a>
          <a
            v-if="isSandbox"
            href="/example-app"
            target="_blank"
            rel="noopener noreferrer"
            class="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors py-1"
          >
            <ExternalLink class="w-3.5 h-3.5" /> Sample App
          </a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Loader2, BookOpen, ExternalLink, Eye, EyeOff, ArrowUpRight } from 'lucide-vue-next'
import { useAccountApiKeyQuery } from '@/features/account/queries'
import { useAccountSettingsQuery } from '@/features/integrations/queries'
import CopyButton from '@/components/CopyButton.vue'
import { useEnvironmentStore } from '@/stores/environment'
import { api } from '@/lib/api'

const environmentStore = useEnvironmentStore()
const isSandbox = computed(() => environmentStore.isSandbox)

const {
  data: apiKeyData,
  isLoading: isLoadingApiKey,
  refetch: refetchApiKey
} = useAccountApiKeyQuery()
const { data: settingsData } = useAccountSettingsQuery()

const showApiKey = ref(false)

type ToolId = 'claude-code' | 'cursor' | 'vscode' | 'windsurf' | 'codex'

const tools: { id: ToolId; label: string }[] = [
  { id: 'claude-code', label: 'Claude Code' },
  { id: 'cursor', label: 'Cursor' },
  { id: 'vscode', label: 'VS Code' },
  { id: 'windsurf', label: 'Windsurf' },
  { id: 'codex', label: 'Codex' }
]

const selectedTool = ref<ToolId>('claude-code')

// API key masking
const maskedApiKey = computed(() => {
  const key = apiKeyData.value?.data?.apiKey
  if (!key) return ''
  const prefix = key.startsWith('sk_live_')
    ? 'sk_live_'
    : key.startsWith('sk_test_')
      ? 'sk_test_'
      : ''
  const suffix = key.slice(-4)
  return `${prefix}${'••••••••'}${suffix}`
})

// MCP endpoint and server name
const mcpEndpoint = computed(() => {
  // Derive from the API client so hosted deploys point at the real backend host
  // instead of localhost. Reference isSandbox so this recomputes when the
  // environment switches — the client's base URL is swapped to match it.
  void isSandbox.value
  return `${api.getBaseUrl()}/mcp`
})
const mcpServerName = computed(() => (isSandbox.value ? 'tanso-sandbox' : 'tanso-prod'))

const llmsUrl = '/llms-mcp.txt'

// Claude Code CLI command
const cliCommand = computed(() => {
  const apiKey = apiKeyData.value?.data?.apiKey ?? 'your_api_key_here'
  return `claude mcp add ${mcpServerName.value} --transport http --header "X-API-Key: ${apiKey}" ${mcpEndpoint.value}`
})

const cliCommandDisplay = computed(() => {
  const keyDisplay = showApiKey.value
    ? (apiKeyData.value?.data?.apiKey ?? 'your_api_key_here')
    : maskedApiKey.value
  return `claude mcp add ${mcpServerName.value} --transport http --header "X-API-Key: ${keyDisplay}" ${mcpEndpoint.value}`
})

// Standard mcpServers JSON config (Cursor, Windsurf)
const mcpConfigJson = computed(() => {
  const apiKey = apiKeyData.value?.data?.apiKey ?? 'your_api_key_here'
  return {
    mcpServers: {
      [mcpServerName.value]: {
        url: mcpEndpoint.value,
        headers: { 'X-API-Key': apiKey }
      }
    }
  }
})

const mcpConfigText = computed(() => JSON.stringify(mcpConfigJson.value, null, 2))

const mcpConfigDisplayJson = computed(() => {
  const keyDisplay = showApiKey.value
    ? (apiKeyData.value?.data?.apiKey ?? 'your_api_key_here')
    : maskedApiKey.value
  return {
    mcpServers: {
      [mcpServerName.value]: {
        url: mcpEndpoint.value,
        headers: { 'X-API-Key': keyDisplay }
      }
    }
  }
})

const mcpConfigDisplayText = computed(() => JSON.stringify(mcpConfigDisplayJson.value, null, 2))

// VS Code uses a slightly different shape
const vscodeConfigJson = computed(() => {
  const apiKey = apiKeyData.value?.data?.apiKey ?? 'your_api_key_here'
  return {
    servers: {
      [mcpServerName.value]: {
        type: 'http',
        url: mcpEndpoint.value,
        headers: { 'X-API-Key': apiKey }
      }
    }
  }
})

const vscodeConfigText = computed(() => JSON.stringify(vscodeConfigJson.value, null, 2))

const vscodeConfigDisplayJson = computed(() => {
  const keyDisplay = showApiKey.value
    ? (apiKeyData.value?.data?.apiKey ?? 'your_api_key_here')
    : maskedApiKey.value
  return {
    servers: {
      [mcpServerName.value]: {
        type: 'http',
        url: mcpEndpoint.value,
        headers: { 'X-API-Key': keyDisplay }
      }
    }
  }
})

const vscodeConfigDisplayText = computed(() =>
  JSON.stringify(vscodeConfigDisplayJson.value, null, 2)
)

// Codex TOML config
const codexConfigText = computed(() => {
  const apiKey = apiKeyData.value?.data?.apiKey ?? 'your_api_key_here'
  return `[mcp_servers.${mcpServerName.value}]\ncommand = "${mcpEndpoint.value}"\n\n[mcp_servers.${mcpServerName.value}.http_headers]\nX-API-Key = "${apiKey}"`
})

const codexConfigDisplayText = computed(() => {
  const keyDisplay = showApiKey.value
    ? (apiKeyData.value?.data?.apiKey ?? 'your_api_key_here')
    : maskedApiKey.value
  return `[mcp_servers.${mcpServerName.value}]\ncommand = "${mcpEndpoint.value}"\n\n[mcp_servers.${mcpServerName.value}.http_headers]\nX-API-Key = "${keyDisplay}"`
})

// Status tracking
const stripeConnected = computed(() => settingsData.value?.data?.stripeEnabled === true)
</script>
