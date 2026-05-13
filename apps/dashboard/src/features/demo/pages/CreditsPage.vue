<template>
  <div class="p-6">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-semibold text-foreground">Credit Models</h1>
        <p class="text-muted-foreground mt-1">Define credit denominations for usage-based billing</p>
      </div>
      <Button>
        <Plus class="w-4 h-4 mr-2" />
        Create Credit Model
      </Button>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <div
        v-for="model in creditModels"
        :key="model.id"
        class="bg-card rounded-lg border p-5 hover:border-primary/30 transition-colors cursor-pointer"
      >
        <div class="flex items-start justify-between">
          <div class="flex-1 min-w-0">
            <h3 class="font-medium text-foreground truncate">{{ model.name }}</h3>
            <p class="text-sm text-muted-foreground mt-1">{{ model.denomination }}</p>
            <p class="text-xs text-muted-foreground mt-2 line-clamp-2">
              {{ model.description }}
            </p>
          </div>
          <DropdownMenu>
            <DropdownMenuTrigger as-child>
              <Button variant="ghost" size="icon" class="h-8 w-8 shrink-0" @click.stop>
                <MoreHorizontal class="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem @click.stop>
                <Eye class="mr-2 h-4 w-4" />
                View Details
              </DropdownMenuItem>
              <DropdownMenuItem
                class="text-destructive focus:text-destructive"
                @click.stop
              >
                <Trash2 class="mr-2 h-4 w-4" />
                Delete
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
        <div class="flex items-center gap-3 mt-4 text-xs text-muted-foreground">
          <span>Created {{ model.createdAt }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu'
import { Plus, MoreHorizontal, Eye, Trash2 } from 'lucide-vue-next'

const creditModels = [
  {
    id: '1',
    name: 'API Credits',
    denomination: '1 credit = 1 API call',
    description: 'Standard credits for API usage across all endpoints',
    createdAt: 'Jan 14, 2026',
  },
  {
    id: '2',
    name: 'Enrichment Credits',
    denomination: '1 credit = 1 enrichment',
    description: 'Credits consumed when enriching contact or company data',
    createdAt: 'Feb 1, 2026',
  },
  {
    id: '3',
    name: 'Email Send Credits',
    denomination: '1 credit = 1 email sent',
    description: 'Credits consumed per email sent through sequences',
    createdAt: 'Feb 14, 2026',
  },
]
</script>
