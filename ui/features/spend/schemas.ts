import { z } from "zod"

export const vendorConnectionSchema = z
  .object({
    provider: z.enum(["ANTHROPIC", "OPENAI", "CURSOR", "COPILOT"]),
    label: z.string().min(1, "Label is required").max(100),
    adminKey: z.string().min(1, "Admin key is required"),
    scope: z.string().max(200).optional(),
  })
  .refine((v) => v.provider !== "COPILOT" || !!v.scope?.trim(), {
    message: "Copilot needs the GitHub organization",
    path: ["scope"],
  })

export type VendorConnectionInput = z.infer<typeof vendorConnectionSchema>
