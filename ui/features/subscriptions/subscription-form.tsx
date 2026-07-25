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
import { subscriptionSchema, type SubscriptionInput } from "./schemas"

interface Option {
  label: string
  value: string
}

interface SubscriptionFormProps {
  customers: Option[]
  plans: Option[]
  isPending: boolean
  onSubmit: (input: SubscriptionInput) => void
}

export function SubscriptionForm({ customers, plans, isPending, onSubmit }: SubscriptionFormProps) {
  const form = useForm<SubscriptionInput>({
    resolver: zodResolver(subscriptionSchema),
    defaultValues: { customerId: "", planId: "", gracePeriod: undefined },
  })
  const errors = form.formState.errors

  return (
    <form onSubmit={form.handleSubmit(onSubmit)}>
      <FieldGroup>
        <Field data-invalid={!!errors.customerId || undefined}>
          <FieldLabel>Customer</FieldLabel>
          <Controller
            control={form.control}
            name="customerId"
            render={({ field }) => (
              <Select items={customers} value={field.value || null} onValueChange={field.onChange}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {customers.map((item) => (
                      <SelectItem key={item.value} value={item.value}>
                        {item.label}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            )}
          />
          {errors.customerId && <FieldError>{errors.customerId.message}</FieldError>}
        </Field>
        <Field data-invalid={!!errors.planId || undefined}>
          <FieldLabel>Plan</FieldLabel>
          <Controller
            control={form.control}
            name="planId"
            render={({ field }) => (
              <Select items={plans} value={field.value || null} onValueChange={field.onChange}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {plans.map((item) => (
                      <SelectItem key={item.value} value={item.value}>
                        {item.label}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            )}
          />
          {errors.planId && <FieldError>{errors.planId.message}</FieldError>}
        </Field>
        <Field data-invalid={!!errors.gracePeriod || undefined}>
          <FieldLabel htmlFor="sub-grace">Grace period (days, optional)</FieldLabel>
          <Input
            id="sub-grace"
            type="number"
            min="0"
            aria-invalid={!!errors.gracePeriod}
            {...form.register("gracePeriod", {
              setValueAs: (v) => (v === "" ? undefined : Number(v)),
            })}
          />
          {errors.gracePeriod && <FieldError>{errors.gracePeriod.message}</FieldError>}
        </Field>
        <Button type="submit" disabled={isPending}>
          {isPending && <Spinner data-icon="inline-start" />}
          Create subscription
        </Button>
      </FieldGroup>
    </form>
  )
}
