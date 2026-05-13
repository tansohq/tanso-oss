import { z } from 'zod'
import {
  customerElementSchema,
  customersResponseSchema,
  customersDataSchema,
  customerDetailSchema,
  customerDetailResponseSchema,
  createCustomerSchema,
  updateCustomerSchema
} from './schemas'

export type CustomerElement = z.infer<typeof customerElementSchema>
export type CustomersData = z.infer<typeof customersDataSchema>
export type CustomersResponse = z.infer<typeof customersResponseSchema>
export type CustomerDetail = z.infer<typeof customerDetailSchema>
export type CustomerDetailResponse = z.infer<typeof customerDetailResponseSchema>
export type CreateCustomer = z.infer<typeof createCustomerSchema>
export type UpdateCustomer = z.infer<typeof updateCustomerSchema>
