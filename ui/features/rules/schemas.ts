import { z } from "zod"

export const ruleSchema = z.object({
  featureId: z.string().min(1, "Pick a feature"),
  isEnabled: z.boolean(),
  value: z.string().superRefine((text, ctx) => {
    try {
      const parsed = JSON.parse(text)
      if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
        ctx.addIssue({ code: "custom", message: "Value must be a JSON object" })
      }
    } catch {
      ctx.addIssue({ code: "custom", message: "Value must be valid JSON" })
    }
  }),
  creditModelId: z.string().optional(),
})

export type RuleInput = z.infer<typeof ruleSchema>
