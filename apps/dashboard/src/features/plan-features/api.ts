import { api } from '@/lib/api'
import type {
  PlanFeatureLinkedDiffRequest,
  PlanFeatureLinkedDiffApiResponse,
  PlanFeatureRuleResponse
} from './types'

export async function syncPlanFeatures(
  planUuid: string,
  data: PlanFeatureLinkedDiffRequest
): Promise<PlanFeatureLinkedDiffApiResponse> {
  return api.patch<PlanFeatureLinkedDiffApiResponse>(
    `/api/v1/monetization/rules/plan-features/diff/${planUuid}`,
    data
  )
}

export async function getPlanFeatureRule(
  planUuid: string,
  featureUuid: string
): Promise<PlanFeatureRuleResponse> {
  return api.get<PlanFeatureRuleResponse>(
    `/api/v1/monetization/rules/plan-features/${planUuid}/${featureUuid}`
  )
}

export async function createPlanFeatureRule(
  data: { planId: string; featureId: string; type: string; value: Record<string, any>; isEnabled: boolean; creditModelId?: string }
): Promise<PlanFeatureRuleResponse> {
  return api.post<PlanFeatureRuleResponse>(
    '/api/v1/monetization/rules/plan-features',
    data
  )
}

export async function updatePlanFeatureRule(
  data: { planId: string; featureId: string; type: string; value: Record<string, any>; isEnabled: boolean; creditModelId?: string }
): Promise<PlanFeatureRuleResponse> {
  return api.patch<PlanFeatureRuleResponse>(
    '/api/v1/monetization/rules/plan-features',
    data
  )
}

export async function deletePlanFeatureRule(
  planUuid: string,
  featureUuid: string
): Promise<void> {
  return api.delete<void>(
    `/api/v1/monetization/rules/plan-features/${planUuid}/${featureUuid}`
  )
}
