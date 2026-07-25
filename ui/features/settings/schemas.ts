import { z } from "zod"

export const accountSettingsSchema = z.object({
  currency: z.string().length(3, "Use a 3-letter currency code"),
  stripeMode: z.enum(["NONE", "PAYMENT_PASS_THROUGH", "FULL_SYNC", "STRIPE_INTEGRATION", "STRIPE_DRIVEN"]),
  stripeCheckoutSuccessUrl: z.union([z.url("Enter a valid URL"), z.literal("")]),
  stripeCheckoutCancelUrl: z.union([z.url("Enter a valid URL"), z.literal("")]),
})

export type AccountSettingsInput = z.infer<typeof accountSettingsSchema>
