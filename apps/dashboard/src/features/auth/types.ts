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

export interface DecodedToken {
  sub: string
  account_id: string
  email: string | null
  role: string
  scope: string
  iat: number
  exp: number
}
