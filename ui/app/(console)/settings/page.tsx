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
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
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
import { StripeCard } from "@/features/settings/stripe-card"
import { useAccountSettings, useApiKey } from "@/features/settings/queries"
import {
  accountSettingsSchema,
  type AccountSettingsInput,
} from "@/features/settings/schemas"

const stripeModeItems = [
  { label: "None", value: "NONE" },
  { label: "Payment pass-through", value: "PAYMENT_PASS_THROUGH" },
  { label: "Full sync", value: "FULL_SYNC" },
  { label: "Stripe integration", value: "STRIPE_INTEGRATION" },
  { label: "Stripe driven", value: "STRIPE_DRIVEN" },
]

function SettingsForm() {
  const settings = useAccountSettings()
  const updateSettings = useUpdateAccountSettings()

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
                <FieldLabel>Stripe mode</FieldLabel>
                <Controller
                  control={form.control}
                  name="stripeMode"
                  render={({ field }) => (
                    <Select
                      items={stripeModeItems}
                      value={field.value}
                      onValueChange={field.onChange}
                    >
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {stripeModeItems.map((item) => (
                            <SelectItem key={item.value} value={item.value}>
                              {item.label}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  )}
                />
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
      <ApiKeyCard />
    </>
  )
}
