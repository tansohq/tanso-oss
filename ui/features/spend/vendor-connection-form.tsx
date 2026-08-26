"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { Controller, useForm } from "react-hook-form"

import { Button } from "@/components/ui/button"
import {
  Field,
  FieldError,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Spinner } from "@/components/ui/spinner"
import { vendorConnectionSchema, type VendorConnectionInput } from "./schemas"

const providerItems = [
  { label: "Anthropic", value: "ANTHROPIC" },
  { label: "OpenAI", value: "OPENAI" },
  { label: "Cursor", value: "CURSOR" },
  { label: "GitHub Copilot", value: "COPILOT" },
  { label: "LiteLLM (gateway)", value: "LITELLM" },
]

const keyLabel: Record<string, string> = {
  ANTHROPIC: "Admin key",
  OPENAI: "Admin key",
  CURSOR: "Admin API key",
  COPILOT: "GitHub token",
  LITELLM: "Master key",
}

const keyHint: Record<string, string> = {
  ANTHROPIC:
    "Stored encrypted. Tanso only reads usage and cost reports with it, but the key itself can administer your whole vendor org — use a dedicated reporting org where you can.",
  OPENAI:
    "Stored encrypted. Tanso only reads usage and cost reports with it, but the key itself can administer your whole vendor org — use a dedicated reporting org where you can.",
  CURSOR:
    "An Enterprise admin API key. Stored encrypted; Tanso reads usage events, spend and daily activity.",
  COPILOT:
    "A token with the View Organization Copilot Metrics permission (or read:org). Stored encrypted; Tanso reads the per-user daily reports.",
  LITELLM:
    "Your proxy's master key. Stored encrypted; Tanso reads spend logs and, for units with a Block budget, sets max_budget on the team, key or user a rule names — the one place a budget is actually enforced.",
}

interface VendorConnectionFormProps {
  isPending: boolean
  onSubmit: (input: VendorConnectionInput) => void
}

export function VendorConnectionForm({
  isPending,
  onSubmit,
}: VendorConnectionFormProps) {
  const form = useForm<VendorConnectionInput>({
    resolver: zodResolver(vendorConnectionSchema),
    defaultValues: {
      provider: "ANTHROPIC",
      label: "",
      adminKey: "",
      scope: "",
    },
  })
  const errors = form.formState.errors
  const provider = form.watch("provider")

  return (
    <form onSubmit={form.handleSubmit(onSubmit)}>
      <FieldGroup>
        <Field>
          <FieldLabel htmlFor="vendor-provider">Provider</FieldLabel>
          <Controller
            control={form.control}
            name="provider"
            render={({ field }) => (
              <Select
                items={providerItems}
                value={field.value}
                onValueChange={field.onChange}
              >
                <SelectTrigger id="vendor-provider">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {providerItems.map((item) => (
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
        <Field data-invalid={!!errors.label || undefined}>
          <FieldLabel htmlFor="vendor-label">Label</FieldLabel>
          <Input
            id="vendor-label"
            placeholder="Engineering org"
            aria-invalid={!!errors.label}
            {...form.register("label")}
          />
          {errors.label && <FieldError>{errors.label.message}</FieldError>}
        </Field>
        <Field data-invalid={!!errors.adminKey || undefined}>
          <FieldLabel htmlFor="vendor-admin-key">
            {keyLabel[provider]}
          </FieldLabel>
          <Input
            id="vendor-admin-key"
            type="password"
            autoComplete="off"
            aria-invalid={!!errors.adminKey}
            {...form.register("adminKey")}
          />
          {errors.adminKey && (
            <FieldError>{errors.adminKey.message}</FieldError>
          )}
          <p className="text-xs text-muted-foreground">{keyHint[provider]}</p>
        </Field>
        {provider === "LITELLM" && (
          <Field data-invalid={!!errors.scope || undefined}>
            <FieldLabel htmlFor="vendor-scope">Proxy URL</FieldLabel>
            <Input
              id="vendor-scope"
              placeholder="https://llm.internal:4000"
              aria-invalid={!!errors.scope}
              {...form.register("scope")}
            />
            {errors.scope && <FieldError>{errors.scope.message}</FieldError>}
          </Field>
        )}
        {provider === "COPILOT" && (
          <Field data-invalid={!!errors.scope || undefined}>
            <FieldLabel htmlFor="vendor-scope">GitHub organization</FieldLabel>
            <Input
              id="vendor-scope"
              placeholder="acme"
              aria-invalid={!!errors.scope}
              {...form.register("scope")}
            />
            {errors.scope && <FieldError>{errors.scope.message}</FieldError>}
          </Field>
        )}
        <Button type="submit" disabled={isPending}>
          {isPending && <Spinner data-icon="inline-start" />}
          Connect
        </Button>
      </FieldGroup>
    </form>
  )
}
