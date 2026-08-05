"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"

import { Button } from "@/components/ui/button"
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Spinner } from "@/components/ui/spinner"
import type { CustomerDto } from "@/lib/api/types"
import { customerSchema, type CustomerInput } from "./schemas"

interface CustomerFormProps {
  customer?: CustomerDto
  isPending: boolean
  onSubmit: (input: CustomerInput) => void
}

export function CustomerForm({ customer, isPending, onSubmit }: CustomerFormProps) {
  const form = useForm<CustomerInput>({
    resolver: zodResolver(customerSchema),
    values: {
      customerReferenceId: customer?.customerReferenceId ?? "",
      firstName: customer?.firstName ?? "",
      lastName: customer?.lastName ?? "",
      email: customer?.email ?? "",
      phoneNumber: customer?.phoneNumber ?? "",
    },
  })
  const errors = form.formState.errors
  const referenceId = form.watch("customerReferenceId")

  return (
    <form onSubmit={form.handleSubmit(onSubmit)}>
      <FieldGroup>
        <div className="grid grid-cols-2 gap-4">
          <Field>
            <FieldLabel htmlFor="customer-first-name">First name</FieldLabel>
            <Input id="customer-first-name" {...form.register("firstName")} />
          </Field>
          <Field>
            <FieldLabel htmlFor="customer-last-name">Last name</FieldLabel>
            <Input id="customer-last-name" {...form.register("lastName")} />
          </Field>
        </div>
        <Field data-invalid={!!errors.email || undefined}>
          <FieldLabel htmlFor="customer-email">Email</FieldLabel>
          <Input
            id="customer-email"
            type="email"
            aria-invalid={!!errors.email}
            {...form.register("email")}
          />
          {errors.email && <FieldError>{errors.email.message}</FieldError>}
        </Field>
        <Field>
          <FieldLabel htmlFor="customer-reference-id">Reference ID</FieldLabel>
          <Input
            id="customer-reference-id"
            placeholder="Your internal customer ID"
            {...form.register("customerReferenceId")}
          />
          <FieldDescription>
            How your product&apos;s API calls identify this customer — entitlement checks and
            events key off it.
          </FieldDescription>
          {!customer && !referenceId && (
            <p className="text-xs text-amber-500">
              Without a Reference ID, client-API entitlement checks for this customer will fail.
            </p>
          )}
        </Field>
        <Field>
          <FieldLabel htmlFor="customer-phone">Phone</FieldLabel>
          <Input id="customer-phone" type="tel" {...form.register("phoneNumber")} />
        </Field>
        <Button type="submit" disabled={isPending}>
          {isPending && <Spinner data-icon="inline-start" />}
          {customer ? "Save changes" : "Create customer"}
        </Button>
      </FieldGroup>
    </form>
  )
}
