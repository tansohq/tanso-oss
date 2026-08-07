"use client"

import { useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { Copy, Eye, EyeOff } from "lucide-react"
import { Controller, useForm } from "react-hook-form"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
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
import { useUpdateAccountSettings } from "@/features/settings/mutations"
import { AgentServeCard } from "@/features/settings/agent-serve-card"
import { StripeCard } from "@/features/settings/stripe-card"
import { useAccountSettings, useApiKey, useStripeKeys } from "@/features/settings/queries"
import {
  accountSettingsSchema,
  type AccountSettingsInput,
} from "@/features/settings/schemas"

// Once Stripe is connected, "who handles billing" is a choice between exactly
// these two — matching the original setup wizard. FULL_SYNC (a deprecated
// alias for STRIPE_INTEGRATION) and STRIPE_DRIVEN (webhook-driven
// auto-creation from Stripe events) are real backend modes but were never
// user-facing choices; they're reachable via the API/MCP tools, not this
// dropdown. If an account is already on one of them, it's shown as its
// current value so saving doesn't silently change it.
const stripeModeItems = [
  {
    label: "Stripe drives billing",
    value: "STRIPE_INTEGRATION",
    description: "Stripe drives billing. Tanso becomes your management plane for entitlements, usage tracking, and revenue analytics.",
  },
  {
    label: "Tanso handles billing",
    value: "PAYMENT_PASS_THROUGH",
    description: "Tanso manages subscriptions, pricing, and invoices. Stripe collects payments via Checkout.",
  },
]

const otherStripeModeLabels: Record<string, string> = {
  FULL_SYNC: "Full sync (legacy alias for Stripe drives billing)",
  STRIPE_DRIVEN: "Stripe driven (set via API — not offered here)",
}

function SettingsForm() {
  const settings = useAccountSettings()
  const updateSettings = useUpdateAccountSettings()
  const stripeKeys = useStripeKeys()
  const stripeConnected = !!stripeKeys.data?.stripeApiKey

  const form = useForm<AccountSettingsInput>({
    resolver: zodResolver(accountSettingsSchema),
    values: {
      currency: settings.data?.currency ?? "USD",
      stripeMode: (settings.data?.stripeMode as AccountSettingsInput["stripeMode"]) ?? "NONE",
      stripeCheckoutSuccessUrl: settings.data?.stripeCheckoutSuccessUrl ?? "",
      stripeCheckoutCancelUrl: settings.data?.stripeCheckoutCancelUrl ?? "",
    },
  })
  const errors = form.formState.errors

  if (settings.isPending) {
    return <Skeleton className="h-64 w-full" />
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Billing</CardTitle>
        <CardDescription>Currency and Stripe behavior for this account.</CardDescription>
      </CardHeader>
      <CardContent>
        <form
          onSubmit={form.handleSubmit((input) =>
            updateSettings.mutate(input, {
              onSuccess: () => toast.add({ title: "Settings saved" }),
              onError: (error) => toast.add({ title: "Save failed", description: error.message }),
            }),
          )}
        >
          <FieldGroup>
            <div className="grid grid-cols-2 gap-4">
              <Field data-invalid={!!errors.currency || undefined}>
                <FieldLabel htmlFor="currency">Currency</FieldLabel>
                <Input
                  id="currency"
                  className="uppercase"
                  maxLength={3}
                  aria-invalid={!!errors.currency}
                  {...form.register("currency")}
                />
                {errors.currency && <FieldError>{errors.currency.message}</FieldError>}
              </Field>
              <Field>
                <FieldLabel htmlFor="stripe-mode">Stripe mode</FieldLabel>
                {!stripeConnected && form.getValues("stripeMode") === "NONE" ? (
                  <>
                    <Input id="stripe-mode" value="None" disabled />
                    <FieldDescription>
                      No Stripe involvement — bill inside Tanso only. Connect Stripe below to
                      choose who handles billing.
                    </FieldDescription>
                  </>
                ) : !stripeConnected ? (
                  <>
                    <Input
                      id="stripe-mode"
                      value={
                        stripeModeItems.find((item) => item.value === form.getValues("stripeMode"))
                          ?.label ?? otherStripeModeLabels[form.getValues("stripeMode")]
                      }
                      disabled
                    />
                    <FieldDescription className="text-destructive">
                      Set to this mode but no Stripe key is connected — sync calls will fail.
                      Reconnect Stripe below, or this setting has no effect.
                    </FieldDescription>
                  </>
                ) : (
                  <Controller
                    control={form.control}
                    name="stripeMode"
                    render={({ field }) => {
                      // Connected but never chosen yet — show unselected rather
                      // than a bare "NONE" that isn't one of the two real choices.
                      const unset = field.value === "NONE"
                      const otherLabel = otherStripeModeLabels[field.value]
                      const items = otherLabel
                        ? [...stripeModeItems, { label: otherLabel, value: field.value, description: undefined }]
                        : stripeModeItems
                      return (
                        <>
                          <Select
                            items={items}
                            value={unset ? null : field.value}
                            onValueChange={field.onChange}
                          >
                            <SelectTrigger id="stripe-mode">
                              <SelectValue placeholder="Choose who handles billing" />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectGroup>
                                {items.map((item) => (
                                  <SelectItem key={item.value} value={item.value}>
                                    {item.label}
                                  </SelectItem>
                                ))}
                              </SelectGroup>
                            </SelectContent>
                          </Select>
                          <FieldDescription>
                            {unset
                              ? "Stripe is connected but no billing owner is chosen yet — pick one."
                              : otherLabel
                                ? "Set outside this dropdown (API or MCP tools). Pick one of the two options above to move off it."
                                : stripeModeItems.find((item) => item.value === field.value)?.description}
                          </FieldDescription>
                        </>
                      )
                    }}
                  />
                )}
              </Field>
            </div>
            <Field data-invalid={!!errors.stripeCheckoutSuccessUrl || undefined}>
              <FieldLabel htmlFor="success-url">Checkout success URL</FieldLabel>
              <Input
                id="success-url"
                placeholder="https://app.example.com/billing/success"
                aria-invalid={!!errors.stripeCheckoutSuccessUrl}
                {...form.register("stripeCheckoutSuccessUrl")}
              />
              {errors.stripeCheckoutSuccessUrl && (
                <FieldError>{errors.stripeCheckoutSuccessUrl.message}</FieldError>
              )}
            </Field>
            <Field data-invalid={!!errors.stripeCheckoutCancelUrl || undefined}>
              <FieldLabel htmlFor="cancel-url">Checkout cancel URL</FieldLabel>
              <Input
                id="cancel-url"
                placeholder="https://app.example.com/billing/cancelled"
                aria-invalid={!!errors.stripeCheckoutCancelUrl}
                {...form.register("stripeCheckoutCancelUrl")}
              />
              {errors.stripeCheckoutCancelUrl && (
                <FieldError>{errors.stripeCheckoutCancelUrl.message}</FieldError>
              )}
            </Field>
            <div>
              <Button type="submit" disabled={updateSettings.isPending}>
                {updateSettings.isPending && <Spinner data-icon="inline-start" />}
                Save changes
              </Button>
            </div>
          </FieldGroup>
        </form>
      </CardContent>
    </Card>
  )
}

function ApiKeyCard() {
  const apiKey = useApiKey()
  const [revealed, setRevealed] = useState(false)
  const key = apiKey.data?.apiKey

  function mask(value: string) {
    if (value.length <= 12) return value
    return `${value.slice(0, 8)}${"•".repeat(20)}${value.slice(-4)}`
  }

  async function copyKey() {
    if (!key) return
    await navigator.clipboard.writeText(key)
    toast.add({ title: "API key copied" })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>API key</CardTitle>
        <CardDescription>
          Server-to-server key for /api/v1/client endpoints. Keep it secret.
        </CardDescription>
      </CardHeader>
      <CardContent>
        {apiKey.isPending ? (
          <Skeleton className="h-9 w-full" />
        ) : !key ? (
          <p className="text-sm text-muted-foreground">No API key on this account.</p>
        ) : (
          <div className="flex items-center gap-2">
            <code className="flex-1 truncate rounded-md border bg-muted px-3 py-2 font-mono text-xs">
              {revealed ? key : mask(key)}
            </code>
            <Button
              variant="outline"
              size="icon"
              aria-label={revealed ? "Hide API key" : "Reveal API key"}
              onClick={() => setRevealed(!revealed)}
            >
              {revealed ? <EyeOff /> : <Eye />}
            </Button>
            <Button variant="outline" size="icon" aria-label="Copy API key" onClick={copyKey}>
              <Copy />
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

export default function SettingsPage() {
  return (
    <>
      <div>
        <h1 className="text-xl font-semibold tracking-tight">Settings</h1>
        <p className="text-sm text-muted-foreground">Account, billing, and integration options.</p>
      </div>
      <SettingsForm />
      <StripeCard />
      <AgentServeCard />
      <ApiKeyCard />
    </>
  )
}
