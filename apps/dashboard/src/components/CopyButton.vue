<script setup lang="ts">
import { ref } from 'vue'
import { Button } from '@/components/ui/button'
import { Copy, Check } from 'lucide-vue-next'
import { toast } from '@/components/ui/toast/use-toast'

const props = defineProps<{
  value: string
  label?: string
}>()

const copied = ref(false)

async function copyToClipboard() {
  try {
    await navigator.clipboard.writeText(props.value)
    copied.value = true
    toast({
      title: 'Copied',
      description: props.label ? `${props.label} copied` : 'Copied to clipboard'
    })
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch {
    toast({ title: 'Error', description: 'Failed to copy to clipboard', variant: 'destructive' })
  }
}
</script>

<template>
  <Button variant="ghost" size="icon" class="h-6 w-6" @click.stop="copyToClipboard">
    <Check v-if="copied" class="h-3.5 w-3.5 text-green-500" />
    <Copy v-else class="h-3.5 w-3.5 text-muted-foreground" />
  </Button>
</template>
