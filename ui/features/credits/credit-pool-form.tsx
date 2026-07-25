"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { Controller, useForm } from "react-hook-form"

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
import { creditPoolSchema, type CreditPoolInput } from "./schemas"

const rolloverItems = [
  { label: "None", value: "NONE" },
  { label: "Full", value: "FULL" },
  { label: "Capped", value: "CAPPED" },
]

interface Option {
  label: string
  value: string
}

interface CreditPoolFormProps {
  customers: Option[]
  isPending: boolean
  onSubmit: (input: CreditPoolInput) => void
}

export function CreditPoolForm({ customers, isPending, onSubmit }: CreditPoolFormProps) {
  const form = useForm<CreditPoolInput>({
    resolver: zodResolver(creditPoolSchema),
    defaultValues: {
      name: "",
      denomination: "",
      currency: "",
      customerId: "",
      hardLimit: false,
      rolloverPolicy: "NONE",
      rolloverCap: undefined,
    },
  })
  const errors = form.formState.errors
  const rolloverPolicy = form.watch("rolloverPolicy")
  const customerItems = [{ label: "No customer", value: null as string | null }, ...customers]

  return (
    <form onSubmit={form.handleSubmit(onSubmit)}>
      <FieldGroup>
        <Field data-invalid={!!errors.name || undefined}>
          <FieldLabel htmlFor="pool-name">Name</FieldLabel>
          <Input id="pool-name" aria-invalid={!!errors.name} {...form.register("name")} />
          {errors.name && <FieldError>{errors.name.message}</FieldError>}
        </Field>
        <div className="grid grid-cols-2 gap-4">
          <Field data-invalid={!!errors.denomination || undefined}>
            <FieldLabel htmlFor="pool-denomination">Denomination</FieldLabel>
            <Input
              id="pool-denomination"
              placeholder="credits"
              aria-invalid={!!errors.denomination}
              {...form.register("denomination")}
            />
            {errors.denomination && <FieldError>{errors.denomination.message}</FieldError>}
          </Field>
          <Field>
            <FieldLabel htmlFor="pool-currency">Currency</FieldLabel>
            <Input id="pool-currency" placeholder="USD" {...form.register("currency")} />
          </Field>
        </div>
        <Field>
          <FieldLabel>Customer (optional)</FieldLabel>
          <Controller
            control={form.control}
            name="customerId"
            render={({ field }) => (
              <Select
                items={customerItems}
                value={field.value || null}
                onValueChange={(value) => field.onChange(value ?? "")}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {customerItems.map((item) => (
                      <SelectItem key={item.value ?? "none"} value={item.value}>
                        {item.label}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            )}
          />
        </Field>
        <div className="grid grid-cols-2 gap-4">
          <Field>
            <FieldLabel>Rollover policy</FieldLabel>
            <Controller
              control={form.control}
              name="rolloverPolicy"
              render={({ field }) => (
                <Select items={rolloverItems} value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger>
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
              <FieldLabel htmlFor="pool-cap">Rollover cap</FieldLabel>
              <Input
                id="pool-cap"
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
          <FieldLabel htmlFor="pool-hard-limit">Hard limit</FieldLabel>
          <Controller
            control={form.control}
            name="hardLimit"
            render={({ field }) => (
              <Switch id="pool-hard-limit" checked={field.value} onCheckedChange={field.onChange} />
            )}
          />
        </Field>
        <Button type="submit" disabled={isPending}>
          {isPending && <Spinner data-icon="inline-start" />}
          Create pool
        </Button>
      </FieldGroup>
    </form>
  )
}
