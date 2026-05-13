import { api } from '@/lib/api'
import { getPlanFeatureRule, createPlanFeatureRule } from '@/features/plan-features/api'
import type {
  PlansResponse,
  CreatePlan,
  PlanDetailResponse,
  UpdatePlan,
  PlanFeatureLinkedResponse,
  PlanRevenueResponse
} from './types'

export async function fetchPlans(): Promise<PlansResponse> {
  return api.get<PlansResponse>('/api/v1/monetization/plans')
}

export async function getPlan(uuid: string): Promise<PlanDetailResponse> {
  return api.get<PlanDetailResponse>(`/api/v1/monetization/plans/${uuid}`)
}

export async function createPlan(data: CreatePlan): Promise<PlanDetailResponse> {
  return api.post<PlanDetailResponse>('/api/v1/monetization/plans', data)
}

export async function updatePlan(uuid: string, data: UpdatePlan): Promise<PlanDetailResponse> {
  return api.patch<PlanDetailResponse>(`/api/v1/monetization/plans/${uuid}`, data)
}

export async function activatePlan(uuid: string): Promise<PlanDetailResponse> {
  return api.patch<PlanDetailResponse>(`/api/v1/monetization/plans/${uuid}`, { status: 'ACTIVE' })
}

export async function archivePlan(uuid: string): Promise<PlanDetailResponse> {
  return api.patch<PlanDetailResponse>(`/api/v1/monetization/plans/${uuid}`, { status: 'ARCHIVED' })
}

export async function restorePlan(uuid: string): Promise<PlanDetailResponse> {
  return api.patch<PlanDetailResponse>(`/api/v1/monetization/plans/${uuid}`, { status: 'ACTIVE' })
}

export async function deletePlan(planId: string): Promise<void> {
  return api.delete<void>(`/api/v1/monetization/plans/${planId}`)
}

export async function deletePlansBulk(ids: string[]): Promise<void> {
  return api.delete<void>('/api/v1/monetization/plans', { ids })
}

export async function getPlanFeatures(planUuid: string): Promise<PlanFeatureLinkedResponse> {
  return api.get<PlanFeatureLinkedResponse>(`/api/v1/monetization/plans/${planUuid}/features`)
}

export async function fetchPlanRevenue(
  planId: string,
  periodStart: string,
  periodEnd: string,
  page = 0,
  size = 20,
  subscriptionId?: string
): Promise<PlanRevenueResponse> {
  const params = new URLSearchParams({
    periodStart,
    periodEnd,
    page: page.toString(),
    size: size.toString()
  })
  if (subscriptionId) {
    params.set('subscriptionId', subscriptionId)
  }
  return api.get<PlanRevenueResponse>(`/api/v1/monetization/plans/${planId}/revenue?${params}`)
}

export interface DuplicatePlanResult {
  planId: string
  planName: string
  failedFeatures: string[]
}

export async function duplicatePlan(sourcePlanId: string): Promise<DuplicatePlanResult> {
  // 1. Get source plan with features
  const sourceResponse = await getPlanFeatures(sourcePlanId)
  const sourcePlan = sourceResponse?.data?.plan
  if (!sourcePlan) throw new Error('Source plan not found')

  // 2. Build copy name — strip existing "(Copy)" or "(Copy N)" suffix before appending
  const baseName = sourcePlan.name.replace(/\s*\(Copy(?:\s*\d+)?\)$/, '')
  const newName = `${baseName} (Copy)`

  // 3. Generate short key suffix (4-char alphanumeric)
  const suffix = Math.random().toString(36).substring(2, 6)
  const baseKey = sourcePlan.key.replace(/_copy_[a-z0-9]+$/, '')
  const newKey = `${baseKey}_copy_${suffix}`

  // 4. Create new draft plan with all required fields
  const newPlanResponse = await createPlan({
    key: newKey,
    name: newName,
    description: sourcePlan.description || newName,
    intervalMonths: sourcePlan.intervalMonths || '1',
    status: 'draft',
    priceAmount: sourcePlan.priceAmount || 0,
    billingTiming: sourcePlan.billingTiming || 'IN_ADVANCE',
    metadata: sourcePlan.metadata ?? undefined
  } as CreatePlan)
  // API may return id or uuid depending on backend version
  const responseData = newPlanResponse?.data as Record<string, unknown> | undefined
  const newPlanId = (responseData?.uuid || responseData?.id) as string | undefined
  if (!newPlanId) throw new Error('Failed to create duplicate plan')

  // 5. Copy features with their pricing rules, tracking failures
  const sourceFeatures = sourceResponse?.data?.features || []
  const failedFeatures: string[] = []

  for (const feature of sourceFeatures) {
    try {
      const ruleResponse = await getPlanFeatureRule(sourcePlanId, feature.id)
      const rule = ruleResponse?.data
      await createPlanFeatureRule({
        planId: newPlanId,
        featureId: feature.id,
        type: rule?.type || 'BASE',
        value: rule?.value || {},
        isEnabled: feature.isEnabled
      })
    } catch {
      failedFeatures.push(feature.name || feature.id)
      try {
        await createPlanFeatureRule({
          planId: newPlanId,
          featureId: feature.id,
          type: 'BASE',
          value: {},
          isEnabled: feature.isEnabled
        })
      } catch {
        // Feature could not be linked at all
      }
    }
  }

  return { planId: newPlanId, planName: newName, failedFeatures }
}
