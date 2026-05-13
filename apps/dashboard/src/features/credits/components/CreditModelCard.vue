<template>
  <Card
    class="p-5 hover:bg-muted/50 transition-colors cursor-pointer"
    @click="$emit('click')"
  >
    <div class="flex items-start justify-between">
      <div class="flex-1 min-w-0">
        <h3 class="font-medium text-foreground truncate">{{ model.name }}</h3>
        <p class="text-sm text-muted-foreground mt-1">{{ model.denomination }}</p>
        <p v-if="model.description" class="text-sm text-muted-foreground mt-2 line-clamp-2">
          {{ model.description }}
        </p>
      </div>
      <DropdownMenu>
        <DropdownMenuTrigger as-child @click.stop>
          <Button variant="ghost" size="icon" class="h-8 w-8 shrink-0">
            <MoreHorizontal class="h-4 w-4" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem @click.stop="$emit('click')">
            <Eye class="mr-2 h-4 w-4" />
            View Details
          </DropdownMenuItem>
          <DropdownMenuItem
            class="text-destructive focus:text-destructive"
            @click.stop="$emit('delete')"
          >
            <Trash2 class="mr-2 h-4 w-4" />
            Delete
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
    <div class="flex items-center gap-3 mt-4 text-sm text-muted-foreground">
      <span>Created {{ formatDate(model.createdAt) }}</span>
    </div>
  </Card>
</template>

<script setup lang="ts">
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu'
import { MoreHorizontal, Eye, Trash2 } from 'lucide-vue-next'
import { formatDate } from '@/lib/formatters'
import type { CreditModel } from '../types'

defineProps<{
  model: CreditModel
}>()

defineEmits<{
  click: []
  delete: []
}>()
</script>
