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
  useBumpSpendBudget,
  useClearSpendBudgetBump,
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
  const bumpBudget = useBumpSpendBudget()
  const clearBump = useClearSpendBudgetBump()
  const [bumpAmount, setBumpAmount] = useState("")
  const [bumpUntil, setBumpUntil] = useState("")
  const [bumpReason, setBumpReason] = useState("")

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
    { label: "Cursor", value: "CURSOR" },
    { label: "GitHub Copilot", value: "COPILOT" },
    { label: "LiteLLM", value: "LITELLM" },
  ]
  const kindItems = (Object.keys(kindLabel) as AttributionMatchKind[]).map(
    (k) => ({ label: kindLabel[k], value: k })
  )
  const modeItems = [
    { label: "Alert only", value: "ALERT" },
    { label: "Block", value: "BLOCK" },
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
                  <TableCell className="whitespace-normal">
                    {kindLabel[r.matchKind]}
                  </TableCell>
                  <TableCell className="font-mono text-xs whitespace-normal break-words">
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
                  provider === "LITELLM"
                    ? matchKind === "WORKSPACE_ID"
                      ? "team_id, e.g. backend"
                      : matchKind === "API_KEY_ID"
                        ? "the virtual key (sk-…)"
                        : "user_id"
                    : matchKind === "ACTOR"
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
                {budget.data.effectiveMonthlyCents != null &&
                  ` / ${formatCents(budget.data.effectiveMonthlyCents)}`}
              </div>
            </div>
            {budget.data.bumpMonthlyCents != null &&
              budget.data.bumpExpiresAt && (
                <div className="col-span-2 text-xs">
                  Bumped to {formatCents(budget.data.bumpMonthlyCents)} until{" "}
                  {new Date(budget.data.bumpExpiresAt)
                    .toISOString()
                    .replace("T", " ")
                    .slice(0, 16)}{" "}
                  UTC
                  {budget.data.bumpReason ? ` — ${budget.data.bumpReason}` : ""}
                  .{" "}
                  <button
                    type="button"
                    className="underline"
                    disabled={clearBump.isPending}
                    onClick={() =>
                      clearBump.mutate(unit.id, {
                        onSuccess: () => toast.add({ title: "Bump ended" }),
                        onError: (e) =>
                          toast.add({
                            title: "Could not end the bump",
                            description: e.message,
                          }),
                      })
                    }
                  >
                    End now
                  </button>
                </div>
              )}
            {budget.data.enforcementTarget && (
              <div className="col-span-2 text-xs text-muted-foreground">
                Enforced at {budget.data.enforcementTarget} — the gateway
                refuses requests past the monthly ceiling.
              </div>
            )}
            {budget.data.gatewaySpentCents != null && (
              <div className="col-span-2 text-xs text-muted-foreground">
                {`LiteLLM itself counts ${formatCents(budget.data.gatewaySpentCents)} this month for the team/key/user this unit's rules name — priced by its own model map, and the number it enforces against. The figure above is by Tanso's price book.`}
              </div>
            )}
            {budget.data.enforcementError && (
              <div className="col-span-2 text-xs text-destructive">
                Not enforced: {budget.data.enforcementError}
              </div>
            )}
            {budget.data.monthlyMode === "BLOCK" &&
              !budget.data.enforcementTarget &&
              !budget.data.enforcementError && (
                <div className="col-span-2 text-xs text-muted-foreground">
                  Block is advisory until a LiteLLM connection and a LiteLLM
                  rule on this unit exist; then the ceiling is pushed to the
                  gateway.
                </div>
              )}
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
        {hasBudget && budget.data?.monthlyCents != null && (
          <form
            className="mt-4"
            onSubmit={(event) => {
              event.preventDefault()
              const cents = Math.round(Number(bumpAmount) * 100)
              if (!bumpAmount.trim() || Number.isNaN(cents) || !bumpUntil) {
                toast.add({ title: "Enter an amount and an end date" })
                return
              }
              bumpBudget.mutate(
                {
                  unitId: unit.id,
                  monthlyCents: cents,
                  expiresAt: new Date(bumpUntil + "T23:59:59Z").toISOString(),
                  reason: bumpReason.trim() || undefined,
                },
                {
                  onSuccess: () => {
                    setBumpAmount("")
                    setBumpReason("")
                    toast.add({ title: "Ceiling bumped" })
                  },
                  onError: (e) =>
                    toast.add({ title: "Bump failed", description: e.message }),
                }
              )
            }}
          >
            <h4 className="mb-1 text-sm font-medium">Temporary bump</h4>
            <p className="mb-2 text-xs text-muted-foreground">
              Lift the monthly ceiling until a date — a launch, a migration —
              without touching the standing number. It drops off on its own.
            </p>
            <FieldGroup>
              <div className="grid grid-cols-3 gap-3">
                <Field>
                  <FieldLabel htmlFor="bump-amount">Monthly ($)</FieldLabel>
                  <Input
                    id="bump-amount"
                    inputMode="decimal"
                    value={bumpAmount}
                    onChange={(e) => setBumpAmount(e.target.value)}
                    placeholder={String(
                      Math.round((budget.data.monthlyCents * 1.5) / 100)
                    )}
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="bump-until">Until (UTC)</FieldLabel>
                  <Input
                    id="bump-until"
                    type="date"
                    value={bumpUntil}
                    onChange={(e) => setBumpUntil(e.target.value)}
                  />
                </Field>
                <Field>
                  <FieldLabel htmlFor="bump-reason">Reason</FieldLabel>
                  <Input
                    id="bump-reason"
                    value={bumpReason}
                    onChange={(e) => setBumpReason(e.target.value)}
                    placeholder="Launch week"
                  />
                </Field>
              </div>
              <div>
                <Button
                  type="submit"
                  variant="outline"
                  disabled={bumpBudget.isPending}
                >
                  {bumpBudget.isPending && <Spinner data-icon="inline-start" />}
                  Bump
                </Button>
              </div>
            </FieldGroup>
          </form>
        )}
      </section>
    </div>
  )
}
