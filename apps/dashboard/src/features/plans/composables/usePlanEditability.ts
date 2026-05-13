import { computed, type Ref } from 'vue'
import type { PlanStatus } from '../types'

export function usePlanEditability(planStatus: Ref<PlanStatus>) {
  const isFullyEditable = computed(() => planStatus.value === 'draft')
  const isReadOnly = computed(() => planStatus.value === 'archived')

  const canEditName = computed(() => planStatus.value === 'draft' || planStatus.value === 'active')
  const canEditDescription = computed(() => planStatus.value === 'draft' || planStatus.value === 'active')
  const canEditKey = computed(() => planStatus.value === 'draft')
  const canEditInterval = computed(() => planStatus.value === 'draft')
  const canEditBasePrice = computed(() => planStatus.value === 'draft')
  const canEditFeatures = computed(() => planStatus.value === 'draft')
  const canAddFeatures = computed(() => planStatus.value === 'draft')
  const canRemoveFeatures = computed(() => planStatus.value === 'draft')

  const canActivate = computed(() => planStatus.value === 'draft')
  const canArchive = computed(() => planStatus.value === 'active')
  const canRestore = computed(() => planStatus.value === 'archived')
  const canDuplicate = computed(() => planStatus.value === 'draft' || planStatus.value === 'active' || planStatus.value === 'archived')
  const canDelete = computed(() => planStatus.value === 'draft')

  return {
    isFullyEditable,
    isReadOnly,
    canEditName,
    canEditDescription,
    canEditKey,
    canEditInterval,
    canEditBasePrice,
    canEditFeatures,
    canAddFeatures,
    canRemoveFeatures,
    canActivate,
    canArchive,
    canRestore,
    canDuplicate,
    canDelete
  }
}
