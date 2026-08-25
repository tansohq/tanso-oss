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
]

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
    defaultValues: { provider: "ANTHROPIC", label: "", adminKey: "" },
  })
  const errors = form.formState.errors

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
          <FieldLabel htmlFor="vendor-admin-key">Admin key</FieldLabel>
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
          <p className="text-xs text-muted-foreground">
            Stored encrypted. Tanso only reads usage and cost reports with it,
            but the key itself can administer your whole vendor org — use a
            dedicated reporting org where you can.
          </p>
        </Field>
        <Button type="submit" disabled={isPending}>
          {isPending && <Spinner data-icon="inline-start" />}
          Connect
        </Button>
      </FieldGroup>
    </form>
  )
}
