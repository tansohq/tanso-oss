<template>
  <Dialog :open="open" @update:open="$emit('update:open', $event)">
    <DialogContent class="max-w-lg">
      <DialogHeader>
        <DialogTitle>Combine Segments</DialogTitle>
        <DialogDescription>
          Combine small segments to reach experiment-ready sample size.
        </DialogDescription>
      </DialogHeader>

      <div class="space-y-4 mt-4">
        <!-- Source Segment -->
        <div class="p-3 border rounded-lg bg-muted/50">
          <div class="flex items-center justify-between">
            <div>
              <div class="font-medium">{{ sourceSegment?.name }}</div>
              <div class="text-sm text-muted-foreground">
                {{ sourceSegment?.customerCount }} accounts
              </div>
            </div>
            <Badge class="bg-red-100 text-red-700 border-0">Too Small</Badge>
          </div>
        </div>

        <!-- Plus Sign -->
        <div class="flex justify-center">
          <div class="flex items-center justify-center w-8 h-8 rounded-full bg-muted">
            <Plus class="h-4 w-4 text-muted-foreground" />
          </div>
        </div>

        <!-- Target Segment Selector -->
        <div>
          <Label>Combine with</Label>
          <Select v-model="selectedTargetId" class="mt-1.5">
            <SelectTrigger>
              <SelectValue placeholder="Select a segment..." />
            </SelectTrigger>
            <SelectContent>
              <SelectItem v-for="segment in eligibleSegments" :key="segment.id" :value="segment.id">
                <div class="flex items-center justify-between w-full gap-4">
                  <span>{{ segment.name }}</span>
                  <span class="text-muted-foreground text-sm"
                    >{{ segment.customerCount }} accounts</span
                  >
                </div>
              </SelectItem>
            </SelectContent>
          </Select>
        </div>

        <!-- Combined Preview -->
        <div v-if="targetSegment" class="p-4 border rounded-lg bg-background">
          <div class="text-center space-y-2">
            <div class="text-3xl font-bold tabular-nums">{{ combinedCount }}</div>
            <div class="text-sm text-muted-foreground">combined accounts</div>
            <Badge :class="combinedReadinessClasses" class="border-0">
              {{ combinedReadiness.label }}
            </Badge>
            <p class="text-xs text-muted-foreground mt-2">{{ combinedReadiness.description }}</p>
          </div>
        </div>
      </div>

      <DialogFooter class="mt-6">
        <Button variant="outline" @click="$emit('update:open', false)"> Cancel </Button>
        <Button @click="saveCombinedSegment" :disabled="!selectedTargetId">
          Save Combined Segment
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@/components/ui/select'
import { Plus } from 'lucide-vue-next'
import { useToast } from '@/components/ui/toast'
import type { Segment } from '../../types'
import { getExperimentReadiness } from '../../types'
import { useDemoState } from '../../composables/useDemoState'

const props = defineProps<{
  open: boolean
  sourceSegment: Segment | null
}>()

defineEmits<{
  'update:open': [value: boolean]
}>()

const { toast } = useToast()
const { segmentsData } = useDemoState()

const selectedTargetId = ref<string>('')

// Get segments that can be combined with (excluding the source)
const eligibleSegments = computed(() => {
  if (!props.sourceSegment) return []
  return segmentsData.value.filter((c) => c.id !== props.sourceSegment?.id && c.status === 'active')
})

// Get the selected target segment
const targetSegment = computed(() => {
  if (!selectedTargetId.value) return null
  return segmentsData.value.find((c) => c.id === selectedTargetId.value) ?? null
})

// Calculate combined count
const combinedCount = computed(() => {
  if (!props.sourceSegment || !targetSegment.value) return 0
  return props.sourceSegment.customerCount + targetSegment.value.customerCount
})

// Get combined readiness
const combinedReadiness = computed(() => {
  return getExperimentReadiness(combinedCount.value)
})

// Get combined readiness badge classes
const combinedReadinessClasses = computed(() => {
  switch (combinedReadiness.value.color) {
    case 'red':
      return 'bg-red-100 text-red-700'
    case 'yellow':
      return 'bg-amber-100 text-amber-700'
    case 'green':
      return 'bg-green-100 text-green-700'
    default:
      return ''
  }
})

function saveCombinedSegment() {
  toast({
    title: 'Coming soon',
    description: 'Segment combination will be available in the next release.'
  })
}
</script>
