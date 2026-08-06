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
import { Textarea } from "@/components/ui/textarea"
import { creditGrantSchema, type CreditGrantInput } from "./schemas"

type CreditGrantFormValues = z.input<typeof creditGrantSchema>

const grantTypeItems = [
  { label: "Plan included", value: "PLAN_INCLUDED" },
  { label: "Purchased", value: "PURCHASED" },
  { label: "Promotional", value: "PROMOTIONAL" },
  { label: "Refund", value: "REFUND" },
  { label: "System", value: "SYSTEM" },
  { label: "Rollover", value: "ROLLOVER" },
]

interface GrantFormProps {
  isPending: boolean
  onSubmit: (input: CreditGrantInput) => void
}

export function GrantForm({ isPending, onSubmit }: GrantFormProps) {
  const form = useForm<CreditGrantFormValues, unknown, CreditGrantInput>({
    resolver: zodResolver(creditGrantSchema),
    defaultValues: {
      amount: undefined,
      grantType: "PROMOTIONAL",
      unitPrice: "",
      currency: "",
      expiresAt: "",
      description: "",
    },
  })
  const errors = form.formState.errors

  return (
    <form onSubmit={form.handleSubmit(onSubmit)}>
      <FieldGroup>
        <div className="grid grid-cols-2 gap-4">
          <Field data-invalid={!!errors.amount || undefined}>
            <FieldLabel htmlFor="grant-amount">Amount</FieldLabel>
            <Input
              id="grant-amount"
              type="number"
              step="any"
              min="0"
              aria-invalid={!!errors.amount}
              {...form.register("amount")}
            />
            {errors.amount && <FieldError>{errors.amount.message}</FieldError>}
          </Field>
          <Field>
            <FieldLabel htmlFor="grant-type">Grant type</FieldLabel>
            <Controller
              control={form.control}
              name="grantType"
              render={({ field }) => (
                <Select items={grantTypeItems} value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="grant-type">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {grantTypeItems.map((item) => (
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
        <div className="grid grid-cols-2 gap-4">
          <Field data-invalid={!!errors.unitPrice || undefined}>
            <FieldLabel htmlFor="grant-unit-price">Unit price (optional)</FieldLabel>
            <Input
              id="grant-unit-price"
              type="number"
              step="any"
              min="0"
              placeholder="Book price"
              aria-invalid={!!errors.unitPrice}
              {...form.register("unitPrice")}
            />
            {errors.unitPrice && <FieldError>{errors.unitPrice.message}</FieldError>}
          </Field>
          <Field data-invalid={!!errors.currency || undefined}>
            <FieldLabel htmlFor="grant-currency">Currency</FieldLabel>
            <Input
              id="grant-currency"
              className="uppercase"
              maxLength={3}
              placeholder="USD"
              aria-invalid={!!errors.currency}
              {...form.register("currency")}
            />
            {errors.currency && <FieldError>{errors.currency.message}</FieldError>}
          </Field>
        </div>
        <p className="text-xs text-muted-foreground">
          Leave unit price empty to stamp purchased grants with the current price book entry.
          Set it for negotiated top-ups and volume deals.
        </p>
        <Field>
          <FieldLabel htmlFor="grant-expires">Expires (optional)</FieldLabel>
          <Input id="grant-expires" type="datetime-local" {...form.register("expiresAt")} />
        </Field>
        <Field>
          <FieldLabel htmlFor="grant-description">Description</FieldLabel>
          <Textarea id="grant-description" rows={2} {...form.register("description")} />
        </Field>
        <Button type="submit" disabled={isPending}>
          {isPending && <Spinner data-icon="inline-start" />}
          Grant credits
        </Button>
      </FieldGroup>
    </form>
  )
}
