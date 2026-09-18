"use client"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useAgentFunnel } from "@/features/agent-funnel/queries"
import { useSpendRange } from "@/features/spend/range-context"
import { isModuleOff } from "@/lib/api/client"
import { formatNumber } from "@/lib/format"

function formatRate(rate: number | undefined): string {
  if (rate === undefined) return "—"
  return `${(rate * 100).toFixed(1)}%`
}

function formatHours(hours: number | null | undefined): string {
  if (hours === null || hours === undefined) return "—"
  return `${hours.toFixed(1)} h`
}

export default function AgentFunnelPage() {
  const range = useSpendRange()
  const report = useAgentFunnel(range.from, range.to)
  const data = report.data

  const stages = data
    ? [
        { label: "Signups", count: data.stages.signups },
        { label: "First verified job", count: data.stages.first_verified_job },
        { label: "Claimed", count: data.stages.claimed },
        { label: "Paid", count: data.stages.paid },
      ]
    : []
  const signups = data?.stages.signups ?? 0

  return (
    <>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Agent funnel</h1>
          <p className="text-sm text-muted-foreground">
            Agents that self-signed up in this window (customer ids starting
            with agent_), and how many sent a first usage event, claimed the
            account by paying, and paid.
          </p>
        </div>
      </div>

      {isModuleOff(report.error) ? (
        <p className="text-sm text-muted-foreground">
          Monetization is switched off on this install
          (APP_MODULES_MONETIZATION_ENABLED=false).
        </p>
      ) : report.error ? (
        <Alert variant="destructive">
          <AlertTitle>Could not load agent funnel</AlertTitle>
          <AlertDescription>{report.error.message}</AlertDescription>
        </Alert>
      ) : (
        <>
          <Card>
            <CardHeader>
              <CardTitle>Stages</CardTitle>
              <CardDescription>
                Each bar is a share of signups in the window.
              </CardDescription>
            </CardHeader>
            <CardContent className="grid gap-3">
              {stages.map((stage) => (
                <div key={stage.label} className="grid gap-1">
                  <div className="flex items-baseline justify-between text-sm">
                    <span>{stage.label}</span>
                    <span className="tabular-nums">
                      {formatNumber(stage.count)}
                    </span>
                  </div>
                  <div className="h-2 w-full rounded bg-muted">
                    <div
                      className="h-2 rounded bg-primary"
                      style={{
                        width: `${signups === 0 ? 0 : (stage.count / signups) * 100}%`,
                      }}
                    />
                  </div>
                </div>
              ))}
              {data && signups === 0 && (
                <p className="text-sm text-muted-foreground">
                  No agent signups in this window.
                </p>
              )}
            </CardContent>
          </Card>

          <div className="grid gap-4 md:grid-cols-3">
            <Card>
              <CardHeader>
                <CardDescription>Activation rate</CardDescription>
                <CardTitle className="text-2xl tabular-nums">
                  {formatRate(data?.rates.activation)}
                </CardTitle>
                <CardDescription>signups with a first usage event</CardDescription>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader>
                <CardDescription>Claim rate</CardDescription>
                <CardTitle className="text-2xl tabular-nums">
                  {formatRate(data?.rates.claim)}
                </CardTitle>
                <CardDescription>signups that paid and were claimed</CardDescription>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader>
                <CardDescription>Paid rate</CardDescription>
                <CardTitle className="text-2xl tabular-nums">
                  {formatRate(data?.rates.paid)}
                </CardTitle>
                <CardDescription>
                  signups with a paid invoice or completed checkout
                </CardDescription>
              </CardHeader>
            </Card>
          </div>

          <div className="grid gap-4 md:grid-cols-3">
            <Card>
              <CardHeader>
                <CardDescription>Median signup to first job</CardDescription>
                <CardTitle className="text-2xl tabular-nums">
                  {formatHours(data?.median_hours_signup_to_first_job)}
                </CardTitle>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader>
                <CardDescription>Median signup to paid</CardDescription>
                <CardTitle className="text-2xl tabular-nums">
                  {formatHours(data?.median_hours_signup_to_paid)}
                </CardTitle>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader>
                <CardDescription>Expired</CardDescription>
                <CardTitle className="text-2xl tabular-nums">
                  {data ? formatNumber(data.expired) : "—"}
                </CardTitle>
                <CardDescription>
                  provisional accounts that ran out before paying
                </CardDescription>
              </CardHeader>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>By signup day</CardTitle>
              <CardDescription>
                Each row is the cohort that signed up that day (UTC) and where
                it is now.
              </CardDescription>
            </CardHeader>
            <CardContent className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Date</TableHead>
                    <TableHead className="text-right">Signups</TableHead>
                    <TableHead className="text-right">First verified job</TableHead>
                    <TableHead className="text-right">Claimed</TableHead>
                    <TableHead className="text-right">Paid</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(data?.by_day ?? []).map((row) => (
                    <TableRow key={row.date}>
                      <TableCell className="tabular-nums">{row.date}</TableCell>
                      <TableCell className="text-right tabular-nums">
                        {formatNumber(row.signups)}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        {formatNumber(row.first_verified_job)}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        {formatNumber(row.claimed)}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        {formatNumber(row.paid)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </>
      )}
    </>
  )
}
