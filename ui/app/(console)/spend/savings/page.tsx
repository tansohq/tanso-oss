"use client"

import { useState } from "react"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
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
import { toast } from "@/components/ui/toast"
import {
  daysAgo,
  formatCents,
  formatTokens,
  providerLabel,
} from "@/features/spend/format"
import { useSimulateRoute } from "@/features/spend/mutations"
import {
  isBuildSideOff,
  usePriceBookModels,
  useSpendSavings,
} from "@/features/spend/queries"
import type { SpendRouteSimulationDto } from "@/features/spend/types"

export default function SpendSavingsPage() {
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
  const report = useSpendSavings(range.from, range.to)
  const models = usePriceBookModels()
  const simulate = useSimulateRoute()
  const [fromModel, setFromModel] = useState("")
  const [toModel, setToModel] = useState("")
  const [workspace, setWorkspace] = useState("")
  const [result, setResult] = useState<SpendRouteSimulationDto | null>(null)
  const data = report.data

  if (isBuildSideOff(report.error)) {
    return (
      <p className="text-sm text-muted-foreground">
        Internal spend is switched off on this install
        (APP_MODULES_BUILD_ENABLED=false).
      </p>
    )
  }

  return (
    <>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Savings</h1>
          <p className="text-sm text-muted-foreground">
            What prompt caching is worth, and what the bill would have been on
            another model. Advice from the price book — Tanso never routes.
          </p>
        </div>
        <div className="flex items-end gap-2">
          <div className="grid gap-1">
            <Label htmlFor="savings-from">From</Label>
            <Input
              id="savings-from"
              type="date"
              value={draft.from}
              onChange={(e) => setDraft({ ...draft, from: e.target.value })}
              onBlur={commit}
              onKeyDown={(e) => e.key === "Enter" && commit()}
            />
          </div>
          <div className="grid gap-1">
            <Label htmlFor="savings-to">To (exclusive)</Label>
            <Input
              id="savings-to"
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
          <AlertTitle>Could not load savings</AlertTitle>
          <AlertDescription>{report.error.message}</AlertDescription>
        </Alert>
      )}

      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader>
            <CardDescription>Saved by caching</CardDescription>
            <CardTitle className="text-2xl tabular-nums">
              {formatCents(data?.totals.savedCents)}
            </CardTitle>
          </CardHeader>
          <CardContent className="text-xs text-muted-foreground">
            Input side billed {formatCents(data?.totals.inputCostCents)} against{" "}
            {formatCents(data?.totals.noCacheCostCents)} with no cache.
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Cache-read share of input</CardDescription>
            <CardTitle className="text-2xl tabular-nums">
              {data ? `${Math.round(data.totals.cacheShare * 100)}%` : "—"}
            </CardTitle>
          </CardHeader>
          <CardContent className="text-xs text-muted-foreground">
            {formatTokens(data?.totals.cacheReadTokens)} read from cache of{" "}
            {formatTokens(
              data
                ? data.totals.uncachedInputTokens +
                    data.totals.cacheReadTokens +
                    data.totals.cacheCreationTokens
                : undefined
            )}{" "}
            input tokens.
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Cache writes</CardDescription>
            <CardTitle className="text-2xl tabular-nums">
              {formatTokens(data?.totals.cacheCreationTokens)}
            </CardTitle>
          </CardHeader>
          <CardContent className="text-xs text-muted-foreground">
            Writes cost more than plain input; a negative saving on a model
            means it wrote more than it read back.
          </CardContent>
        </Card>
      </div>

      {data && !data.totals.cacheRatesKnown && (
        <Alert>
          <AlertTitle>Some models have no cache rates</AlertTitle>
          <AlertDescription>
            Their cached tokens are priced at the input rate, so their saving
            reads as zero. Add cache_read / cache_write rates under model
            pricing.
          </AlertDescription>
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>By model</CardTitle>
          <CardDescription>
            Input-side cost as billed against the same tokens with no cache.
          </CardDescription>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Provider</TableHead>
                <TableHead>Model</TableHead>
                <TableHead className="text-right">Uncached</TableHead>
                <TableHead className="text-right">Cache read</TableHead>
                <TableHead className="text-right">Cache write</TableHead>
                <TableHead className="text-right">Cache share</TableHead>
                <TableHead className="text-right">Billed</TableHead>
                <TableHead className="text-right">No cache</TableHead>
                <TableHead className="text-right">Saved</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(data?.byModel ?? []).map((row) => (
                <TableRow key={`${row.provider}-${row.model}`}>
                  <TableCell>{providerLabel[row.provider ?? ""]}</TableCell>
                  <TableCell>
                    {row.model ?? "—"}
                    {!row.priced && (
                      <span className="ml-2 text-xs text-muted-foreground">
                        unpriced
                      </span>
                    )}
                    {row.priced && !row.cacheRatesKnown && (
                      <span className="ml-2 text-xs text-muted-foreground">
                        no cache rates
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
                    {Math.round(row.cacheShare * 100)}%
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatCents(row.inputCostCents)}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatCents(row.noCacheCostCents)}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatCents(row.savedCents)}
                  </TableCell>
                </TableRow>
              ))}
              {data && data.byModel.length === 0 && (
                <TableRow>
                  <TableCell
                    colSpan={9}
                    className="text-center text-muted-foreground"
                  >
                    No usage in this window. Sync a connection first.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Route simulator</CardTitle>
          <CardDescription>
            What if this window&apos;s traffic on one model had gone to another:
            the same tokens at the other model&apos;s rates. It does not know
            whether the answers would have been as good.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <form
            className="grid gap-3 md:grid-cols-4"
            onSubmit={(event) => {
              event.preventDefault()
              if (!fromModel.trim() || !toModel.trim()) {
                toast.add({ title: "Pick both models" })
                return
              }
              simulate.mutate(
                {
                  from: range.from,
                  to: range.to,
                  fromModel: fromModel.trim(),
                  toModel: toModel.trim(),
                  workspaceId: workspace.trim() || undefined,
                },
                {
                  onSuccess: setResult,
                  onError: (e) => {
                    setResult(null)
                    toast.add({
                      title: "Simulation failed",
                      description: e.message,
                    })
                  },
                }
              )
            }}
          >
            <div className="grid gap-1">
              <Label htmlFor="sim-from">Traffic on</Label>
              <Input
                id="sim-from"
                list="sim-models-seen"
                value={fromModel}
                onChange={(e) => setFromModel(e.target.value)}
                placeholder="claude-opus-4-1"
              />
              <datalist id="sim-models-seen">
                {(data?.byModel ?? []).map((r) =>
                  r.model ? <option key={r.model} value={r.model} /> : null
                )}
              </datalist>
            </div>
            <div className="grid gap-1">
              <Label htmlFor="sim-to">Priced as</Label>
              <Input
                id="sim-to"
                list="sim-models-book"
                value={toModel}
                onChange={(e) => setToModel(e.target.value)}
                placeholder="claude-sonnet-4-5"
              />
              <datalist id="sim-models-book">
                {(models.data ?? []).map((m) => (
                  <option key={`${m.provider}-${m.model}`} value={m.model}>
                    {m.provider} · in ${m.inputCostPerMillion}/M · out $
                    {m.outputCostPerMillion}/M
                  </option>
                ))}
              </datalist>
            </div>
            <div className="grid gap-1">
              <Label htmlFor="sim-ws">Workspace (optional)</Label>
              <Input
                id="sim-ws"
                value={workspace}
                onChange={(e) => setWorkspace(e.target.value)}
                placeholder="wrkspc_… / project / team id"
              />
            </div>
            <div className="flex items-end">
              <Button type="submit" disabled={simulate.isPending}>
                Simulate
              </Button>
            </div>
          </form>
          {result && (
            <div className="grid gap-4 md:grid-cols-3">
              <div>
                <div className="text-xs text-muted-foreground">
                  {result.fromModel} cost
                </div>
                <div className="text-xl tabular-nums">
                  {formatCents(result.currentCents)}
                </div>
              </div>
              <div>
                <div className="text-xs text-muted-foreground">
                  As {result.toModel}
                </div>
                <div className="text-xl tabular-nums">
                  {formatCents(result.simulatedCents)}
                </div>
              </div>
              <div>
                <div className="text-xs text-muted-foreground">Difference</div>
                <div className="text-xl tabular-nums">
                  {result.deltaCents === 0
                    ? ""
                    : result.deltaCents < 0
                      ? "−"
                      : "+"}
                  {formatCents(Math.abs(result.deltaCents))}
                </div>
              </div>
              <div className="text-xs text-muted-foreground md:col-span-3">
                {formatTokens(
                  result.uncachedInputTokens +
                    result.cacheReadTokens +
                    result.cacheCreationTokens
                )}{" "}
                input · {formatTokens(result.outputTokens)} output
                {result.requests != null
                  ? ` · ${result.requests} requests`
                  : ""}
                {result.workspaceId ? ` · workspace ${result.workspaceId}` : ""}
              </div>
              <ul className="list-disc pl-5 text-xs text-muted-foreground md:col-span-3">
                {result.caveats.map((c) => (
                  <li key={c}>{c}</li>
                ))}
              </ul>
            </div>
          )}
        </CardContent>
      </Card>
    </>
  )
}
