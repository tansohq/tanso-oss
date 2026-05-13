import { z } from 'zod'

// CustomerElement - used in list responses
export const customerElementSchema = z.object({
  id: z.string(),
  referenceId: z.string(),
  firstName: z.string(),
  lastName: z.string(),
  email: z.string(),
  createdAt: z.string().optional()
})

// CustomerDto - used in detail responses
export const customerDetailSchema = z.object({
  id: z.string(),
  customerReferenceId: z.string(),
  firstName: z.string(),
  lastName: z.string(),
  email: z.string(),
  phoneNumber: z.string().nullable(),
  createdAt: z.string(),
  modifiedAt: z.string()
})

export const customersDataSchema = z.object({
  customers: z.array(customerElementSchema)
})

export const customersResponseSchema = z
  .object({
    data: customersDataSchema.optional(),
    success: z.boolean().optional()
  })
  .passthrough()

export const customerDetailResponseSchema = z
  .object({
    data: customerDetailSchema.optional(),
    success: z.boolean().optional()
  })
  .passthrough()

export const createCustomerSchema = z.object({
  customerReferenceId: z.string().min(1, 'Please enter a reference ID'),
  firstName: z.string().min(1, 'Please enter a first name'),
  lastName: z.string().min(1, 'Please enter a last name'),
  email: z.string().email('Please enter a valid email address'),
  phoneNumber: z.string().optional(),
  address: z.string().optional()
})

export const updateCustomerSchema = z.object({
  id: z.string().optional(),
  firstName: z.string().min(1, 'Please enter a first name'),
  lastName: z.string().min(1, 'Please enter a last name'),
  email: z.string().email('Please enter a valid email address'),
  phoneNumber: z.string().optional(),
  address: z.string().optional()
})
