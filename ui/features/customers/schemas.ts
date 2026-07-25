import { z } from "zod"

export const customerSchema = z.object({
  customerReferenceId: z.string().optional(),
  firstName: z.string().optional(),
  lastName: z.string().optional(),
  email: z.email("Enter a valid email"),
  phoneNumber: z.string().optional(),
})

export type CustomerInput = z.infer<typeof customerSchema>
