"use client"

import Link from "next/link"

import { Button } from "@/components/ui/button"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { formatCurrency } from "@/lib/format"
import { cn } from "@/lib/utils"
import type { CustomerAnalyticsDto, ModelSummaryDto } from "@/lib/api/types"

interface BreakdownRow {
  key: string
  label: string
  mono?: boolean
  cost: number
  revenue: number
  margin: number | null
}

function marginText(margin: number | null | undefined) {
  return margin != null ? `${(margin * 100).toFixed(1)}%` : "—"
}

function Kpi({ label, value, negative }: { label: string; value: string; negative?: boolean }) {
  return (
    <div className="min-w-0 rounded-lg border p-3">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div
        className={cn(
          "mt-0.5 truncate font-mono text-base font-semibold tabular-nums",
          negative && "text-destructive",
        )}
      >
        {value}
      </div>
    </div>
  )
}

function BreakdownList({ rows, labelHeader }: { rows: BreakdownRow[]; labelHeader: string }) {
  if (rows.length === 0) {
    return (
      <div className="py-6 text-center text-sm text-muted-foreground">No data for this view.</div>
    )
  }
  return (
    <div>
      <div className="flex items-center py-1.5 text-xs font-medium tracking-wider text-muted-foreground uppercase">
        <div className="flex-1">{labelHeader}</div>
        <div className="w-20 text-right">Cost</div>
        <div className="w-20 text-right">Revenue</div>
        <div className="w-16 text-right">Margin</div>
      </div>
      {rows.map((row) => (
        <div key={row.key} className="flex items-center py-2 text-sm">
          <div className={cn("flex-1 truncate font-medium", row.mono && "font-mono text-xs")}>
            {row.label}
          </div>
          <div className="w-20 text-right font-mono tabular-nums">{formatCurrency(row.cost)}</div>
          <div className="w-20 text-right font-mono tabular-nums">
            {formatCurrency(row.revenue)}
          </div>
          <div
            className={cn(
              "w-16 text-right font-mono tabular-nums",
              (row.margin ?? 0) < 0 && "text-destructive",
            )}
          >
            {marginText(row.margin)}
          </div>
        </div>
      ))}
    </div>
  )
}

function marginOf(cost: number, revenue: number): number | null {
  return revenue > 0 ? (revenue - cost) / revenue : null
}

export function CustomerDetailSheet({
  customer,
  open,
  onOpenChange,
}: {
  customer: CustomerAnalyticsDto | null
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const featureRows: BreakdownRow[] = (customer?.featureProfitability ?? [])
    .filter((f) => (f.cost ?? 0) > 0 || (f.revenue ?? 0) > 0)
    .map((f) => ({
      key: f.featureId ?? f.featureKey ?? "",
      label: f.featureName ?? f.featureKey ?? "—",
      cost: f.cost ?? 0,
      revenue: f.revenue ?? 0,
      margin: marginOf(f.cost ?? 0, f.revenue ?? 0),
    }))
    .sort((a, b) => b.cost - a.cost)

  const modelRows: BreakdownRow[] = (customer?.modelProfitability ?? [])
    .filter((m) => (m.cost ?? 0) > 0 || (m.revenue ?? 0) > 0)
    .map((m) => ({
      key: m.model ?? "unattributed",
      label: m.model ?? "Unattributed",
      mono: true,
      cost: m.cost ?? 0,
      revenue: m.revenue ?? 0,
      margin: m.margin ?? marginOf(m.cost ?? 0, m.revenue ?? 0),
    }))
    .sort((a, b) => b.cost - a.cost)

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="overflow-y-auto data-[side=right]:sm:max-w-xl">
        <SheetHeader>
          <SheetTitle>
            {customer?.customerName || customer?.email || customer?.customerReferenceId}
          </SheetTitle>
          <SheetDescription>Customer cost and margin breakdown.</SheetDescription>
        </SheetHeader>
        <div className="flex flex-col gap-6 px-4 pb-4">
          <div className="grid grid-cols-3 gap-3">
            <Kpi label="Cost" value={formatCurrency(customer?.totalCost ?? 0)} />
            <Kpi label="Revenue" value={formatCurrency(customer?.mrr ?? 0)} />
            <Kpi
              label="Margin"
              value={marginText(customer?.margin)}
              negative={(customer?.margin ?? 0) < 0}
            />
          </div>
          <Tabs defaultValue="feature">
            <TabsList className="w-full">
              <TabsTrigger value="feature" className="flex-1">
                By feature ({featureRows.length})
              </TabsTrigger>
              <TabsTrigger value="model" className="flex-1">
                By model ({modelRows.length})
              </TabsTrigger>
            </TabsList>
            <TabsContent value="feature" className="mt-3">
              <BreakdownList rows={featureRows} labelHeader="Feature" />
            </TabsContent>
            <TabsContent value="model" className="mt-3">
              <BreakdownList rows={modelRows} labelHeader="Model" />
            </TabsContent>
          </Tabs>
          {customer?.customerReferenceId && (
            <Button variant="outline" size="sm" nativeButton={false} render={<Link href={`/events?customerReferenceId=${encodeURIComponent(customer.customerReferenceId)}`} />}>
              View events for this customer
            </Button>
          )}
        </div>
      </SheetContent>
    </Sheet>
  )
}

