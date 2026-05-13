<template>
  <Dialog :open="visible" @update:open="$emit('update:visible', $event)">
    <DialogContent class="max-w-lg">
      <DialogHeader>
        <DialogTitle>Integrate with AI Tools</DialogTitle>
        <DialogDescription>
          Give any AI coding tool full context on the Tanso API by providing this URL.
        </DialogDescription>
      </DialogHeader>

      <div class="space-y-4">
        <div>
          <label class="text-sm font-medium text-foreground mb-1.5 block">Context URL</label>
          <div class="flex items-center gap-2">
            <code class="flex-1 bg-muted px-3 py-2 rounded-md text-sm font-mono truncate">{{ contextUrl }}</code>
            <Button variant="outline" size="sm" @click="copyUrl">
              <Check v-if="copied" class="h-3.5 w-3.5 mr-1.5 text-green-500" />
              <Copy v-else class="h-3.5 w-3.5 mr-1.5" />
              {{ copied ? 'Copied' : 'Copy' }}
            </Button>
          </div>
        </div>

        <div class="rounded-lg border p-4 space-y-3">
          <p class="text-sm font-medium">How to use</p>
          <ol class="text-sm text-muted-foreground space-y-2 list-decimal list-inside">
            <li>Copy the URL above</li>
            <li>Paste it into Claude, Cursor, ChatGPT, or any AI coding tool</li>
            <li>Ask it to help you integrate Tanso billing into your app</li>
          </ol>
          <p class="text-xs text-muted-foreground pt-1">The URL contains the full Tanso API reference, endpoint docs, and integration patterns.</p>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Copy, Check } from 'lucide-vue-next'
import { toast } from '@/components/ui/toast/use-toast'

defineProps<{
  visible: boolean
}>()

defineEmits<{
  'update:visible': [value: boolean]
}>()

const contextUrl = '/llms-mcp.txt'
const copied = ref(false)

async function copyUrl() {
  try {
    await navigator.clipboard.writeText(contextUrl)
    copied.value = true
    toast({
      title: 'Copied',
      description: 'Context URL copied to clipboard'
    })
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch {
    toast({ title: 'Error', description: 'Failed to copy', variant: 'destructive' })
  }
}
</script>
