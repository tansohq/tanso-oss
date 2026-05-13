import { z } from 'zod'
import { loginSchema, loginResponseSchema, signupSchema } from './schemas'

export type LoginCredentials = z.infer<typeof loginSchema>
export type LoginResponse = z.infer<typeof loginResponseSchema>

export type SignupFormData = z.infer<typeof signupSchema>

export interface SignupRequest {
  customerDetails: {
    email: string
  }
  password: string
}

export interface OnboardingPlan {
  id: string
  key: string
  name: string
  description: string
  priceAmount: number
  intervalMonths: string
  billingTiming: string
  metadata?: Record<string, unknown> | null
}

export interface OnboardingPlansResponse {
  success: boolean
  data: OnboardingPlan[]
}

export interface DecodedToken {
  sub: string
  account_id: string
  email: string | null
  role: string
  scope: string
  iat: number
  exp: number
}
