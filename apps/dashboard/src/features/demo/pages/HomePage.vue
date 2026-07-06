<template>
  <div class="p-6 pb-16 max-w-3xl">
    <div class="mb-6">
      <h1 class="text-2xl font-semibold tracking-tight text-foreground">Getting Started</h1>
      <p class="text-sm text-muted-foreground mt-1">Connect Tanso to your AI tools in under a minute.</p>
    </div>

    <div class="space-y-6">
      <!-- Step 1: Connect MCP -->
      <Card class="p-6">
        <div class="flex items-start gap-4">
          <div class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full border-2 border-foreground/20 text-muted-foreground mt-0.5">
            <span class="text-xs font-semibold">1</span>
          </div>
          <div class="min-w-0 flex-1 space-y-4">
            <div>
              <div class="flex items-center gap-2 mb-1">
                <h3 class="text-base font-medium">Connect your MCP server</h3>
                <Badge variant="outline" class="text-amber-600 border-amber-200 bg-amber-50">Sandbox</Badge>
              </div>
              <p class="text-sm text-muted-foreground">Pick your tool. Your API key is pre-filled.</p>
            </div>

            <div class="space-y-4">
              <!-- Tool selector -->
              <div class="flex gap-4 sm:gap-6 border-b overflow-x-auto">
                <button
                  v-for="tool in tools"
                  :key="tool.id"
                  class="pb-2.5 text-sm font-medium transition-colors relative whitespace-nowrap"
                  :class="selectedTool === tool.id ? 'text-foreground' : 'text-muted-foreground hover:text-foreground'"
                  @click="selectedTool = tool.id"
                >
                  {{ tool.label }}
                  <span v-if="selectedTool === tool.id" class="absolute bottom-0 left-0 right-0 h-[2.5px] bg-foreground rounded-full" />
                </button>
              </div>

              <!-- Claude Code -->
              <div v-if="selectedTool === 'claude-code'" class="space-y-2">
                <p class="text-xs text-muted-foreground">Run this in your terminal:</p>
                <div class="bg-zinc-950 text-zinc-100 px-3 py-2 rounded-md font-mono text-xs border border-zinc-800 overflow-x-auto">
                  <span class="text-zinc-500 select-none">$ </span>claude mcp add tanso-sandbox --transport http --header "X-API-Key: sk_test_••••••••demo" http://localhost:8080/mcp
                </div>
              </div>

              <!-- Cursor -->
              <div v-if="selectedTool === 'cursor'" class="space-y-2">
                <p class="text-xs text-muted-foreground">Add this to <code class="bg-muted px-1 py-0.5 rounded text-[11px]">~/.cursor/mcp.json</code></p>
                <pre class="bg-zinc-950 text-zinc-100 rounded-md border border-zinc-800 p-3 text-xs font-mono overflow-x-auto leading-relaxed"><code>{{ mcpConfigText }}</code></pre>
              </div>

              <!-- VS Code -->
              <div v-if="selectedTool === 'vscode'" class="space-y-2">
                <p class="text-xs text-muted-foreground">Add this to <code class="bg-muted px-1 py-0.5 rounded text-[11px]">.vscode/mcp.json</code> in your workspace</p>
                <pre class="bg-zinc-950 text-zinc-100 rounded-md border border-zinc-800 p-3 text-xs font-mono overflow-x-auto leading-relaxed"><code>{{ vscodeConfigText }}</code></pre>
              </div>

              <!-- Windsurf -->
              <div v-if="selectedTool === 'windsurf'" class="space-y-2">
                <p class="text-xs text-muted-foreground">Add this to <code class="bg-muted px-1 py-0.5 rounded text-[11px]">~/.codeium/windsurf/mcp_config.json</code></p>
                <pre class="bg-zinc-950 text-zinc-100 rounded-md border border-zinc-800 p-3 text-xs font-mono overflow-x-auto leading-relaxed"><code>{{ mcpConfigText }}</code></pre>
              </div>

              <!-- Codex -->
              <div v-if="selectedTool === 'codex'" class="space-y-2">
                <p class="text-xs text-muted-foreground">Add this to <code class="bg-muted px-1 py-0.5 rounded text-[11px]">~/.codex/config.toml</code></p>
                <pre class="bg-zinc-950 text-zinc-100 rounded-md border border-zinc-800 p-3 text-xs font-mono overflow-x-auto leading-relaxed"><code>{{ codexConfigText }}</code></pre>
              </div>
            </div>
          </div>
        </div>
      </Card>

      <!-- Step 2: Paste the context file -->
      <Card class="p-6">
        <div class="flex items-start gap-4">
          <div class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full border-2 border-foreground/20 text-muted-foreground mt-0.5">
            <span class="text-xs font-semibold">2</span>
          </div>
          <div class="min-w-0 flex-1 space-y-4">
            <div>
              <h3 class="text-base font-medium mb-1">Paste the context file</h3>
              <p class="text-sm text-muted-foreground">
                Paste this URL into your first conversation so your AI knows how to use Tanso's MCP tools.
              </p>
            </div>
            <div class="flex items-center gap-2">
              <div class="flex-1 bg-muted px-3 py-2 rounded-md font-mono text-xs border truncate">
                /llms-mcp.txt
              </div>
            </div>
          </div>
        </div>
      </Card>

      <!-- Step 3: Connect Stripe -->
      <Card class="p-6">
        <div class="flex items-start gap-4">
          <div class="flex h-7 w-7 shrink-0 items-center justify-center rounded-full border-2 border-foreground/20 text-muted-foreground mt-0.5">
            <span class="text-xs font-semibold">3</span>
          </div>
          <div class="min-w-0 flex-1 space-y-4">
            <div>
              <div class="flex items-center gap-2 mb-1">
                <h3 class="text-base font-medium">Connect Stripe</h3>
                <Badge variant="outline" class="text-muted-foreground">Optional</Badge>
              </div>
              <p class="text-sm text-muted-foreground">
                Connect Stripe before creating plans in Tanso. Your products, customers, and subscriptions will sync automatically — no need to recreate them.
              </p>
            </div>
            <Button variant="outline" size="sm" as-child>
              <router-link to="/signup">
                Go to Integrations
              </router-link>
            </Button>
          </div>
        </div>
      </Card>

      <!-- Try asking -->
      <div class="pt-4">
        <p class="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-3">Try asking your AI</p>
        <div class="space-y-2">
          <div class="rounded-lg border bg-muted/30 p-3">
            <p class="text-xs text-muted-foreground italic">"Set up two plans: Starter at $29/mo and Pro at $99/mo with usage-based API access."</p>
          </div>
          <div class="rounded-lg border bg-muted/30 p-3">
            <p class="text-xs text-muted-foreground italic">"Show me all my customers and their current subscription status."</p>
          </div>
          <div class="rounded-lg border bg-muted/30 p-3">
            <p class="text-xs text-muted-foreground italic">"Which customers are close to hitting their usage limits this month?"</p>
          </div>
        </div>
      </div>

      <!-- Resources -->
      <div class="pt-2">
        <p class="text-xs font-medium text-muted-foreground uppercase tracking-wide mb-3">Resources</p>
        <div class="flex flex-wrap gap-x-6 gap-y-2">
          <a href="https://github.com/tansohq/tanso-oss/tree/main/docs" target="_blank" rel="noopener noreferrer" class="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors py-1">
            <BookOpen class="w-3.5 h-3.5" /> REST API Docs <ArrowUpRight class="w-3 h-3" />
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
import { BookOpen, ArrowUpRight } from 'lucide-vue-next'

