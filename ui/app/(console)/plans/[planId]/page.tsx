"use client"

import { use, useState } from "react"
import { Pencil, Plus } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { toast } from "@/components/ui/toast"
import type { FeatureDto } from "@/lib/api/types"
import { formatCurrency, formatDate, formatNumber } from "@/lib/format"
import { useFeatures } from "@/features/features/queries"
import { useUpdatePlan } from "@/features/plans/mutations"
import { PlanForm } from "@/features/plans/plan-form"
import {
  usePlanCreditAllocations,
  usePlanFeatures,
  usePlanRevenue,
  usePlans,
} from "@/features/plans/queries"
import { useCreateRule, useUpdateRule } from "@/features/rules/mutations"
import { usePlanFeatureRule } from "@/features/rules/queries"
import { RuleSheet } from "@/features/rules/rule-sheet"

export default function PlanDetailPage({ params }: { params: Promise<{ planId: string }> }) {
  const { planId } = use(params)
  const plans = usePlans()
  const linked = usePlanFeatures(planId)
  const revenue = usePlanRevenue(planId)
  const allocations = usePlanCreditAllocations(planId)
  const allFeatures = useFeatures()
  const updatePlan = useUpdatePlan(planId)
  const createRule = useCreateRule(planId)
  const updateRule = useUpdateRule(planId)

  const [editOpen, setEditOpen] = useState(false)
  const [ruleFeature, setRuleFeature] = useState<FeatureDto | null>(null)
  const [attachOpen, setAttachOpen] = useState(false)

  const rule = usePlanFeatureRule(planId, ruleFeature?.id ?? null)

  const plan = linked.data?.plan ?? plans.data?.find((p) => p.id === planId)
  const linkedFeatures = linked.data?.features ?? []
  const linkedIds = new Set(linkedFeatures.map((f) => f.id))
  const attachableFeatures = (allFeatures.data ?? []).filter((f) => !linkedIds.has(f.id))

  if (linked.isPending) {
    return (
      <div className="flex flex-col gap-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-40 w-full" />
      </div>
    )
  }

  return (
    <>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="text-xl font-semibold tracking-tight">{plan?.name ?? "Plan"}</h1>
          <span className="font-mono text-xs text-muted-foreground">{plan?.key}</span>
          {plan?.status && (
            <Badge variant={plan.status === "ACTIVE" ? "default" : "secondary"}>{plan.status}</Badge>
          )}
        </div>
        <Button variant="outline" onClick={() => setEditOpen(true)}>
          <Pencil data-icon="inline-start" />
          Edit plan
        </Button>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader>
            <CardDescription>Base price</CardDescription>
            <CardTitle className="font-mono text-2xl tabular-nums">
              {formatCurrency(plan?.priceAmount)}
            </CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">
            {plan?.intervalMonths === "1" ? "Billed monthly" : `Billed every ${plan?.intervalMonths} months`}
            {" · "}
            {plan?.billingTiming === "IN_ADVANCE" ? "in advance" : "in arrears"}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Revenue this period</CardDescription>
            <CardTitle className="font-mono text-2xl tabular-nums">
              {formatCurrency(revenue.data?.aggregateTotalRevenue)}
            </CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">
            {revenue.data
              ? `${formatNumber(revenue.data.aggregateTotalUnits)} usage units since ${formatDate(revenue.data.periodStart)}`
              : "No revenue data"}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Credit allocations</CardDescription>
            <CardTitle className="font-mono text-2xl tabular-nums">
              {allocations.data?.length ?? 0}
            </CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">
            {allocations.data?.length
              ? allocations.data
                  .map((a) => `${formatNumber(a.creditAmount)} ${a.denomination ?? a.creditModelName}`)
                  .join(", ")
              : "No credit models attached to this plan"}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div className="flex flex-col gap-1.5">
              <CardTitle>Features</CardTitle>
              <CardDescription>Click a feature to view or edit its pricing rule.</CardDescription>
            </div>
            <Button
              variant="outline"
              onClick={() => setAttachOpen(true)}
              disabled={attachableFeatures.length === 0}
            >
              <Plus data-icon="inline-start" />
              Attach feature
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          {linkedFeatures.length === 0 ? (
            <Empty>
              <EmptyHeader>
                <EmptyTitle>No features attached</EmptyTitle>
                <EmptyDescription>
                  Attach a feature to define what this plan includes.
                </EmptyDescription>
              </EmptyHeader>
            </Empty>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Key</TableHead>
                  <TableHead>Enabled</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {linkedFeatures.map((feature) => (
                  <TableRow
                    key={feature.id}
                    className="cursor-pointer"
                    onClick={() => setRuleFeature(feature)}
                  >
                    <TableCell>{feature.name}</TableCell>
                    <TableCell className="font-mono text-xs">{feature.key}</TableCell>
                    <TableCell>
                      <Badge variant={feature.isEnabled ? "default" : "secondary"}>
                        {feature.isEnabled ? "Enabled" : "Disabled"}
                      </Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Sheet open={editOpen} onOpenChange={setEditOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Edit plan</SheetTitle>
            <SheetDescription>Changes apply to new billing cycles.</SheetDescription>
          </SheetHeader>
          <div className="px-4">
            {plan && (
              <PlanForm
                plan={plan}
                isPending={updatePlan.isPending}
                onSubmit={(input) =>
                  updatePlan.mutate(input, {
                    onSuccess: () => {
                      setEditOpen(false)
                      toast.add({ title: "Plan updated" })
                    },
                    onError: (error) =>
                      toast.add({ title: "Update failed", description: error.message }),
                  })
                }
              />
            )}
          </div>
        </SheetContent>
      </Sheet>

      <RuleSheet
        open={!!ruleFeature}
        onOpenChange={(open) => !open && setRuleFeature(null)}
        features={ruleFeature ? [ruleFeature] : []}
        rule={rule.data}
        isPending={updateRule.isPending}
        onSubmit={(input) =>
          updateRule.mutate(input, {
            onSuccess: () => {
              setRuleFeature(null)
              toast.add({ title: "Rule updated" })
            },
            onError: (error) => toast.add({ title: "Update failed", description: error.message }),
          })
        }
      />

      <RuleSheet
        open={attachOpen}
        onOpenChange={setAttachOpen}
        features={attachableFeatures}
        isPending={createRule.isPending}
        onSubmit={(input) =>
          createRule.mutate(input, {
            onSuccess: () => {
              setAttachOpen(false)
              toast.add({ title: "Feature attached" })
            },
            onError: (error) => toast.add({ title: "Attach failed", description: error.message }),
          })
        }
      />
    </>
  )
}
