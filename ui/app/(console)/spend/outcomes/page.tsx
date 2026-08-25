"use client"

import { useState } from "react"
import { Plus, RefreshCw, ShieldCheck, Trash2 } from "lucide-react"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
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
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { toast } from "@/components/ui/toast"
import { daysAgo, formatCents } from "@/features/spend/format"
import {
  useDeleteOutcomeSource,
  useProbeOutcomeSource,
  useSyncOutcomeSource,
} from "@/features/spend/mutations"
import { OutcomeSourceForm } from "@/features/spend/outcome-source-form"
import {
  isBuildSideOff,
  useOutcomeReport,
  useOutcomeSources,
  useRecentOutcomes,
  useSpendUnits,
} from "@/features/spend/queries"

const sourceLabel: Record<string, string> = {
  GITHUB: "GitHub",
  LINEAR: "Linear",
  MANUAL: "Posted",
}
const kindLabel: Record<string, string> = {
  PR_MERGED: "PR merged",
  ISSUE_DONE: "Issue done",
  CUSTOM: "Custom",
}

export default function SpendOutcomesPage() {
  const [range] = useState({ from: daysAgo(29), to: daysAgo(-1) })
  const sources = useOutcomeSources()
  const units = useSpendUnits()
  const report = useOutcomeReport(range.from, range.to)
  const recent = useRecentOutcomes()
  const probe = useProbeOutcomeSource()
  const sync = useSyncOutcomeSource()
  const remove = useDeleteOutcomeSource()
  const [adding, setAdding] = useState(false)
  const [busyId, setBusyId] = useState<string | null>(null)

  if (isBuildSideOff(sources.error)) {
    return (
      <p className="text-sm text-muted-foreground">
        The build side is switched off on this install
        (APP_MODULES_BUILD_ENABLED=false).
      </p>
    )
  }

  return (
    <>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Outcomes</h1>
          <p className="text-sm text-muted-foreground">
            Shipped work next to what it cost. Last 30 days. Anything can post
            one:{" "}
            <code className="font-mono text-xs">
              POST /api/v1/spend/outcomes
            </code>
            .
          </p>
        </div>
        <Button onClick={() => setAdding(true)}>
          <Plus data-icon="inline-start" />
          Connect GitHub / Linear
        </Button>
      </div>

      <div className="grid gap-4 md:grid-cols-4">
        <Card>
          <CardHeader>
            <CardDescription>Outcomes</CardDescription>
            <CardTitle className="text-2xl tabular-nums">
              {report.data?.totalOutcomes ?? "—"}
            </CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>AI-assisted</CardDescription>
            <CardTitle className="text-2xl tabular-nums">
              {report.data && report.data.totalOutcomes > 0
                ? `${report.data.aiAssistedOutcomes} (${Math.round((100 * report.data.aiAssistedOutcomes) / report.data.totalOutcomes)}%)`
                : "—"}
            </CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Metered spend</CardDescription>
            <CardTitle className="text-2xl tabular-nums">
              {formatCents(report.data?.totalSpendCents)}
            </CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Cost per outcome</CardDescription>
            <CardTitle className="text-2xl tabular-nums">
              {formatCents(report.data?.costPerOutcomeCents)}
            </CardTitle>
          </CardHeader>
        </Card>
      </div>

      {report.data && report.data.unattributedOutcomes > 0 && (
        <Alert>
          <AlertTitle>
            {report.data.unattributedOutcomes} outcomes belong to no unit
          </AlertTitle>
          <AlertDescription>
            Their author matched no person and the source has no default unit.
            Set a default unit on the source, or add the person (with email /
            GitHub login) under Teams.
          </AlertDescription>
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>By unit</CardTitle>
          <CardDescription>
            Spend and outcomes include descendants.
          </CardDescription>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Unit</TableHead>
                <TableHead className="text-right">PRs</TableHead>
                <TableHead className="text-right">Issues</TableHead>
                <TableHead className="text-right">Custom</TableHead>
                <TableHead
                  className="text-right"
                  title="Outcomes with an AI assistant in the work"
                >
                  AI-assisted
                </TableHead>
                <TableHead className="text-right">Metered spend</TableHead>
                <TableHead
                  className="text-right"
                  title="Claude Code's own per-person estimate; not part of the spend basis"
                >
                  Claude Code est.
                </TableHead>
                <TableHead className="text-right">Cost / outcome</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(report.data?.rows ?? []).map((r) => (
                <TableRow key={r.unitId}>
                  <TableCell>{r.name}</TableCell>
                  <TableCell className="text-right tabular-nums">
                    {r.prsMerged}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {r.issuesDone}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {r.custom}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatCents(r.spendCents)}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatCents(r.personEstimateCents)}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatCents(r.costPerOutcomeCents)}
                  </TableCell>
                </TableRow>
              ))}
              {report.data && report.data.rows.length === 0 && (
                <TableRow>
                  <TableCell
                    colSpan={7}
                    className="text-center text-muted-foreground"
                  >
                    No units yet. Create teams under Spend → Teams first.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Sources</CardTitle>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>System</TableHead>
                <TableHead>Label</TableHead>
                <TableHead>Scope</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Last synced</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {(sources.data ?? []).map((s) => {
                const busy = busyId === s.id
                return (
                  <TableRow key={s.id}>
                    <TableCell>
                      <Badge variant="secondary">{sourceLabel[s.source]}</Badge>
                    </TableCell>
                    <TableCell>{s.label}</TableCell>
                    <TableCell className="font-mono text-xs">
                      {s.scope}
                    </TableCell>
                    <TableCell>
                      {s.status === "ERROR" ? (
                        <span
                          className="text-destructive"
                          title={s.lastError ?? undefined}
                        >
                          Error
                        </span>
                      ) : (
                        <span className="text-muted-foreground">OK</span>
                      )}
                    </TableCell>
                    <TableCell className="tabular-nums">
                      {s.lastSyncedAt
                        ? new Date(s.lastSyncedAt).toLocaleString()
                        : "Never"}
                    </TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          disabled={busy}
                          onClick={() => {
                            setBusyId(s.id)
                            probe.mutate(s.id, {
                              onSuccess: (r) =>
                                toast.add({
                                  title: r.ok
                                    ? "Token accepted"
                                    : "Token rejected",
                                  description: (r.message ?? "").slice(0, 160),
                                }),
                              onError: (e) =>
                                toast.add({
                                  title: "Check failed",
                                  description: e.message,
                                }),
                              onSettled: () => setBusyId(null),
                            })
                          }}
                        >
                          <ShieldCheck data-icon="inline-start" />
                          Check
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          disabled={busy}
                          onClick={() => {
                            setBusyId(s.id)
                            sync.mutate(s.id, {
                              onSuccess: (r) =>
                                toast.add({
                                  title: "Synced",
                                  description: `${r.rowsWritten} outcomes for ${r.from} → ${r.to}`,
                                }),
                              onError: (e) =>
                                toast.add({
                                  title: "Sync failed",
                                  description: e.message,
                                }),
                              onSettled: () => setBusyId(null),
                            })
                          }}
                        >
                          <RefreshCw data-icon="inline-start" />
                          Sync
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label="Disconnect"
                          disabled={busy || remove.isPending}
                          onClick={() => {
                            if (
                              !window.confirm(
                                `Disconnect ${s.label}? Its pulled outcomes are removed.`
                              )
                            )
                              return
                            remove.mutate(s.id, {
                              onSuccess: () =>
                                toast.add({ title: "Disconnected" }),
                              onError: (e) =>
                                toast.add({
                                  title: "Disconnect failed",
                                  description: e.message,
                                }),
                            })
                          }}
                        >
                          <Trash2 />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })}
              {sources.data && sources.data.length === 0 && (
                <TableRow>
                  <TableCell
                    colSpan={6}
                    className="text-center text-muted-foreground"
                  >
                    No sources. Connect GitHub or Linear, or post outcomes from
                    CI.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Recent</CardTitle>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>When</TableHead>
                <TableHead>Kind</TableHead>
                <TableHead>What</TableHead>
                <TableHead>Who</TableHead>
                <TableHead>Unit</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(recent.data ?? []).slice(0, 50).map((o) => (
                <TableRow key={o.id}>
                  <TableCell className="whitespace-nowrap tabular-nums">
                    {new Date(o.occurredAt).toLocaleString()}
                  </TableCell>
                  <TableCell className="whitespace-nowrap">
                    <Badge variant="secondary">{kindLabel[o.kind]}</Badge>
                    {o.aiAssisted && (
                      <Badge
                        variant="outline"
                        className="ml-1"
                        title={o.aiTool ?? undefined}
                      >
                        AI{o.aiTool ? ` · ${o.aiTool}` : ""}
                      </Badge>
                    )}
                  </TableCell>
                  <TableCell className="max-w-md whitespace-normal">
                    {o.url ? (
                      <a
                        href={o.url}
                        target="_blank"
                        rel="noreferrer"
                        className="underline-offset-2 hover:underline"
                      >
                        {o.title ?? o.externalId}
                      </a>
                    ) : (
                      (o.title ?? o.externalId)
                    )}
                    <span className="ml-2 font-mono text-xs text-muted-foreground">
                      {o.externalId}
                    </span>
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {o.actorEmail ?? o.actorLogin ?? "—"}
                  </TableCell>
                  <TableCell>
                    {o.unitName ?? (
                      <span className="text-muted-foreground">—</span>
                    )}
                  </TableCell>
                </TableRow>
              ))}
              {recent.data && recent.data.length === 0 && (
                <TableRow>
                  <TableCell
                    colSpan={5}
                    className="text-center text-muted-foreground"
                  >
                    Nothing shipped yet — or nothing pulled. Sync a source.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Sheet open={adding} onOpenChange={setAdding}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Connect a source</SheetTitle>
            <SheetDescription>
              Merged pull requests or completed issues become outcomes.
            </SheetDescription>
          </SheetHeader>
          <div className="px-4">
            <OutcomeSourceForm
              units={units.data ?? []}
              onDone={() => setAdding(false)}
            />
          </div>
        </SheetContent>
      </Sheet>
    </>
  )
}
