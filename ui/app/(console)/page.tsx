"use client"

import { useState } from "react"
import { isModuleOff } from "@/lib/api/client"
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts"

import { Badge } from "@/components/ui/badge"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { formatCurrency, formatNumber } from "@/lib/format"
import { cn } from "@/lib/utils"
import type { CustomerAnalyticsDto, ModelSummaryDto } from "@/lib/api/types"
import { CustomerDetailSheet, ModelDetailSheet } from "@/features/analytics/detail-sheets"
import { BuildSpendCard } from "@/features/analytics/build-spend-card"
import { GettingStartedCard } from "@/features/analytics/getting-started-card"
import { useModelsAnalytics, usePortfolio, useRevenueBridge } from "@/features/analytics/queries"

const bridgeChartConfig = {
  baseRevenue: { label: "Base", color: "var(--chart-1)" },
  usageRevenue: { label: "Usage", color: "var(--chart-3)" },
} satisfies ChartConfig

function churnBadgeVariant(risk: string | undefined) {
  if (risk === "CRITICAL" || risk === "HIGH") return "destructive" as const
  return "secondary" as const
}

export default function OverviewPage() {
  const portfolio = usePortfolio()
  const monetizationOff = isModuleOff(portfolio.error)
  const bridge = useRevenueBridge()
  const models = useModelsAnalytics()
  const [selectedCustomer, setSelectedCustomer] = useState<CustomerAnalyticsDto | null>(null)
  const [customerOpen, setCustomerOpen] = useState(false)
  const [selectedModel, setSelectedModel] = useState<ModelSummaryDto | null>(null)
  const [modelOpen, setModelOpen] = useState(false)

  const summary = portfolio.data?.summary
  const customers = [...(portfolio.data?.customers ?? [])].sort(
    (a, b) => (b.mrr ?? 0) - (a.mrr ?? 0),
  )

  const bridgeData = (bridge.data?.periods ?? []).map((p) => ({
    ...p,
    label: p.periodStart
      ? new Intl.DateTimeFormat("en-US", { month: "short" }).format(new Date(p.periodStart))
      : "",
  }))

  if (monetizationOff) {
    return (
      <>
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Overview</h1>
          <p className="text-sm text-muted-foreground">
            What your AI costs. Monetization is switched off on this install.
          </p>
        </div>
        <BuildSpendCard />
      </>
    )
  }

  if (portfolio.isPending) {
    return (
      <div className="flex flex-col gap-4">
        <div className="grid gap-4 md:grid-cols-4">
          <Skeleton className="h-28" />
          <Skeleton className="h-28" />
          <Skeleton className="h-28" />
          <Skeleton className="h-28" />
        </div>
        <Skeleton className="h-72 w-full" />
      </div>
    )
  }

  return (
    <>
      <div>
        <h1 className="text-xl font-semibold tracking-tight">Overview</h1>
        <p className="text-sm text-muted-foreground">
          What your AI costs to build, and what it earns when you sell it.
        </p>
      </div>

      <BuildSpendCard />

      <GettingStartedCard />

      <div className="grid gap-4 md:grid-cols-4">
        <Card>
          <CardHeader>
            <CardDescription>MRR</CardDescription>
            <CardTitle className="font-mono text-2xl tabular-nums">
              {formatCurrency(summary?.totalMrr)}
            </CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">
            {summary?.totalEffectiveMrr != null
              ? `${formatCurrency(summary.totalEffectiveMrr)} effective with usage`
              : "Effective MRR appears with usage"}
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Costs</CardDescription>
            <CardTitle className="font-mono text-2xl tabular-nums">
              {formatCurrency(summary?.totalCosts)}
            </CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">
            Inference and provider costs
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Avg margin</CardDescription>
            <CardTitle
              className={cn(
                "font-mono text-2xl tabular-nums",
                (summary?.avgMargin ?? 0) < 0 && "text-destructive",
              )}
            >
              {summary?.avgMargin != null ? `${(summary.avgMargin * 100).toFixed(1)}%` : "—"}
            </CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">
            Across paying customers
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Churn risk</CardDescription>
            <CardTitle className="font-mono text-2xl tabular-nums">
              {formatNumber(summary?.criticalChurnCount)}
            </CardTitle>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">
            {summary?.highRiskMrr != null
              ? `Critical accounts · ${formatCurrency(summary.highRiskMrr)} MRR at risk`
              : "Critical accounts by churn score"}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Revenue bridge</CardTitle>
          <CardDescription>Base subscription vs usage revenue per period.</CardDescription>
        </CardHeader>
        <CardContent>
          {bridgeData.length === 0 ? (
            <Empty>
              <EmptyHeader>
                <EmptyTitle>No revenue history yet</EmptyTitle>
                <EmptyDescription>
                  Periods appear here once billing cycles close.
                </EmptyDescription>
              </EmptyHeader>
            </Empty>
          ) : (
            <ChartContainer config={bridgeChartConfig} className="h-72 w-full">
              <BarChart data={bridgeData}>
                <CartesianGrid vertical={false} strokeOpacity={0.3} />
                <XAxis dataKey="label" tickLine={false} axisLine={false} />
                <YAxis tickLine={false} axisLine={false} width={64} />
                <ChartTooltip content={<ChartTooltipContent />} />
                <ChartLegend content={<ChartLegendContent />} />
                <Bar dataKey="baseRevenue" stackId="revenue" fill="var(--color-baseRevenue)" />
                <Bar
                  dataKey="usageRevenue"
                  stackId="revenue"
                  fill="var(--color-usageRevenue)"
                  radius={[4, 4, 0, 0]}
                />
              </BarChart>
            </ChartContainer>
          )}
        </CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Margin by customer</CardTitle>
            <CardDescription>Sorted by MRR.</CardDescription>
          </CardHeader>
          <CardContent>
            {customers.length === 0 ? (
              <Empty>
                <EmptyHeader>
                  <EmptyTitle>No customers yet</EmptyTitle>
                </EmptyHeader>
              </Empty>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Customer</TableHead>
                    <TableHead>Plan</TableHead>
                    <TableHead className="text-right">MRR</TableHead>
                    <TableHead className="text-right">Cost</TableHead>
                    <TableHead className="text-right">Margin</TableHead>
                    <TableHead>Churn</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {customers.slice(0, 10).map((c) => (
                    <TableRow
                      key={c.customerId}
                      className="cursor-pointer"
                      onClick={() => {
                        setSelectedCustomer(c)
                        setCustomerOpen(true)
                      }}
                    >
                      <TableCell className="max-w-40 truncate">
                        {c.customerName || c.email || c.customerReferenceId}
                      </TableCell>
                      <TableCell className="text-muted-foreground">{c.planName ?? "—"}</TableCell>
                      <TableCell className="text-right font-mono tabular-nums">
                        {formatCurrency(c.mrr)}
                      </TableCell>
                      <TableCell className="text-right font-mono tabular-nums">
                        {formatCurrency(c.totalCost)}
                      </TableCell>
                      <TableCell
                        className={cn(
                          "text-right font-mono tabular-nums",
                          (c.margin ?? 0) < 0 && "text-destructive",
                        )}
                      >
                        {c.margin != null ? `${(c.margin * 100).toFixed(1)}%` : "—"}
                      </TableCell>
                      <TableCell>
                        <Badge variant={churnBadgeVariant(c.churnRisk)}>{c.churnRisk ?? "—"}</Badge>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Models</CardTitle>
            <CardDescription>
              {formatNumber(models.data?.totalEvents)} events ·{" "}
              {formatCurrency(models.data?.totalCost)} cost ·{" "}
              {formatCurrency(models.data?.totalRevenue)} revenue
            </CardDescription>
          </CardHeader>
          <CardContent>
            {(models.data?.models ?? []).length === 0 ? (
              <Empty>
                <EmptyHeader>
                  <EmptyTitle>No model usage yet</EmptyTitle>
                  <EmptyDescription>
                    Ingest events with model metadata to see per-model margins.
                  </EmptyDescription>
                </EmptyHeader>
              </Empty>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Model</TableHead>
                    <TableHead className="text-right">Events</TableHead>
                    <TableHead className="text-right">Cost</TableHead>
                    <TableHead className="text-right">Revenue</TableHead>
                    <TableHead className="text-right">Margin</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(models.data?.models ?? []).slice(0, 10).map((m) => (
                    <TableRow
                      key={`${m.modelProvider}-${m.model}`}
                      className="cursor-pointer"
                      onClick={() => {
                        setSelectedModel(m)
                        setModelOpen(true)
                      }}
                    >
                      <TableCell>
                        <span className="font-mono text-xs">{m.model}</span>{" "}
                        <span className="text-xs text-muted-foreground">{m.modelProvider}</span>
                      </TableCell>
                      <TableCell className="text-right font-mono tabular-nums">
                        {formatNumber(m.eventCount)}
                      </TableCell>
                      <TableCell className="text-right font-mono tabular-nums">
                        {formatCurrency(m.totalCost)}
                      </TableCell>
                      <TableCell className="text-right font-mono tabular-nums">
                        {formatCurrency(m.totalRevenue)}
                      </TableCell>
                      <TableCell
                        className={cn(
                          "text-right font-mono tabular-nums",
                          (m.margin ?? 0) < 0 && "text-destructive",
                        )}
                      >
                        {m.margin != null ? `${(m.margin * 100).toFixed(1)}%` : "—"}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      </div>

      <CustomerDetailSheet
        customer={selectedCustomer}
        open={customerOpen}
        onOpenChange={setCustomerOpen}
      />
      <ModelDetailSheet
        model={selectedModel}
        customers={portfolio.data?.customers ?? []}
        open={modelOpen}
        onOpenChange={setModelOpen}
      />
    </>
  )
}
