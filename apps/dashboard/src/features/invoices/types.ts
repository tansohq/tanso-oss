import { z } from 'zod'
import {
  invoiceSchema,
  invoicesResponseSchema,
  invoiceItemSchema,
  invoiceDetailSchema,
  invoiceDetailResponseSchema
} from './schemas'

export type Invoice = z.infer<typeof invoiceSchema>
export type InvoicesResponse = z.infer<typeof invoicesResponseSchema>
export type InvoiceItem = z.infer<typeof invoiceItemSchema>
export type InvoiceDetail = z.infer<typeof invoiceDetailSchema>
export type InvoiceDetailResponse = z.infer<typeof invoiceDetailResponseSchema>
