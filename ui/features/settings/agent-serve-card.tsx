"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { Controller, useForm } from "react-hook-form"
import { z } from "zod"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import { toast } from "@/components/ui/toast"
import { useUpdateAgentServeSettings } from "@/features/settings/mutations"
import { useAccountSettings } from "@/features/settings/queries"
import { usePlans } from "@/features/plans/queries"

const agentServeSchema = z.object({
  slug: z
    .union([z.string().regex(/^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])?$/, "Lowercase letters, digits, hyphens"), z.literal("")]),
  publicCatalogEnabled: z.boolean(),
  agentSignupEnabled: z.boolean(),
  agentSignupDefaultPlanId: z.string(),
  agentSignupHourlyCap: z.string().regex(/^[1-9]\d*$/, "At least 1"),
  agentMaxTopupAmount: z.union([z.string().regex(/^\d+(\.\d{1,2})?$/, "Enter an amount"), z.literal("")]),
})

type AgentServeInput = z.infer<typeof agentServeSchema>

export function AgentServeCard() {
  const settings = useAccountSettings()
  const plans = usePlans()
  const updateSettings = useUpdateAgentServeSettings()

  // Signup requires a FREE, ACTIVE default plan — same rule the server enforces
  const freePlans = (plans.data ?? []).filter(
    (plan) => plan.status === "ACTIVE" && Number(plan.priceAmount ?? 0) === 0,
  )
  const freePlanItems = freePlans.map((plan) => ({
    label: `${plan.name} (${plan.key})`,
    value: plan.id ?? "",
  }))

  const form = useForm<AgentServeInput>({
    resolver: zodResolver(agentServeSchema),
    values: {
      slug: settings.data?.slug ?? "",
      publicCatalogEnabled: settings.data?.publicCatalogEnabled ?? false,
      agentSignupEnabled: settings.data?.agentSignupEnabled ?? false,
      agentSignupDefaultPlanId: settings.data?.agentSignupDefaultPlanId ?? "",
      agentSignupHourlyCap: String(settings.data?.agentSignupHourlyCap ?? 10),
      agentMaxTopupAmount:
        settings.data?.agentMaxTopupAmount != null ? String(settings.data.agentMaxTopupAmount) : "",
    },
  })
  const errors = form.formState.errors

  if (settings.isPending) {
    return <Skeleton className="h-64 w-full" />
  }

  const slug = form.watch("slug")

  return (
    <Card>
      <CardHeader>
        <CardTitle>Agent-serve</CardTitle>
        <CardDescription>
          Let your customers&apos; AI agents discover pricing, sign up, and buy. Everything here
          is off until you turn it on.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form
          onSubmit={form.handleSubmit((input) =>
            updateSettings.mutate(input, {
              onSuccess: () => toast.add({ title: "Agent-serve settings saved" }),
              onError: (error: Error) => toast.add({ title: "Save failed", description: error.message }),
            }),
          )}
        >
          <FieldGroup>
            <div className="grid grid-cols-2 gap-4">
              <Field data-invalid={!!errors.slug || undefined}>
                <FieldLabel htmlFor="slug">Catalog slug</FieldLabel>
                <Input id="slug" placeholder="acme" aria-invalid={!!errors.slug} {...form.register("slug")} />
                {errors.slug ? (
                  <FieldError>{errors.slug.message}</FieldError>
                ) : (
                  <FieldDescription>
                    {slug
                      ? `Public URL: /public/v1/catalog/${slug}/pricing.json`
                      : "Names your account in public catalog URLs."}
                  </FieldDescription>
                )}
              </Field>
              <Field>
                <FieldLabel htmlFor="signup-plan">Agent signup plan</FieldLabel>
                <Controller
                  control={form.control}
                  name="agentSignupDefaultPlanId"
                  render={({ field }) => (
                    <Select
                      items={freePlanItems}
                      value={field.value || null}
                      onValueChange={field.onChange}
                    >
                      <SelectTrigger id="signup-plan">
                        <SelectValue placeholder="Pick a free ACTIVE plan" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {freePlanItems.map((item) => (
                            <SelectItem key={item.value} value={item.value}>
                              {item.label}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  )}
                />
                <FieldDescription>
                  {freePlanItems.length === 0
                    ? "No free ACTIVE plans yet — signup needs one."
                    : "What a signing-up agent gets subscribed to. Free plans only; paid conversion happens after signup."}
                </FieldDescription>
              </Field>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <Field>
                <Controller
                  control={form.control}
                  name="publicCatalogEnabled"
                  render={({ field }) => (
                    <label className="flex items-center gap-2 text-sm">
                      <Checkbox checked={field.value} onCheckedChange={field.onChange} />
                      Publish machine-readable pricing
                    </label>
                  )}
                />
                <FieldDescription>
                  Serves pricing.json publicly — plans, credit weights, and prices. Needs a slug.
                </FieldDescription>
              </Field>
              <Field>
                <Controller
                  control={form.control}
                  name="agentSignupEnabled"
                  render={({ field }) => (
                    <label className="flex items-center gap-2 text-sm">
                      <Checkbox checked={field.value} onCheckedChange={field.onChange} />
                      Allow agent signup
                    </label>
                  )}
                />
                <FieldDescription>
                  One API call creates a customer, subscribes the plan above, and issues a
                  customer-scoped key.
                </FieldDescription>
              </Field>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <Field data-invalid={!!errors.agentSignupHourlyCap || undefined}>
                <FieldLabel htmlFor="signup-cap">Signups per hour</FieldLabel>
                <Input
                  id="signup-cap"
                  type="number"
                  min={1}
                  aria-invalid={!!errors.agentSignupHourlyCap}
                  {...form.register("agentSignupHourlyCap")}
                />
                {errors.agentSignupHourlyCap && (
                  <FieldError>{errors.agentSignupHourlyCap.message}</FieldError>
                )}
              </Field>
              <Field data-invalid={!!errors.agentMaxTopupAmount || undefined}>
                <FieldLabel htmlFor="spend-cap">Agent spend cap</FieldLabel>
                <Input
                  id="spend-cap"
                  type="number"
                  min={0}
                  step="0.01"
                  placeholder="No cap"
                  aria-invalid={!!errors.agentMaxTopupAmount}
                  {...form.register("agentMaxTopupAmount")}
                />
                <FieldDescription>
                  Max money one agent-initiated charge may move. Empty or 0 = no cap.
                </FieldDescription>
              </Field>
            </div>
            <div>
              <Button type="submit" disabled={updateSettings.isPending}>
                {updateSettings.isPending && <Spinner data-icon="inline-start" />}
                Save agent-serve settings
              </Button>
            </div>
          </FieldGroup>
        </form>
      </CardContent>
    </Card>
  )
}
