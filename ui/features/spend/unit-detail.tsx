"use client"

import { useState } from "react"
import { Trash2 } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Spinner } from "@/components/ui/spinner"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { toast } from "@/components/ui/toast"
import { formatCents, providerLabel } from "./format"
import {
  useCreateSpendRule,
  useDeleteSpendBudget,
  useDeleteSpendRule,
  usePutSpendBudget,
} from "./mutations"
import { useSpendBudget } from "./queries"
import type {
  AttributionMatchKind,
  BudgetMode,
  SpendAttributionRuleDto,
  SpendUnitDto,
  VendorProvider,
} from "./types"
import { UnitForm } from "./unit-form"

const kindLabel: Record<AttributionMatchKind, string> = {
  WORKSPACE_ID: "Workspace / project id",
  API_KEY_ID: "API key id",
  ACTOR: "Actor (email / user id)",
}

interface UnitDetailProps {
  unit: SpendUnitDto
  units: SpendUnitDto[]
  rules: SpendAttributionRuleDto[]
  personLevelEnabled: boolean
  onUnitChanged: (unit: SpendUnitDto) => void
}

export function UnitDetail({
  unit,
  units,
  rules,
  personLevelEnabled,
  onUnitChanged,
}: UnitDetailProps) {
  const createRule = useCreateSpendRule()
  const deleteRule = useDeleteSpendRule()
  const budget = useSpendBudget(unit.id)
  const putBudget = usePutSpendBudget()
  const deleteBudget = useDeleteSpendBudget()

  const [provider, setProvider] = useState<VendorProvider>("ANTHROPIC")
  const [matchKind, setMatchKind] =
    useState<AttributionMatchKind>("WORKSPACE_ID")
  const [matchValue, setMatchValue] = useState("")

  const hasBudget = !!budget.data
  const [daily, setDaily] = useState<string | null>(null)
  const [monthly, setMonthly] = useState<string | null>(null)
  const [threshold, setThreshold] = useState<string | null>(null)
  const [mode, setMode] = useState<BudgetMode | null>(null)
  const dailyValue =
    daily ??
    (budget.data?.dailyCents != null
      ? String(budget.data.dailyCents / 100)
      : "")
  const monthlyValue =
    monthly ??
    (budget.data?.monthlyCents != null
      ? String(budget.data.monthlyCents / 100)
      : "")
  const thresholdValue = threshold ?? String(budget.data?.alertThreshold ?? 80)
  const modeValue = mode ?? budget.data?.monthlyMode ?? "ALERT"

  const providerItems = [
    { label: "Anthropic", value: "ANTHROPIC" },
    { label: "OpenAI", value: "OPENAI" },
  ]
  const kindItems = (Object.keys(kindLabel) as AttributionMatchKind[]).map(
    (k) => ({ label: kindLabel[k], value: k })
  )
  const modeItems = [
    { label: "Alert only", value: "ALERT" },
    { label: "Block (advisory)", value: "BLOCK" },
  ]

  return (
    <div className="flex flex-col gap-6 pb-6">
      <section>
        <h3 className="mb-2 text-sm font-medium">Details</h3>
        <UnitForm
          units={units}
          personLevelEnabled={personLevelEnabled}
          existing={unit}
          onDone={onUnitChanged}
        />
      </section>

      <Separator />

      <section>
        <h3 className="mb-1 text-sm font-medium">Attribution rules</h3>
        <p className="mb-3 text-xs text-muted-foreground">
          Usage on a matching vendor dimension is allocated here. Lower priority
          number wins when several rules match one row.
        </p>
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Vendor</TableHead>
                <TableHead>Matches</TableHead>
                <TableHead>Value</TableHead>
                <TableHead className="text-right">Priority</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {rules.map((r) => (
                <TableRow key={r.id}>
                  <TableCell>{providerLabel[r.provider]}</TableCell>
                  <TableCell>{kindLabel[r.matchKind]}</TableCell>
                  <TableCell className="font-mono text-xs">
                    {r.matchValue}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {r.priority}
                  </TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label="Remove rule"
                      onClick={() =>
                        deleteRule.mutate(r.id, {
                          onError: (e) =>
                            toast.add({
                              title: "Remove failed",
                              description: e.message,
                            }),
                        })
                      }
                    >
                      <Trash2 />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
              {rules.length === 0 && (
                <TableRow>
                  <TableCell
                    colSpan={5}
                    className="text-center text-muted-foreground"
                  >
                    No rules yet — nothing is allocated to this unit.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>
        <form
          className="mt-3"
          onSubmit={(event) => {
            event.preventDefault()
            if (!matchValue.trim()) return
            createRule.mutate(
              {
                spendUnitId: unit.id,
                provider,
                matchKind,
                matchValue: matchValue.trim(),
              },
              {
                onSuccess: () => {
                  setMatchValue("")
                  toast.add({ title: "Rule added" })
                },
                onError: (e) =>
                  toast.add({ title: "Add failed", description: e.message }),
              }
            )
          }}
        >
          <FieldGroup>
            <div className="grid grid-cols-2 gap-3">
              <Field>
                <FieldLabel htmlFor="rule-provider">Vendor</FieldLabel>
                <Select
                  items={providerItems}
                  value={provider}
                  onValueChange={(v) =>
                    setProvider((v ?? "ANTHROPIC") as VendorProvider)
                  }
                >
                  <SelectTrigger id="rule-provider">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {providerItems.map((i) => (
                        <SelectItem key={i.value} value={i.value}>
                          {i.label}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel htmlFor="rule-kind">Matches</FieldLabel>
                <Select
                  items={kindItems}
                  value={matchKind}
                  onValueChange={(v) =>
                    setMatchKind((v ?? "WORKSPACE_ID") as AttributionMatchKind)
                  }
                >
                  <SelectTrigger id="rule-kind">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {kindItems.map((i) => (
                        <SelectItem key={i.value} value={i.value}>
                          {i.label}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
            </div>
            <Field>
              <FieldLabel htmlFor="rule-value">Value</FieldLabel>
              <Input
                id="rule-value"
                value={matchValue}
                onChange={(e) => setMatchValue(e.target.value)}
                placeholder={
                  matchKind === "ACTOR"
                    ? "alice@acme.com"
                    : matchKind === "API_KEY_ID"
                      ? "apikey_01…"
                      : "wrkspc_01… / proj_…"
                }
              />
            </Field>
            <Button
              type="submit"
              variant="outline"
              disabled={createRule.isPending || !matchValue.trim()}
            >
              {createRule.isPending && <Spinner data-icon="inline-start" />}
              Add rule
            </Button>
          </FieldGroup>
        </form>
      </section>

      <Separator />

      <section>
        <h3 className="mb-1 text-sm font-medium">Budget</h3>
        <p className="mb-3 text-xs text-muted-foreground">
          Two clocks, UTC calendar. The daily ceiling catches a runaway agent;
          the monthly one is the real budget. Alerts fire once per window at the
          threshold and at the ceiling.
        </p>
        {budget.data && (
          <div className="mb-3 grid grid-cols-2 gap-3 text-sm">
            <div>
              <div className="text-xs text-muted-foreground">Today</div>
              <div className="tabular-nums">
                {formatCents(budget.data.dailySpentCents)}
                {budget.data.dailyCents != null &&
                  ` / ${formatCents(budget.data.dailyCents)}`}
              </div>
            </div>
            <div>
              <div className="text-xs text-muted-foreground">This month</div>
              <div className="tabular-nums">
                {formatCents(budget.data.monthlySpentCents)}
                {budget.data.monthlyCents != null &&
                  ` / ${formatCents(budget.data.monthlyCents)}`}
              </div>
            </div>
          </div>
        )}
        <form
          onSubmit={(event) => {
            event.preventDefault()
            const d =
              dailyValue.trim() === ""
                ? null
                : Math.round(Number(dailyValue) * 100)
            const m =
              monthlyValue.trim() === ""
                ? null
                : Math.round(Number(monthlyValue) * 100)
            if (
              (d != null && Number.isNaN(d)) ||
              (m != null && Number.isNaN(m))
            ) {
              toast.add({ title: "Enter dollar amounts" })
              return
            }
            putBudget.mutate(
              {
                unitId: unit.id,
                dailyCents: d,
                monthlyCents: m,
                alertThreshold: Number(thresholdValue) || 80,
                monthlyMode: modeValue,
              },
              {
                onSuccess: () => toast.add({ title: "Budget saved" }),
                onError: (e) =>
                  toast.add({ title: "Save failed", description: e.message }),
              }
            )
          }}
        >
          <FieldGroup>
            <div className="grid grid-cols-2 gap-3">
              <Field>
                <FieldLabel htmlFor="budget-daily">Daily ($)</FieldLabel>
                <Input
                  id="budget-daily"
                  inputMode="decimal"
                  value={dailyValue}
                  onChange={(e) => setDaily(e.target.value)}
                  placeholder="25"
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="budget-monthly">Monthly ($)</FieldLabel>
                <Input
                  id="budget-monthly"
                  inputMode="decimal"
                  value={monthlyValue}
                  onChange={(e) => setMonthly(e.target.value)}
                  placeholder="500"
                />
              </Field>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Field>
                <FieldLabel htmlFor="budget-threshold">Alert at (%)</FieldLabel>
                <Input
                  id="budget-threshold"
                  type="number"
                  min={1}
                  max={100}
                  value={thresholdValue}
                  onChange={(e) => setThreshold(e.target.value)}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="budget-mode">Monthly ceiling</FieldLabel>
                <Select
                  items={modeItems}
                  value={modeValue}
                  onValueChange={(v) => setMode((v ?? "ALERT") as BudgetMode)}
                >
                  <SelectTrigger id="budget-mode">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      {modeItems.map((i) => (
                        <SelectItem key={i.value} value={i.value}>
                          {i.label}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
            </div>
            <div className="flex gap-2">
              <Button type="submit" disabled={putBudget.isPending}>
                {putBudget.isPending && <Spinner data-icon="inline-start" />}
                {hasBudget ? "Save budget" : "Set budget"}
              </Button>
              {hasBudget && (
                <Button
                  type="button"
                  variant="ghost"
                  disabled={deleteBudget.isPending}
                  onClick={() =>
                    deleteBudget.mutate(unit.id, {
                      onSuccess: () => {
                        setDaily(null)
                        setMonthly(null)
                        toast.add({ title: "Budget removed" })
                      },
                      onError: (e) =>
                        toast.add({
                          title: "Remove failed",
                          description: e.message,
                        }),
                    })
                  }
                >
                  Remove budget
                </Button>
              )}
            </div>
          </FieldGroup>
        </form>
      </section>
    </div>
  )
}
