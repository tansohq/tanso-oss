"use client"

import { Suspense, useState } from "react"
import { useSearchParams } from "next/navigation"
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
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
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { formatCurrency, formatDateTime, formatNumber } from "@/lib/format"
import {
  useEvents,
  useGroupedEvents,
  type EventFilters,
  type EventGroupBy,
} from "@/features/events/queries"
import { useCustomers } from "@/features/customers/queries"
import { useFeatures } from "@/features/features/queries"

const groupChartConfig = {
  totalRevenue: { label: "Revenue", color: "var(--chart-1)" },
  totalCost: { label: "Cost", color: "var(--chart-3)" },
} satisfies ChartConfig

const groupByOptions: { label: string; value: EventGroupBy }[] = [
  { label: "Model", value: "MODEL" },
  { label: "Provider", value: "MODEL_PROVIDER" },
  { label: "Customer", value: "CUSTOMER" },
  { label: "Feature", value: "FEATURE" },
  { label: "Event name", value: "EVENT_NAME" },
]

function EventsTable() {
  // The Overview detail sheets link here pre-filtered (?customerReferenceId=…, ?model=…).
  const searchParams = useSearchParams()
  const [draft, setDraft] = useState({
    eventName: searchParams.get("eventName") ?? "",
    customerReferenceId: searchParams.get("customerReferenceId") ?? "",
    model: searchParams.get("model") ?? "",
  })
  const [filters, setFilters] = useState<EventFilters>({
    page: 0,
    size: 25,
    eventName: searchParams.get("eventName") ?? undefined,
    customerReferenceId: searchParams.get("customerReferenceId") ?? undefined,
    model: searchParams.get("model") ?? undefined,
  })
  const events = useEvents(filters)
  const customers = useCustomers()
  const features = useFeatures()

  // Ingestion resolves reference strings to IDs and drops them from the
  // stored event, so display names come from the cached catalog lists.
  const customerById = new Map(
    (customers.data?.customers ?? []).map((c) => [c.id, c.referenceId ?? c.email]),
  )
  const featureById = new Map((features.data ?? []).map((f) => [f.id, f.key]))

  const items = events.data?.items ?? []
  const totalPages = events.data?.totalPages ?? 0
  const page = events.data?.page ?? filters.page

  function applyFilters() {
    setFilters({
      page: 0,
      size: filters.size,
      eventName: draft.eventName || undefined,
      customerReferenceId: draft.customerReferenceId || undefined,
      model: draft.model || undefined,
    })
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end gap-2">
        <Input
          aria-label="Filter by event name"
          placeholder="Event name"
          className="w-44"
          value={draft.eventName}
          onChange={(e) => setDraft({ ...draft, eventName: e.target.value })}
        />
        <Input
          aria-label="Filter by customer reference"
          placeholder="Customer reference"
          className="w-44"
          value={draft.customerReferenceId}
          onChange={(e) => setDraft({ ...draft, customerReferenceId: e.target.value })}
        />
        <Input
          aria-label="Filter by model"
          placeholder="Model"
          className="w-44"
          value={draft.model}
          onChange={(e) => setDraft({ ...draft, model: e.target.value })}
        />
        <Button variant="outline" onClick={applyFilters}>
          Apply
        </Button>
      </div>

      {events.isPending ? (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-9 w-full" />
          <Skeleton className="h-9 w-full" />
          <Skeleton className="h-9 w-full" />
        </div>
      ) : items.length === 0 ? (
        <Empty>
          <EmptyHeader>
            <EmptyTitle>No events</EmptyTitle>
            <EmptyDescription>
              Ingest usage events via POST /api/v1/client/events to see them here.
            </EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Occurred</TableHead>
                  <TableHead>Event</TableHead>
                  <TableHead>Customer</TableHead>
                  <TableHead>Feature</TableHead>
                  <TableHead>Model</TableHead>
                  <TableHead className="text-right">Units</TableHead>
                  <TableHead className="text-right">Cost</TableHead>
                  <TableHead className="text-right">Revenue</TableHead>
                  <TableHead>Type</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((event) => (
                  <TableRow key={event.id}>
                    <TableCell className="whitespace-nowrap">
                      {formatDateTime(event.occurredAt)}
                    </TableCell>
                    <TableCell>{event.eventName}</TableCell>
                    <TableCell className="font-mono text-xs">
                      {event.customerReferenceId ?? customerById.get(event.customerId ?? "") ?? "—"}
                    </TableCell>
                    <TableCell className="font-mono text-xs">
                      {event.featureKey ?? featureById.get(event.featureId ?? "") ?? "—"}
                    </TableCell>
                    <TableCell className="font-mono text-xs">{event.model ?? "—"}</TableCell>
                    <TableCell className="text-right font-mono tabular-nums">
                      {formatNumber(event.usageUnits)}
                    </TableCell>
                    <TableCell className="text-right font-mono tabular-nums">
                      {formatCurrency(event.costAmount)}
                    </TableCell>
                    <TableCell className="text-right font-mono tabular-nums">
                      {formatCurrency(event.revenueAmount)}
                    </TableCell>
                    <TableCell>
                      <Badge variant="secondary">{event.eventType}</Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-sm text-muted-foreground">
              Page {page + 1} of {Math.max(totalPages, 1)} ·{" "}
              {formatNumber(events.data?.totalElements)} events
            </span>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={page <= 0}
                onClick={() => setFilters({ ...filters, page: page - 1 })}
              >
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={page + 1 >= totalPages}
                onClick={() => setFilters({ ...filters, page: page + 1 })}
              >
                Next
              </Button>
            </div>
          </div>
        </>
      )}
    </div>
  )
}

function GroupedEvents() {
  const [groupBy, setGroupBy] = useState<EventGroupBy>("MODEL")
  const grouped = useGroupedEvents(groupBy)
  const groups = grouped.data ?? []

  return (
    <div className="flex flex-col gap-4">
      <ToggleGroup
        value={[groupBy]}
        onValueChange={(value: string[]) => {
          if (value[0]) setGroupBy(value[0] as EventGroupBy)
        }}
      >
        {groupByOptions.map((option) => (
          <ToggleGroupItem key={option.value} value={option.value}>
            {option.label}
          </ToggleGroupItem>
        ))}
      </ToggleGroup>

      {grouped.isPending ? (
        <Skeleton className="h-72 w-full" />
      ) : groups.length === 0 ? (
        <Empty>
          <EmptyHeader>
            <EmptyTitle>Nothing to group</EmptyTitle>
            <EmptyDescription>Ingest events to see cost vs revenue breakdowns.</EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <>
          <Card>
            <CardHeader>
              <CardTitle>Cost vs revenue</CardTitle>
              <CardDescription>By {groupBy.toLowerCase().replace("_", " ")}.</CardDescription>
            </CardHeader>
            <CardContent>
              <ChartContainer
                config={groupChartConfig}
                style={{ height: Math.max(groups.length * 56, 160) }}
                className="w-full"
              >
                <BarChart data={groups} layout="vertical">
                  <CartesianGrid horizontal={false} strokeOpacity={0.3} />
                  <XAxis type="number" tickLine={false} axisLine={false} />
                  <YAxis
                    type="category"
                    dataKey="groupLabel"
                    tickLine={false}
                    axisLine={false}
                    width={140}
                    tickFormatter={truncateTick}
                  />
                  <ChartTooltip content={<ChartTooltipContent />} />
                  <ChartLegend content={<ChartLegendContent />} />
                  <Bar dataKey="totalRevenue" fill="var(--color-totalRevenue)" radius={[0, 4, 4, 0]} />
                  <Bar dataKey="totalCost" fill="var(--color-totalCost)" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ChartContainer>
            </CardContent>
          </Card>
          <div className="overflow-x-auto rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Group</TableHead>
                  <TableHead className="text-right">Events</TableHead>
                  <TableHead className="text-right">Usage units</TableHead>
                  <TableHead className="text-right">Cost</TableHead>
                  <TableHead className="text-right">Revenue</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {groups.map((group) => (
                  <TableRow key={group.groupKey}>
                    <TableCell>{group.groupLabel}</TableCell>
                    <TableCell className="text-right font-mono tabular-nums">
                      {formatNumber(group.eventCount)}
                    </TableCell>
                    <TableCell className="text-right font-mono tabular-nums">
                      {formatNumber(group.totalUsageUnits)}
                    </TableCell>
                    <TableCell className="text-right font-mono tabular-nums">
                      {formatCurrency(group.totalCost)}
                    </TableCell>
                    <TableCell className="text-right font-mono tabular-nums">
                      {formatCurrency(group.totalRevenue)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </>
      )}
    </div>
  )
}

/**
 * Group labels are user data — model names, customer references, feature keys —
 * so they can be longer than the axis. Recharts does not clip them, it just
 * draws them past the edge of the chart, which turned
 * "claude-sonnet-4-20250514" into "ude-sonnet-4-20250514": a different model
 * name that reads as real. Truncating keeps the overflow visible as an
 * ellipsis; the tooltip still carries the full label.
 */
const TICK_MAX = 18
function truncateTick(value: string) {
  const label = String(value ?? "")
  return label.length > TICK_MAX ? `${label.slice(0, TICK_MAX - 1)}\u2026` : label
}


export default function EventsPage() {
  return (
    <>
      <div>
        <h1 className="text-xl font-semibold tracking-tight">Events</h1>
        <p className="text-sm text-muted-foreground">
          Every metered event with its cost and revenue.
        </p>
      </div>
      <Tabs defaultValue="stream">
        <TabsList>
          <TabsTrigger value="stream">Stream</TabsTrigger>
          <TabsTrigger value="grouped">Grouped</TabsTrigger>
        </TabsList>
        <TabsContent value="stream">
          <Suspense>
            <EventsTable />
          </Suspense>
        </TabsContent>
        <TabsContent value="grouped">
          <GroupedEvents />
        </TabsContent>
      </Tabs>
    </>
  )
}
