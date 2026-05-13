import { api } from '@/lib/api'
import type {
  CustomersResponse,
  CreateCustomer,
  UpdateCustomer,
  CustomerDetailResponse
} from './types'

export async function fetchCustomers(): Promise<CustomersResponse> {
  return api.get<CustomersResponse>('/api/v1/monetization/customers')
}

export async function createCustomer(data: CreateCustomer): Promise<void> {
  return api.post<void>('/api/v1/monetization/customers', data)
}

export async function getCustomer(customerId: string): Promise<CustomerDetailResponse> {
  return api.get<CustomerDetailResponse>(`/api/v1/monetization/customers/${customerId}`)
}

export async function updateCustomer(customerId: string, data: UpdateCustomer): Promise<void> {
  return api.patch<void>(`/api/v1/monetization/customers/${customerId}`, data)
}
