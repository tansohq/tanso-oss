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
import {
  daysAgo,
  formatCents,
  formatTokens,
  providerLabel,
} from "@/features/spend/format"
import {
  isBuildSideOff,
  useSpendSettings,
  useSpendUsage,
} from "@/features/spend/queries"
import { formatNumber } from "@/lib/format"

export default function SpendUsagePage() {
  const [range, setRange] = useState({ from: daysAgo(29), to: daysAgo(-1) })
  const [draft, setDraft] = useState(range)
  // Date inputs emit every partial value while typing a year; only a complete,
  // ordered range reaches the query key.
  const commit = () => {
    if (
      /^\d{4}-\d{2}-\d{2}$/.test(draft.from) &&
      /^\d{4}-\d{2}-\d{2}$/.test(draft.to) &&
      draft.from < draft.to
    ) {
      setRange(draft)
    }
  }
  const report = useSpendUsage(range.from, range.to)
  const settings = useSpendSettings()
  const data = report.data

  return (
    <>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">
            Internal AI usage
          </h1>
          <p className="text-sm text-muted-foreground">
            What your org sent to each vendor, and what it cost — by the price
            book and by the vendor&apos;s own report.
          </p>
        </div>
        <div className="flex items-end gap-2">
          <div className="grid gap-1">
            <Label htmlFor="usage-from">From</Label>
            <Input
              id="usage-from"
              type="date"
              value={draft.from}
              onChange={(e) => setDraft({ ...draft, from: e.target.value })}
              onBlur={commit}
              onKeyDown={(e) => e.key === "Enter" && commit()}
            />
          </div>
          <div className="grid gap-1">
            <Label htmlFor="usage-to">To (exclusive)</Label>
            <Input
              id="usage-to"
              type="date"
              value={draft.to}
              onChange={(e) => setDraft({ ...draft, to: e.target.value })}
              onBlur={commit}
              onKeyDown={(e) => e.key === "Enter" && commit()}
            />
          </div>
        </div>
      </div>

      {isBuildSideOff(report.error) ? (
        <p className="text-sm text-muted-foreground">
          The build side is switched off on this install
          (APP_MODULES_BUILD_ENABLED=false).
        </p>
      ) : report.error ? (
        <Alert variant="destructive">
          <AlertTitle>Could not load usage</AlertTitle>
          <AlertDescription>{report.error.message}</AlertDescription>
        </Alert>
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-4">
            <Card>
              <CardHeader>
                <CardDescription>Vendor-reported cost</CardDescription>
                <CardTitle className="text-2xl tabular-nums">
                  {formatCents(data?.totals.vendorCostCents)}
                </CardTitle>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader>
                <CardDescription>Metered by price book</CardDescription>
                <CardTitle className="text-2xl tabular-nums">
                  {formatCents(data?.totals.meteredCostCents)}
                </CardTitle>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader>
                <CardDescription>Tokens</CardDescription>
                <CardTitle className="text-2xl tabular-nums">
                  {data
                    ? formatTokens(
                        data.totals.uncachedInputTokens +
                          data.totals.cacheReadTokens +
                          data.totals.cacheCreationTokens +
                          data.totals.outputTokens
                      )
                    : "—"}
                </CardTitle>
              </CardHeader>
            </Card>
            <Card>
              <CardHeader>
                <CardDescription>Requests</CardDescription>
                <CardTitle className="text-2xl tabular-nums">
                  {data?.totals.requests != null
                    ? formatNumber(data.totals.requests)
                    : "—"}
                </CardTitle>
              </CardHeader>
            </Card>
          </div>

          {data && data.unpricedModels.length > 0 && (
            <Alert>
              <AlertTitle>Models the price book does not know</AlertTitle>
              <AlertDescription>
                {data.unpricedModels.join(", ")} — their metered cost is counted
                as zero. Add them to model pricing to close the gap.
              </AlertDescription>
            </Alert>
          )}

          <Card>
            <CardHeader>
              <CardTitle>By model</CardTitle>
            </CardHeader>
            <CardContent className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Provider</TableHead>
                    <TableHead>Model</TableHead>
                    <TableHead className="text-right">Input</TableHead>
                    <TableHead className="text-right">Cache read</TableHead>
                    <TableHead className="text-right">Cache write</TableHead>
                    <TableHead className="text-right">Output</TableHead>
                    <TableHead className="text-right">Requests</TableHead>
                    <TableHead className="text-right">Metered</TableHead>
                    <TableHead className="text-right">Vendor</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(data?.byModel ?? []).map((row) => (
                    <TableRow key={`${row.provider}-${row.model}`}>
                      <TableCell>{providerLabel[row.provider]}</TableCell>
                      <TableCell className="font-mono text-xs">
                        {row.model ?? "—"}
                        {!row.priced && (
                          <span className="ml-2 text-muted-foreground">
                            (unpriced)
                          </span>
                        )}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        {formatTokens(row.uncachedInputTokens)}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        {formatTokens(row.cacheReadTokens)}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        {formatTokens(row.cacheCreationTokens)}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        {formatTokens(row.outputTokens)}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        {row.requests != null
                          ? formatNumber(row.requests)
                          : "—"}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        {formatCents(row.meteredCostCents)}
                      </TableCell>
                      <TableCell className="text-right tabular-nums">
                        {formatCents(row.vendorCostCents)}
                      </TableCell>
                    </TableRow>
                  ))}
                  {data && data.byModel.length === 0 && (
                    <TableRow>
                      <TableCell
                        colSpan={9}
                        className="text-center text-muted-foreground"
                      >
                        Nothing pulled for this window yet. Connect a vendor and
                        sync.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>

          <div className="grid gap-4 lg:grid-cols-2">
            {settings.data?.personLevelEnabled !== false && (
              <Card>
                <CardHeader>
                  <CardTitle>By person</CardTitle>
                  <CardDescription>
                    Claude Code actors, OpenAI users, Cursor and Copilot seats.
                    Tokens here are already inside the totals above; "Sessions"
                    is events for Cursor and CLI sessions for Copilot.
                  </CardDescription>
                </CardHeader>
                <CardContent className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Actor</TableHead>
                        <TableHead className="text-right">Tokens</TableHead>
                        <TableHead className="text-right">Sessions</TableHead>
                        <TableHead
                          className="text-right"
                          title="Requests or interactions the person made through the assistant"
                        >
                          Requests
                        </TableHead>
                        <TableHead
                          className="text-right"
                          title="Accepted / rejected suggestions"
                        >
                          Acc / rej
                        </TableHead>
                        <TableHead
                          className="text-right"
                          title="Lines added by the assistant"
                        >
                          Lines
                        </TableHead>
                        <TableHead
                          className="text-right"
                          title="Commits / pull requests made through the assistant"
                        >
                          Commits / PRs
                        </TableHead>
                        <TableHead
                          className="text-right"
                          title="Copilot premium-request credits"
                        >
                          Credits
                        </TableHead>
                        <TableHead className="text-right">Metered</TableHead>
                        <TableHead className="text-right">
                          Vendor est.
                        </TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {(data?.byActor ?? []).map((row) => (
                        <TableRow key={`${row.provider}-${row.actor}`}>
                          <TableCell>
                            {row.actor}
                            <span className="ml-2 text-xs text-muted-foreground">
                              {providerLabel[row.provider]}
                            </span>
                          </TableCell>
                          <TableCell className="text-right tabular-nums">
                            {formatTokens(row.totalTokens)}
                          </TableCell>
                          <TableCell className="text-right tabular-nums">
                            {formatNumber(row.sessions)}
                          </TableCell>
                          <TableCell className="text-right tabular-nums">
                            {row.requests != null
                              ? formatNumber(row.requests)
                              : "—"}
                          </TableCell>
                          <TableCell className="text-right tabular-nums">
                            {row.accepted != null
                              ? `${formatNumber(row.accepted)} / ${row.rejected != null ? formatNumber(row.rejected) : "—"}`
                              : "—"}
                          </TableCell>
                          <TableCell className="text-right tabular-nums">
                            {row.linesAdded != null
                              ? `+${formatNumber(row.linesAdded)}`
                              : "—"}
                            {row.linesRemoved != null &&
                              ` −${formatNumber(row.linesRemoved)}`}
                          </TableCell>
                          <TableCell className="text-right tabular-nums">
                            {row.commits != null || row.pullRequests != null
                              ? `${row.commits ?? "—"} / ${row.pullRequests ?? "—"}`
                              : "—"}
                          </TableCell>
                          <TableCell className="text-right tabular-nums">
                            {row.creditsUsed != null ? row.creditsUsed : "—"}
                          </TableCell>
                          <TableCell className="text-right tabular-nums">
                            {formatCents(row.meteredCostCents)}
                          </TableCell>
                          <TableCell className="text-right tabular-nums">
                            {formatCents(row.vendorCostCents)}
                          </TableCell>
                        </TableRow>
                      ))}
                      {data && data.byActor.length === 0 && (
                        <TableRow>
                          <TableCell
                            colSpan={10}
                            className="text-center text-muted-foreground"
                          >
                            No per-person data. Anthropic reports people only
                            for Claude Code; OpenAI only for user-scoped keys;
                            Cursor and Copilot per seat.
                          </TableCell>
                        </TableRow>
                      )}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
            )}
            <Card>
              <CardHeader>
                <CardTitle>By day</CardTitle>
              </CardHeader>
              <CardContent className="overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Date</TableHead>
                      <TableHead className="text-right">Tokens</TableHead>
                      <TableHead className="text-right">Metered</TableHead>
                      <TableHead className="text-right">Vendor</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {(data?.byDay ?? []).map((row) => (
                      <TableRow key={row.date}>
                        <TableCell className="tabular-nums">
                          {row.date}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          {formatTokens(row.totalTokens)}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          {formatCents(row.meteredCostCents)}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          {formatCents(row.vendorCostCents)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          </div>
        </>
      )}
    </>
  )
}
