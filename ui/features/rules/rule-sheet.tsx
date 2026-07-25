"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { Controller, useForm } from "react-hook-form"

import { Button } from "@/components/ui/button"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { Spinner } from "@/components/ui/spinner"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import type { FeatureDto, PlanFeatureRuleDto } from "@/lib/api/types"
import { ruleSchema, type RuleInput } from "./schemas"

interface RuleSheetProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  features: FeatureDto[]
  rule?: PlanFeatureRuleDto
  isPending: boolean
  onSubmit: (input: RuleInput) => void
}

export function RuleSheet({ open, onOpenChange, features, rule, isPending, onSubmit }: RuleSheetProps) {
  const featureItems = features.map((f) => ({ label: f.name ?? f.key ?? "", value: f.id ?? "" }))

  const form = useForm<RuleInput>({
    resolver: zodResolver(ruleSchema),
    values: {
      featureId: rule?.featureId ?? "",
      isEnabled: rule?.enabled ?? true,
      value: rule?.value ? JSON.stringify(rule.value, null, 2) : "{\n  \n}",
      creditModelId: rule?.creditModelId ?? "",
    },
  })
  const errors = form.formState.errors

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent>
        <SheetHeader>
          <SheetTitle>{rule ? "Edit rule" : "Attach feature"}</SheetTitle>
          <SheetDescription>
            {rule
              ? "How this feature behaves in the plan."
              : "Link a feature to this plan with a pricing rule."}
          </SheetDescription>
        </SheetHeader>
        <div className="px-4">
          <form onSubmit={form.handleSubmit(onSubmit)}>
            <FieldGroup>
              <Field data-invalid={!!errors.featureId || undefined}>
                <FieldLabel>Feature</FieldLabel>
                <Controller
                  control={form.control}
                  name="featureId"
                  render={({ field }) => (
                    <Select
                      items={featureItems}
                      value={field.value || null}
                      onValueChange={field.onChange}
                      disabled={!!rule}
                    >
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {featureItems.map((item) => (
                            <SelectItem key={item.value} value={item.value}>
                              {item.label}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  )}
                />
                {errors.featureId && <FieldError>{errors.featureId.message}</FieldError>}
              </Field>
              <Field data-invalid={!!errors.value || undefined}>
                <FieldLabel htmlFor="rule-value">Rule value (JSON)</FieldLabel>
                <Textarea
                  id="rule-value"
                  rows={8}
                  className="font-mono text-xs"
                  aria-invalid={!!errors.value}
                  {...form.register("value")}
                />
                {errors.value && <FieldError>{errors.value.message}</FieldError>}
              </Field>
              <Field orientation="horizontal">
                <FieldLabel htmlFor="rule-enabled">Enabled</FieldLabel>
                <Controller
                  control={form.control}
                  name="isEnabled"
                  render={({ field }) => (
                    <Switch id="rule-enabled" checked={field.value} onCheckedChange={field.onChange} />
                  )}
                />
              </Field>
              <Button type="submit" disabled={isPending}>
                {isPending && <Spinner data-icon="inline-start" />}
                {rule ? "Save rule" : "Attach feature"}
              </Button>
            </FieldGroup>
          </form>
        </div>
      </SheetContent>
    </Sheet>
  )
}