type ToolId = 'claude-code' | 'cursor' | 'vscode' | 'windsurf' | 'codex'

const tools: { id: ToolId; label: string }[] = [
  { id: 'claude-code', label: 'Claude Code' },
  { id: 'cursor', label: 'Cursor' },
  { id: 'vscode', label: 'VS Code' },
  { id: 'windsurf', label: 'Windsurf' },
  { id: 'codex', label: 'Codex' },
]

const selectedTool = ref<ToolId>('claude-code')

const mcpConfigText = computed(() => JSON.stringify({
  mcpServers: {
    'tanso-sandbox': {
      url: 'http://localhost:8080/mcp',
      headers: { 'X-API-Key': 'sk_test_••••••••demo' },
    },
  },
}, null, 2))

const vscodeConfigText = computed(() => JSON.stringify({
  servers: {
    'tanso-sandbox': {
      type: 'http',
      url: 'http://localhost:8080/mcp',
      headers: { 'X-API-Key': 'sk_test_••••••••demo' },
    },
  },
}, null, 2))

const codexConfigText = computed(() =>
  `[mcp_servers.tanso-sandbox]\ncommand = "http://localhost:8080/mcp"\n\n[mcp_servers.tanso-sandbox.http_headers]\nX-API-Key = "sk_test_••••••••demo"`
)
</script>
