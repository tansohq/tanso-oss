import { z } from 'zod'

export const loginSchema = z.object({
  username: z.string().min(1, 'Please enter your email'),
  password: z.string().min(1, 'Please enter your password')
})

export const loginResponseSchema = z.object({
  data: z.object({
    token: z.string(),
    type: z.string()
  }),
  success: z.boolean()
})

export const signupSchema = z.object({
  email: z.string().email('Please enter a valid email address'),
  password: z.string().min(8, 'Password must be at least 8 characters')
})
