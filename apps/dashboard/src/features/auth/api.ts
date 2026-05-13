import { api } from '@/lib/api'
import type {
  LoginCredentials,
  LoginResponse,
  SignupRequest,
  OnboardingPlansResponse
} from './types'

export async function login(credentials: LoginCredentials): Promise<LoginResponse> {
  return api.post<LoginResponse>('/public/v1/login', credentials)
}

export async function signup(request: SignupRequest): Promise<LoginResponse> {
  return api.post<LoginResponse>('/public/v1/signup', request)
}

export async function fetchOnboardingPlans(): Promise<OnboardingPlansResponse> {
  return api.get<OnboardingPlansResponse>('/public/v1/onboarding/plans')
}
