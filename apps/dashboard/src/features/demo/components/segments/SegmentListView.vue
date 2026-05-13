<template>
  <div class="space-y-6">
    <!-- Header with Create Button -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-4">
        <div class="relative">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input v-model="searchQuery" placeholder="Search segments..." class="pl-9 w-64 lg:w-80" />
        </div>
        <SuggestedSegmentChips />
      </div>
      <Button @click="openSegmentBuilder">
        <Plus class="h-4 w-4 mr-2" />
        Create Segment
      </Button>
    </div>

    <!-- Summary Stats Bar -->
    <div class="flex items-center gap-6 text-sm text-muted-foreground py-2 border-b">
      <div class="flex items-center gap-2">
        <Users class="h-4 w-4 text-primary" />
        <span class="tabular-nums font-medium text-foreground">{{
          segmentSummaryStats.totalSegments
        }}</span>
        <span>segments</span>
      </div>
      <div class="flex items-center gap-2">
        <DollarSign class="h-4 w-4 text-blue-500" />
        <span class="tabular-nums font-medium text-foreground">{{
          segmentSummaryStats.totalCustomers.toLocaleString()
        }}</span>
        <span>customers</span>
      </div>
      <div class="flex items-center gap-2">
        <TrendingUp class="h-4 w-4 text-emerald-500" />
        <span class="tabular-nums font-medium text-foreground"
          >{{ segmentSummaryStats.avgGrossMargin }}%</span
        >
        <span>avg margin</span>
      </div>
      <div v-if="segmentSummaryStats.unprofitableAccounts > 0" class="flex items-center gap-2">
        <AlertTriangle class="h-4 w-4 text-destructive" />
        <span class="tabular-nums font-medium text-destructive">{{
          segmentSummaryStats.unprofitableAccounts
        }}</span>
        <span>unprofitable</span>
      </div>
    </div>

    <!-- Segment Cards -->
    <div
      v-if="filteredSegments.length > 0"
      class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5"
    >
      <SegmentCard
        v-for="segment in filteredSegments"
        :key="segment.id"
        :segment="segment"
        @click="navigateToSegment(segment.id)"
      />
    </div>

    <!-- Empty State -->
    <div v-else class="flex flex-col items-center justify-center py-16 text-muted-foreground">
      <Inbox class="w-12 h-12 mb-4" />
      <p class="text-lg font-medium text-foreground mb-2">No segments found</p>
      <p class="text-sm mb-4">Try adjusting your search</p>
      <Button variant="outline" @click="searchQuery = ''"> Clear Search </Button>
    </div>

    <!-- Combine Segments Dialog -->
    <CombineSegmentsDialog
      :open="showCombineSegments"
      :source-segment="combineSourceSegment"
      @update:open="showCombineSegments = $event"
    />
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Plus, Search, Inbox, Users, DollarSign, TrendingUp, AlertTriangle } from 'lucide-vue-next'
import { useDemoState } from '../../composables/useDemoState'
import SegmentCard from './SegmentCard.vue'
import CombineSegmentsDialog from './CombineSegmentsDialog.vue'
import SuggestedSegmentChips from './SuggestedSegmentChips.vue'

const router = useRouter()

const {
  searchQuery,
  filteredSegments,
  segmentSummaryStats,
  openSegmentBuilder,
  showCombineSegments,
  combineSourceSegment
} = useDemoState()

function navigateToSegment(id: string) {
  router.push(`/demo/segments/${id}`)
}
</script>
