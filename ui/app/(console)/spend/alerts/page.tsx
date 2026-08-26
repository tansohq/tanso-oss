"use client"

import { useState } from "react"
import { Check, RefreshCw } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { toast } from "@/components/ui/toast"
import { formatCents } from "@/features/spend/format"
import {
  useAckSpendAlert,
  useEvaluateBudgets,
  useSendSpendDigest,
} from "@/features/spend/mutations"
import {
  isBuildSideOff,
  useSpendAlerts,
  useSpendDigest,
  useSpendSettings,
} from "@/features/spend/queries"
import type { SpendAlertKind } from "@/features/spend/types"

const kindVariant: Record<
  SpendAlertKind,
  "default" | "secondary" | "destructive"
> = {
  THRESHOLD: "secondary",
  BREACH: "destructive",
  SPIKE: "default",
  PROJECTED: "secondary",
}

export default function SpendAlertsPage() {
  const [tab, setTab] = useState<"open" | "all">("open")
  const alerts = useSpendAlerts(tab === "open")
  const ack = useAckSpendAlert()
  const evaluate = useEvaluateBudgets()
  const digest = useSpendDigest()
  const sendDigest = useSendSpendDigest()
  const settings = useSpendSettings()

  if (isBuildSideOff(alerts.error)) {
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
          <h1 className="text-xl font-semibold tracking-tight">Spend alerts</h1>
          <p className="text-sm text-muted-foreground">
            What the budgets said. Checked after every sync and hourly;
            acknowledge to clear.
          </p>
        </div>
        <Button
          variant="outline"
          disabled={evaluate.isPending}
          onClick={() =>
            evaluate.mutate(undefined, {
              onSuccess: (fired) =>
                toast.add({
                  title:
                    fired.length === 0
                      ? "Nothing new"
                      : `${fired.length} alert${fired.length === 1 ? "" : "s"} fired`,
                }),
              onError: (e) =>
                toast.add({ title: "Check failed", description: e.message }),
            })
          }
        >
          <RefreshCw data-icon="inline-start" />
          Check now
        </Button>
      </div>
      <Tabs value={tab} onValueChange={(v) => setTab(v as "open" | "all")}>
        <TabsList>
          <TabsTrigger value="open">Open</TabsTrigger>
          <TabsTrigger value="all">All</TabsTrigger>
        </TabsList>
      </Tabs>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>When</TableHead>
            <TableHead>Unit</TableHead>
            <TableHead>Kind</TableHead>
            <TableHead>Message</TableHead>
            <TableHead className="text-right">Spent</TableHead>
            <TableHead />
          </TableRow>
        </TableHeader>
        <TableBody>
          {(alerts.data ?? []).map((a) => (
            <TableRow key={a.id}>
              <TableCell className="whitespace-nowrap tabular-nums">
                {new Date(a.firedAt).toLocaleString()}
              </TableCell>
              <TableCell>{a.unitName ?? a.spendUnitId}</TableCell>
              <TableCell>
                <Badge variant={kindVariant[a.kind]}>
                  {a.kind}
                  {a.period ? ` · ${a.period.toLowerCase()}` : ""}
                </Badge>
              </TableCell>
              <TableCell className="max-w-md break-words whitespace-normal">
                {a.message}
              </TableCell>
              <TableCell className="text-right tabular-nums">
                {formatCents(a.spentCents)}
                {a.limitCents != null && (
                  <span className="text-muted-foreground">
                    {" "}
                    / {formatCents(a.limitCents)}
                  </span>
                )}
              </TableCell>
              <TableCell className="text-right">
                {a.ackedAt ? (
                  <span
                    className="text-xs text-muted-foreground"
                    title={a.ackedBy ?? undefined}
                  >
                    Seen
                  </span>
                ) : (
                  <Button
                    variant="ghost"
                    size="sm"
                    disabled={ack.isPending}
                    onClick={() =>
                      ack.mutate(a.id, {
                        onError: (e) =>
                          toast.add({
                            title: "Could not acknowledge",
                            description: e.message,
                          }),
                      })
                    }
                  >
                    <Check data-icon="inline-start" />
                    Acknowledge
                  </Button>
                )}
              </TableCell>
            </TableRow>
          ))}
          {alerts.data && alerts.data.length === 0 && (
            <TableRow>
              <TableCell
                colSpan={6}
                className="text-center text-muted-foreground"
              >
                {tab === "open"
                  ? "Nothing open."
                  : "No alerts yet. Set a budget on a unit under Teams."}
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
      <section className="mt-4 rounded-lg border p-4">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h2 className="text-base font-semibold">Weekly digest</h2>
            <p className="text-sm text-muted-foreground">
              {digest.data
                ? `${digest.data.from} to ${digest.data.to} (exclusive): ${formatCents(digest.data.totalCents)} vs ${formatCents(digest.data.previousTotalCents)} the week before; ${digest.data.alertsFired} alert${digest.data.alertsFired === 1 ? "" : "s"} fired.`
                : "Last seven full days against the seven before, per unit."}{" "}
              {settings.data?.digestEnabled
                ? "Goes out Monday 08:00 UTC."
                : "Not scheduled — switch it on under Teams → Settings to send it Monday 08:00 UTC."}
            </p>
          </div>
          <Button
            variant="outline"
            disabled={sendDigest.isPending}
            onClick={() =>
              sendDigest.mutate(undefined, {
                onSuccess: (sent) => {
                  const d = sent.delivery
                  const word = (o?: string) =>
                    o === "SENT" ? "sent" : o === "FAILED" ? "failed" : "off"
                  const anyFailed =
                    d && [d.slack, d.webhook, d.email].includes("FAILED")
                  toast.add({
                    title: anyFailed ? "Digest partly sent" : "Digest sent",
                    description: d
                      ? `Slack ${word(d.slack)} · webhook ${word(d.webhook)} · email ${word(d.email)}`
                      : undefined,
                  })
                },
                onError: (e) =>
                  toast.add({ title: "Send failed", description: e.message }),
              })
            }
          >
            Send now
          </Button>
        </div>
        {digest.data && digest.data.rows.length > 0 && (
          <Table className="mt-3">
            <TableHeader>
              <TableRow>
                <TableHead>Unit</TableHead>
                <TableHead className="text-right">This week</TableHead>
                <TableHead className="text-right">Week before</TableHead>
                <TableHead>Month to date</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {digest.data.rows.map((r) => (
                <TableRow key={r.unitId}>
                  <TableCell>{r.name}</TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatCents(r.cents)}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatCents(r.previousCents)}
                  </TableCell>
                  <TableCell className="tabular-nums">
                    {r.monthlyLimitCents != null
                      ? `${formatCents(r.monthlySpentCents ?? 0)} of ${formatCents(r.monthlyLimitCents)}${r.bumpReason ? ` (bumped: ${r.bumpReason})` : ""}`
                      : "—"}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </section>
    </>
  )
}
