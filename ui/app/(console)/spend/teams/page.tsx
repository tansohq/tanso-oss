"use client"

import { useState } from "react"
import { ChevronRight, Plus, Settings2, Trash2 } from "lucide-react"

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
import { Label } from "@/components/ui/label"
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
import { useDeleteSpendUnit } from "@/features/spend/mutations"
import {
  isBuildSideOff,
  useSpendAllocation,
  useSpendRules,
  useSpendSettings,
  useSpendUnits,
} from "@/features/spend/queries"
import { SpendSettingsForm } from "@/features/spend/spend-settings-form"
import type {
  SpendAllocationRowDto,
  SpendUnitDto,
} from "@/features/spend/types"
import { UnitDetail } from "@/features/spend/unit-detail"
import { UnitForm } from "@/features/spend/unit-form"

const typeLabel: Record<string, string> = {
  TEAM: "Team",
  PERSON: "Person",
  PROJECT: "Project",
}

function orderTree(
  units: SpendUnitDto[]
): { unit: SpendUnitDto; depth: number }[] {
  const byParent = new Map<string | undefined, SpendUnitDto[]>()
  for (const u of units) {
    const key = u.parentId ?? undefined
    byParent.set(key, [...(byParent.get(key) ?? []), u])
  }
  const out: { unit: SpendUnitDto; depth: number }[] = []
  const walk = (
    parent: string | undefined,
    depth: number,
    seen: Set<string>
  ) => {
    for (const u of byParent.get(parent) ?? []) {
      if (seen.has(u.id)) continue
      seen.add(u.id)
      out.push({ unit: u, depth })
      walk(u.id, depth + 1, seen)
    }
  }
  walk(undefined, 0, new Set())
  // orphans (parent deleted or unknown) still show
  for (const u of units)
    if (!out.some((o) => o.unit.id === u.id)) out.push({ unit: u, depth: 0 })
  return out
}

