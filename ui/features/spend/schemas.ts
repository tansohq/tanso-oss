import { z } from "zod"

export const vendorConnectionSchema = z.object({
  provider: z.enum(["ANTHROPIC", "OPENAI"]),
  label: z.string().min(1, "Label is required").max(100),
  adminKey: z.string().min(1, "Admin key is required"),
})

export type VendorConnectionInput = z.infer<typeof vendorConnectionSchema>
