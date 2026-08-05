"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { Controller, useForm } from "react-hook-form"
import type { z } from "zod"

import { Button } from "@/components/ui/button"
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
import { Spinner } from "@/components/ui/spinner"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import { creditModelSchema, type CreditModelInput } from "./schemas"

type CreditModelFormValues = z.input<typeof creditModelSchema>

const rolloverItems = [
  { label: "None", value: "NONE" },
  { label: "Full", value: "FULL" },
  { label: "Capped", value: "CAPPED" },
]

interface CreditModelFormProps {
  isPending: boolean
  onSubmit: (input: CreditModelInput) => void
}

export function CreditModelForm({ isPending, onSubmit }: CreditModelFormProps) {
  const form = useForm<CreditModelFormValues, unknown, CreditModelInput>({
    resolver: zodResolver(creditModelSchema),
    defaultValues: {
      name: "",
      denomination: "",
      description: "",
      hardLimit: false,
      rolloverPolicy: "NONE",
      rolloverCap: undefined,
    },
  })
  const errors = form.formState.errors
  const rolloverPolicy = form.watch("rolloverPolicy")

  return (
    <form onSubmit={form.handleSubmit(onSubmit)}>
      <FieldGroup>
        <Field data-invalid={!!errors.name || undefined}>
          <FieldLabel htmlFor="model-name">Name</FieldLabel>
          <Input id="model-name" aria-invalid={!!errors.name} {...form.register("name")} />
          {errors.name && <FieldError>{errors.name.message}</FieldError>}
        </Field>
        <Field data-invalid={!!errors.denomination || undefined}>
          <FieldLabel htmlFor="model-denomination">Denomination</FieldLabel>
          <Input
            id="model-denomination"
            placeholder="credits"
            aria-invalid={!!errors.denomination}
            {...form.register("denomination")}
          />
          {errors.denomination && <FieldError>{errors.denomination.message}</FieldError>}
        </Field>
        <Field>
          <FieldLabel htmlFor="model-description">Description</FieldLabel>
          <Textarea id="model-description" rows={2} {...form.register("description")} />
        </Field>
        <div className="grid grid-cols-2 gap-4">
          <Field>
            <FieldLabel htmlFor="credit-model-rollover">Rollover policy</FieldLabel>
            <Controller
              control={form.control}
              name="rolloverPolicy"
              render={({ field }) => (
                <Select items={rolloverItems} value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="credit-model-rollover">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {rolloverItems.map((item) => (
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
          {rolloverPolicy === "CAPPED" && (
            <Field data-invalid={!!errors.rolloverCap || undefined}>
              <FieldLabel htmlFor="model-cap">Rollover cap</FieldLabel>
              <Input
                id="model-cap"
                type="number"
                min="0"
                aria-invalid={!!errors.rolloverCap}
                {...form.register("rolloverCap")}
              />
              {errors.rolloverCap && <FieldError>{errors.rolloverCap.message}</FieldError>}
            </Field>
          )}
        </div>
        <Field orientation="horizontal">
          <FieldLabel htmlFor="model-hard-limit">Hard limit</FieldLabel>
          <Controller
            control={form.control}
            name="hardLimit"
            render={({ field }) => (
              <Switch id="model-hard-limit" checked={field.value} onCheckedChange={field.onChange} />
            )}
          />
        </Field>
        <Button type="submit" disabled={isPending}>
          {isPending && <Spinner data-icon="inline-start" />}
          Create credit model
        </Button>
      </FieldGroup>
    </form>
  )
}
