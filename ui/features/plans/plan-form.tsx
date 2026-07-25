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
import { Textarea } from "@/components/ui/textarea"
import type { PlanDto } from "@/lib/api/types"
import { planSchema, type PlanInput } from "./schemas"

const billingTimingItems = [
  { label: "In advance", value: "IN_ADVANCE" },
  { label: "In arrears", value: "IN_ARREARS" },
]

const statusItems = [
  { label: "Draft", value: "DRAFT" },
  { label: "Active", value: "ACTIVE" },
  { label: "Archived", value: "ARCHIVED" },
]

interface PlanFormProps {
  plan?: PlanDto
  isPending: boolean
  onSubmit: (input: PlanInput) => void
}

export function PlanForm({ plan, isPending, onSubmit }: PlanFormProps) {
  const form = useForm<PlanInput>({
    resolver: zodResolver(planSchema),
    defaultValues: {
      key: plan?.key ?? "",
      name: plan?.name ?? "",
      description: plan?.description ?? "",
      priceAmount: plan?.priceAmount ?? 0,
      intervalMonths: plan?.intervalMonths ? Number(plan.intervalMonths) : 1,
      billingTiming: (plan?.billingTiming as PlanInput["billingTiming"]) ?? "IN_ADVANCE",
      status: (plan?.status as PlanInput["status"]) ?? "ACTIVE",
    },
  })
  const errors = form.formState.errors

  return (
    <form onSubmit={form.handleSubmit(onSubmit)}>
      <FieldGroup>
        <Field data-invalid={!!errors.name || undefined}>
          <FieldLabel htmlFor="plan-name">Name</FieldLabel>
          <Input id="plan-name" aria-invalid={!!errors.name} {...form.register("name")} />
          {errors.name && <FieldError>{errors.name.message}</FieldError>}
        </Field>
        <Field data-invalid={!!errors.key || undefined}>
          <FieldLabel htmlFor="plan-key">Key</FieldLabel>
          <Input
            id="plan-key"
            placeholder="pro_tier"
            aria-invalid={!!errors.key}
            {...form.register("key")}
          />
          {errors.key && <FieldError>{errors.key.message}</FieldError>}
        </Field>
        <Field>
          <FieldLabel htmlFor="plan-description">Description</FieldLabel>
          <Textarea id="plan-description" rows={2} {...form.register("description")} />
        </Field>
        <div className="grid grid-cols-2 gap-4">
          <Field data-invalid={!!errors.priceAmount || undefined}>
            <FieldLabel htmlFor="plan-price">Price</FieldLabel>
            <Input
              id="plan-price"
              type="number"
              step="0.01"
              min="0"
              aria-invalid={!!errors.priceAmount}
              {...form.register("priceAmount")}
            />
            {errors.priceAmount && <FieldError>{errors.priceAmount.message}</FieldError>}
          </Field>
          <Field data-invalid={!!errors.intervalMonths || undefined}>
            <FieldLabel htmlFor="plan-interval">Interval (months)</FieldLabel>
            <Input
              id="plan-interval"
              type="number"
              min="1"
              aria-invalid={!!errors.intervalMonths}
              {...form.register("intervalMonths")}
            />
            {errors.intervalMonths && <FieldError>{errors.intervalMonths.message}</FieldError>}
          </Field>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <Field>
            <FieldLabel>Billing timing</FieldLabel>
            <Controller
              control={form.control}
              name="billingTiming"
              render={({ field }) => (
                <Select
                  items={billingTimingItems}
                  value={field.value}
                  onValueChange={field.onChange}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {billingTimingItems.map((item) => (
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
          <Field>
            <FieldLabel>Status</FieldLabel>
            <Controller
              control={form.control}
              name="status"
              render={({ field }) => (
                <Select items={statusItems} value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {statusItems.map((item) => (
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
        <Button type="submit" disabled={isPending}>
          {isPending && <Spinner data-icon="inline-start" />}
          {plan ? "Save changes" : "Create plan"}
        </Button>
      </FieldGroup>
    </form>
  )
}
