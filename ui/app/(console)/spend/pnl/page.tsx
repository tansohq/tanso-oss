"use client"

import { useState } from "react"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { daysAgo, formatCents } from "@/features/spend/format"
import { isBuildSideOff, useSpendPnl } from "@/features/spend/queries"

export default function SpendPnlPage() {
  const [range, setRange] = useState({ from: daysAgo(29), to: daysAgo(-1) })
  const [draft, setDraft] = useState(range)
  const commit = () => {
    if (
      /^\d{4}-\d{2}-\d{2}$/.test(draft.from) &&
      /^\d{4}-\d{2}-\d{2}$/.test(draft.to) &&
      draft.from < draft.to
    ) {
      setRange(draft)
    }
  }
  const report = useSpendPnl(range.from, range.to)
  const data = report.data

  if (isBuildSideOff(report.error)) {
    return (
      <p className="text-sm text-muted-foreground">
        The build side is switched off on this install
        (APP_MODULES_BUILD_ENABLED=false) — or the console&apos;s API base URL
        does not reach it.
      </p>
    )
  }

  const signed = (cents: number) =>
    `${cents < 0 ? "−" : ""}${formatCents(Math.abs(cents))}`

  return (
    <>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Feature P&L</h1>
          <p className="text-sm text-muted-foreground">
            What a project cost to build next to what the feature it shipped
            earns from customers, in the same window. Link a project to its
            feature under Teams.
          </p>
        </div>
        <div className="flex items-end gap-2">
          <div className="grid gap-1">
            <Label htmlFor="pnl-from">From</Label>
            <Input
              id="pnl-from"
              type="date"
              value={draft.from}
              onChange={(e) => setDraft({ ...draft, from: e.target.value })}
              onBlur={commit}
              onKeyDown={(e) => e.key === "Enter" && commit()}
            />
          </div>
          <div className="grid gap-1">
            <Label htmlFor="pnl-to">To (exclusive)</Label>
            <Input
              id="pnl-to"
              type="date"
              value={draft.to}
              onChange={(e) => setDraft({ ...draft, to: e.target.value })}
              onBlur={commit}
              onKeyDown={(e) => e.key === "Enter" && commit()}
            />
          </div>
        </div>
      </div>

      {report.error && !isBuildSideOff(report.error) && (
        <Alert variant="destructive">
          <AlertTitle>Could not load the P&L</AlertTitle>
          <AlertDescription>{report.error.message}</AlertDescription>
        </Alert>
      )}

      <div className="grid gap-4 md:grid-cols-4">
        <Card>
          <CardHeader>
            <CardDescription>Build</CardDescription>
            <CardTitle className="text-2xl tabular-nums">
              {formatCents(data?.totalBuildCents)}
            </CardTitle>
          </CardHeader>
          <CardContent className="text-xs text-muted-foreground">
            AI spend attributed to linked projects.
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Revenue</CardDescription>
            <CardTitle className="text-2xl tabular-nums">
              {formatCents(data?.totalRevenueCents)}
            </CardTitle>
          </CardHeader>
          <CardContent className="text-xs text-muted-foreground">
            From the linked features&apos; customer events.
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Serving cost</CardDescription>
            <CardTitle className="text-2xl tabular-nums">
              {formatCents(data?.totalServeCostCents)}
            </CardTitle>
          </CardHeader>
          <CardContent className="text-xs text-muted-foreground">
            What those events cost to serve.
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Net</CardDescription>
            <CardTitle className="text-2xl tabular-nums">
              {data ? signed(data.totalNetCents) : "—"}
            </CardTitle>
          </CardHeader>
          <CardContent className="text-xs text-muted-foreground">
            Revenue − serving cost − build.
          </CardContent>
        </Card>
      </div>

      {data && data.unlinkedProjects.length > 0 && (
        <Alert>
          <AlertTitle>
            {data.unlinkedProjects.length} project
            {data.unlinkedProjects.length === 1 ? "" : "s"} without a feature
          </AlertTitle>
          <AlertDescription>
            {data.unlinkedProjects.join(", ")} — their build cost is not in
            these totals. Link each to the feature it shipped under Teams.
          </AlertDescription>
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>By project</CardTitle>
          <CardDescription>
            Serve margin is revenue minus serving cost; net takes the build cost
            off it.
          </CardDescription>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Project</TableHead>
                <TableHead>Feature</TableHead>
                <TableHead className="text-right">Build</TableHead>
                <TableHead className="text-right">Outcomes</TableHead>
                <TableHead className="text-right">Build / outcome</TableHead>
                <TableHead className="text-right">Revenue</TableHead>
                <TableHead className="text-right">Serving</TableHead>
                <TableHead className="text-right">Serve margin</TableHead>
                <TableHead className="text-right">Net</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data?.rows ?? []).map((r) => (
                <TableRow key={r.unitId}>
                  <TableCell>{r.name}</TableCell>
                  <TableCell>
                    {r.featureName ?? "—"}
                    {r.featureKey && (
                      <span className="ml-2 font-mono text-xs text-muted-foreground">
                        {r.featureKey}
                      </span>
                    )}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatCents(r.buildCents)}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {r.outcomes}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {r.buildPerOutcomeCents != null
                      ? formatCents(r.buildPerOutcomeCents)
                      : "—"}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatCents(r.revenueCents)}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatCents(r.serveCostCents)}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {signed(r.serveMarginCents)}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {signed(r.netCents)}
                  </TableCell>
                </TableRow>
              ))}
              {data && data.rows.length === 0 && (
                <TableRow>
                  <TableCell
                    colSpan={9}
                    className="text-center text-muted-foreground"
                  >
                    No project is linked to a feature yet. Under Teams, open a
                    project and pick the feature it shipped.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </>
  )
}
