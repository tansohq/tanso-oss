"use client"

import Link from "next/link"
import { ArrowRight, Check, Circle } from "lucide-react"

import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { useCustomers } from "@/features/customers/queries"
import { useEvents } from "@/features/events/queries"
import { useFeatures } from "@/features/features/queries"
import { usePlans } from "@/features/plans/queries"

interface Step {
  label: string
  done: boolean
  href: string
}

export function GettingStartedCard() {
  const features = useFeatures()
  const plans = usePlans()
  const customers = useCustomers()
  const events = useEvents({ page: 0, size: 1 })

  const loading =
    features.isPending || plans.isPending || customers.isPending || events.isPending
  if (loading) return null

  const steps: Step[] = [
    {
      label: "Create a feature — the unit you meter",
      done: (features.data ?? []).length > 0,
      href: "/features",
    },
    {
      label: "Create a plan and link the feature to it",
      done: (plans.data ?? []).length > 0,
      href: "/plans",
    },
    {
      label: "Create a customer and subscribe them",
      done: (customers.data?.customers ?? []).length > 0,
      href: "/customers",
    },
    {
      label: "Send your first event from your app",
      done: (events.data?.items ?? []).length > 0,
      href: "/events",
    },
  ]

  if (steps.every((s) => s.done)) return null

  return (
    <Card>
      <CardHeader>
        <CardTitle>Getting started</CardTitle>
        <CardDescription>
          Four steps from empty account to metered usage. Margin analytics light up as data
          arrives.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-2">
        {steps.map((step) => (
          <Link
            key={step.href}
            href={step.href}
            className="group flex items-center gap-3 rounded-lg border p-3 transition-colors hover:bg-muted"
          >
            {step.done ? (
              <Check className="size-4 text-primary" />
            ) : (
              <Circle className="size-4 text-muted-foreground" />
            )}
            <span className={step.done ? "text-sm text-muted-foreground line-through" : "text-sm"}>
              {step.label}
            </span>
            <ArrowRight className="ml-auto size-4 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" />
          </Link>
        ))}
      </CardContent>
    </Card>
  )
}
