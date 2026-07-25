"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { Controller, useForm } from "react-hook-form"

import { Button } from "@/components/ui/button"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Spinner } from "@/components/ui/spinner"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import type { FeatureDto } from "@/lib/api/types"
import { featureSchema, type FeatureInput } from "./schemas"

interface FeatureFormProps {
  feature?: FeatureDto
  isPending: boolean
  onSubmit: (input: FeatureInput) => void
}

export function FeatureForm({ feature, isPending, onSubmit }: FeatureFormProps) {
  const form = useForm<FeatureInput>({
    resolver: zodResolver(featureSchema),
    values: {
      name: feature?.name ?? "",
      key: feature?.key ?? "",
      description: feature?.description ?? "",
      isEnabled: feature?.isEnabled ?? true,
    },
  })
  const errors = form.formState.errors

  return (
    <form onSubmit={form.handleSubmit(onSubmit)}>
      <FieldGroup>
        <Field data-invalid={!!errors.name || undefined}>
          <FieldLabel htmlFor="feature-name">Name</FieldLabel>
          <Input id="feature-name" aria-invalid={!!errors.name} {...form.register("name")} />
          {errors.name && <FieldError>{errors.name.message}</FieldError>}
        </Field>
        <Field data-invalid={!!errors.key || undefined}>
          <FieldLabel htmlFor="feature-key">Key</FieldLabel>
          <Input
            id="feature-key"
            placeholder="feature_api_access"
            aria-invalid={!!errors.key}
            {...form.register("key")}
          />
          {errors.key && <FieldError>{errors.key.message}</FieldError>}
        </Field>
        <Field>
          <FieldLabel htmlFor="feature-description">Description</FieldLabel>
          <Textarea id="feature-description" rows={2} {...form.register("description")} />
        </Field>
        <Field orientation="horizontal">
          <FieldLabel htmlFor="feature-enabled">Enabled</FieldLabel>
          <Controller
            control={form.control}
            name="isEnabled"
            render={({ field }) => (
              <Switch id="feature-enabled" checked={field.value} onCheckedChange={field.onChange} />
            )}
          />
        </Field>
        <Button type="submit" disabled={isPending}>
          {isPending && <Spinner data-icon="inline-start" />}
          {feature ? "Save changes" : "Create feature"}
        </Button>
      </FieldGroup>
    </form>
  )
}