export default function SpendTeamsPage() {
  const [range] = useState({ from: daysAgo(29), to: daysAgo(-1) })
  const units = useSpendUnits()
  const rules = useSpendRules()
  const settings = useSpendSettings()
  const allocation = useSpendAllocation(range.from, range.to)
  const removeUnit = useDeleteSpendUnit()
  const [creating, setCreating] = useState(false)
  const [selected, setSelected] = useState<SpendUnitDto | null>(null)
  const [settingsOpen, setSettingsOpen] = useState(false)

  const allocationByUnit = new Map<string, SpendAllocationRowDto>()
  for (const row of allocation.data?.rows ?? [])
    allocationByUnit.set(row.unitId, row)
  const rulesByUnit = new Map<string, number>()
  for (const r of rules.data ?? [])
    rulesByUnit.set(r.spendUnitId, (rulesByUnit.get(r.spendUnitId) ?? 0) + 1)

  if (units.error && !isBuildSideOff(units.error)) {
    return (
      <Alert variant="destructive">
        <AlertTitle>Could not load units</AlertTitle>
        <AlertDescription>{units.error.message}</AlertDescription>
      </Alert>
    )
  }
  if (isBuildSideOff(units.error)) {
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
          <h1 className="text-xl font-semibold tracking-tight">
            Teams &amp; budgets
          </h1>
          <p className="text-sm text-muted-foreground">
            Who the spend belongs to, and how much each may spend. Last 30 days
            shown.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => setSettingsOpen(true)}>
            <Settings2 data-icon="inline-start" />
            Spend settings
          </Button>
          <Button onClick={() => setCreating(true)}>
            <Plus data-icon="inline-start" />
            New unit
          </Button>
        </div>
      </div>

      {settings.data && !settings.data.personLevelEnabled && (
        <Alert>
          <AlertTitle>Person-level attribution is off</AlertTitle>
          <AlertDescription>
            Spend is allocated to teams and projects only. Turn it on under
            Spend settings — after telling staff — to add people.
          </AlertDescription>
        </Alert>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Allocation</CardTitle>
          <CardDescription>
            {allocation.data
              ? `${formatCents(allocation.data.totalMeteredCents)} metered · ${formatCents(allocation.data.unattributedCents)} unattributed`
              : "—"}
          </CardDescription>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Unit</TableHead>
                <TableHead>Type</TableHead>
                <TableHead className="text-right">Own</TableHead>
                <TableHead className="text-right">Total</TableHead>
                <TableHead
                  className="text-right"
                  title="Claude Code's own per-person estimate; not rolled up into the team"
                >
                  Claude Code est.
                </TableHead>
                <TableHead className="text-right">Rules</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {orderTree(units.data ?? []).map(({ unit, depth }) => {
                const row = allocationByUnit.get(unit.id)
                return (
                  <TableRow
                    key={unit.id}
                    className="cursor-pointer"
                    onClick={() => setSelected(unit)}
                  >
                    <TableCell>
                      <span
                        style={{ paddingLeft: depth * 16 }}
                        className="inline-flex items-center gap-1"
                      >
                        {depth > 0 && (
                          <ChevronRight className="size-3 text-muted-foreground" />
                        )}
                        {unit.name}
                        {unit.email && (
                          <span className="ml-2 text-xs text-muted-foreground">
                            {unit.email}
                          </span>
                        )}
                      </span>
                    </TableCell>
                    <TableCell>
                      <Badge variant="secondary">{typeLabel[unit.type]}</Badge>
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatCents(row?.ownCents)}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatCents(row?.totalCents)}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatCents(row?.personEstimateCents)}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {rulesByUnit.get(unit.id) ?? 0}
                    </TableCell>
                    <TableCell className="text-right">
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label="Remove unit"
                        disabled={removeUnit.isPending}
                        onClick={(event) => {
                          event.stopPropagation()
                          if (
                            !window.confirm(
                              `Remove ${unit.name}? Its rules and budget go with it; children move up.`
                            )
                          )
                            return
                          removeUnit.mutate(unit.id, {
                            onSuccess: () =>
                              toast.add({ title: "Unit removed" }),
                            onError: (error) =>
                              toast.add({
                                title: "Remove failed",
                                description: error.message,
                              }),
                          })
                        }}
                      >
                        <Trash2 />
                      </Button>
                    </TableCell>
                  </TableRow>
                )
              })}
              {units.data && units.data.length === 0 && (
                <TableRow>
                  <TableCell
                    colSpan={7}
                    className="text-center text-muted-foreground"
                  >
                    No units yet. Create a team, then map its vendor workspace
                    or keys to it.
                  </TableCell>
                </TableRow>
              )}
              {allocation.data && allocation.data.unattributedCents > 0 && (
                <TableRow>
                  <TableCell className="text-muted-foreground">
                    Unattributed
                  </TableCell>
                  <TableCell />
                  <TableCell className="text-right text-muted-foreground tabular-nums">
                    {formatCents(allocation.data.unattributedCents)}
                  </TableCell>
                  <TableCell colSpan={4} />
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Sheet open={creating} onOpenChange={setCreating}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>New unit</SheetTitle>
            <SheetDescription>
              A team, a project, or — if enabled — a person.
            </SheetDescription>
          </SheetHeader>
          <div className="px-4">
            <UnitForm
              units={units.data ?? []}
              personLevelEnabled={!!settings.data?.personLevelEnabled}
              onDone={() => setCreating(false)}
            />
          </div>
        </SheetContent>
      </Sheet>

      <Sheet open={!!selected} onOpenChange={(v) => !v && setSelected(null)}>
        <SheetContent className="overflow-y-auto sm:max-w-xl">
          {selected && (
            <>
              <SheetHeader>
                <SheetTitle>{selected.name}</SheetTitle>
                <SheetDescription>
                  {typeLabel[selected.type]} · rules, budget and standing.
                </SheetDescription>
              </SheetHeader>
              <div className="px-4">
                <UnitDetail
                  unit={selected}
                  units={units.data ?? []}
                  rules={(rules.data ?? []).filter(
                    (r) => r.spendUnitId === selected.id
                  )}
                  personLevelEnabled={!!settings.data?.personLevelEnabled}
                  onUnitChanged={(u) => setSelected(u)}
                />
              </div>
            </>
          )}
        </SheetContent>
      </Sheet>

      <Sheet open={settingsOpen} onOpenChange={setSettingsOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Spend settings</SheetTitle>
            <SheetDescription>
              Person-level attribution and where alerts go.
            </SheetDescription>
          </SheetHeader>
          <div className="px-4">
            {settings.data ? (
              <SpendSettingsForm
                settings={settings.data}
                onDone={() => setSettingsOpen(false)}
              />
            ) : (
              <Label>Loading…</Label>
            )}
          </div>
        </SheetContent>
      </Sheet>
    </>
  )
}