export function ModelDetailSheet({
  model,
  customers,
  open,
  onOpenChange,
}: {
  model: ModelSummaryDto | null
  customers: CustomerAnalyticsDto[]
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const modelName = model?.model

  const customerRows: BreakdownRow[] = []
  const featureTotals = new Map<string, BreakdownRow>()
  for (const customer of customers) {
    let cost = 0
    let revenue = 0
    for (const feature of customer.featureProfitability ?? []) {
      for (const mb of feature.modelBreakdown ?? []) {
        if (mb.model !== modelName) continue
        cost += mb.cost ?? 0
        revenue += mb.revenue ?? 0
        const key = feature.featureId ?? feature.featureKey ?? ""
        const existing = featureTotals.get(key)
        if (existing) {
          existing.cost += mb.cost ?? 0
          existing.revenue += mb.revenue ?? 0
        } else {
          featureTotals.set(key, {
            key,
            label: feature.featureName ?? feature.featureKey ?? "—",
            cost: mb.cost ?? 0,
            revenue: mb.revenue ?? 0,
            margin: null,
          })
        }
      }
    }
    if (cost > 0 || revenue > 0) {
      customerRows.push({
        key: customer.customerId ?? "",
        label: customer.customerName || customer.email || customer.customerReferenceId || "—",
        cost,
        revenue,
        margin: marginOf(cost, revenue),
      })
    }
  }
  customerRows.sort((a, b) => b.cost - a.cost)
  const featureRows = [...featureTotals.values()]
    .map((row) => ({ ...row, margin: marginOf(row.cost, row.revenue) }))
    .sort((a, b) => b.cost - a.cost)

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="overflow-y-auto data-[side=right]:sm:max-w-xl">
        <SheetHeader>
          <SheetTitle className="font-mono">{modelName ?? "Unattributed"}</SheetTitle>
          <SheetDescription>Model cost and margin breakdown.</SheetDescription>
        </SheetHeader>
        <div className="flex flex-col gap-6 px-4 pb-4">
          <div className="grid grid-cols-3 gap-3">
            <Kpi label="Cost" value={formatCurrency(model?.totalCost ?? 0)} />
            <Kpi label="Revenue" value={formatCurrency(model?.totalRevenue ?? 0)} />
            <Kpi
              label="Margin"
              value={marginText(model?.margin)}
              negative={(model?.margin ?? 0) < 0}
            />
          </div>
          <Tabs defaultValue="customer">
            <TabsList className="w-full">
              <TabsTrigger value="customer" className="flex-1">
                By customer ({customerRows.length})
              </TabsTrigger>
              <TabsTrigger value="feature" className="flex-1">
                By feature ({featureRows.length})
              </TabsTrigger>
            </TabsList>
            <TabsContent value="customer" className="mt-3">
              <BreakdownList rows={customerRows} labelHeader="Customer" />
            </TabsContent>
            <TabsContent value="feature" className="mt-3">
              <BreakdownList rows={featureRows} labelHeader="Feature" />
            </TabsContent>
          </Tabs>
          {modelName && (
            <Button variant="outline" size="sm" nativeButton={false} render={<Link href={`/events?model=${encodeURIComponent(modelName)}`} />}>
              View events for this model
            </Button>
          )}
        </div>
      </SheetContent>
    </Sheet>
  )
}
