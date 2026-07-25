import { z } from "zod"

export const featureSchema = z.object({
  name: z.string().min(1, "Name is required"),
  key: z.string().min(1, "Key is required"),
  description: z.string().optional(),
  isEnabled: z.boolean(),
})

export type FeatureInput = z.infer<typeof featureSchema>
