"use client"

import { useState } from "react"
import { Check, Copy, KeyRound, RotateCw, Trash2 } from "lucide-react"

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
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { toast } from "@/components/ui/toast"
import { formatCurrency, formatDate, formatNumber } from "@/lib/format"
import type { CustomerApiKeyDto } from "@/lib/api/types"

import {
  useClearKeyBudget,
  useCreateCustomerKey,
  useRevokeCustomerKey,
  useRotateCustomerKey,
  useSetKeyBudget,
} from "./mutations"
import { useCustomerKeys, useKeyBudget } from "./queries"

const PERIOD_SUFFIX: Record<string, string> = {
  DAY: "/day",
  WEEK: "/wk",
  MONTH: "/mo",
  TOTAL: " total",
}

/**
 * What the key is capped at, short enough to read down a column. An uncapped
 * axis is left out rather than printed as "unlimited" — the point of the column
 * is spotting the limits that exist.
 */
function budgetSummary(key: CustomerApiKeyDto) {
  const suffix = PERIOD_SUFFIX[key.budgetPeriod ?? ""] ?? ""
  const parts: string[] = []
  if (key.budgetCredits != null) parts.push(`${formatNumber(Number(key.budgetCredits))} cr`)
  if (key.budgetAmount != null) parts.push(formatCurrency(Number(key.budgetAmount)))
  // A budget with a window but no limit on either axis caps nothing.
  return parts.length ? `${parts.join(" · ")}${suffix}` : "No cap"
}

const PERIODS = [
  { value: "DAY", label: "Per day" },
  { value: "WEEK", label: "Per week" },
  { value: "MONTH", label: "Per month" },
  { value: "TOTAL", label: "Total, never resets" },
]

/** Shows the plaintext key once. There is no second chance to read it. */
function NewKeyDialog({
  apiKey,
  onClose,
}: {
  apiKey: string | null
  onClose: () => void
}) {
  const [copied, setCopied] = useState(false)
  return (
    <Dialog open={Boolean(apiKey)} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Copy this key now</DialogTitle>
          <DialogDescription>
            This is the only time the key is shown. Tanso stores a hash, so it cannot be
            shown again — if it is lost, rotate the key.
          </DialogDescription>
        </DialogHeader>
        <code className="block overflow-x-auto rounded-md border bg-muted p-3 font-mono text-xs">
          {apiKey}
        </code>
        <DialogFooter>
          <Button
            onClick={() => {
              if (apiKey) navigator.clipboard?.writeText(apiKey)
              setCopied(true)
            }}
          >
            {copied ? <Check /> : <Copy />}
            {copied ? "Copied" : "Copy key"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function BudgetDialog({
  customerId,
  apiKey,
  onClose,
}: {
  customerId: string
  apiKey: CustomerApiKeyDto | null
  onClose: () => void
}) {
  const budget = useKeyBudget(customerId, apiKey?.id ?? null)
  const setBudget = useSetKeyBudget(customerId)
  const clearBudget = useClearKeyBudget(customerId)

  const [period, setPeriod] = useState("MONTH")
  const [creditLimit, setCreditLimit] = useState("")
  const [amountLimit, setAmountLimit] = useState("")
  const [alertThreshold, setAlertThreshold] = useState("")
  const [loadedFor, setLoadedFor] = useState<string | null>(null)

  // Seed the form from whatever budget the key already has, once per key.
  if (apiKey && budget.data && loadedFor !== apiKey.id) {
    setLoadedFor(apiKey.id ?? null)
    setPeriod(budget.data.period ?? "MONTH")
    setCreditLimit(budget.data.creditLimit != null ? String(budget.data.creditLimit) : "")
    setAmountLimit(budget.data.amountLimit != null ? String(budget.data.amountLimit) : "")
    setAlertThreshold(budget.data.alertThreshold != null ? String(budget.data.alertThreshold) : "")
  }

  const current = budget.data
  const hasBudget = Boolean(current?.period)

  return (
    <Dialog
      open={Boolean(apiKey)}
      onOpenChange={(open) => {
        if (!open) {
          setLoadedFor(null)
          onClose()
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Spend budget for {apiKey?.keyHint}</DialogTitle>
          <DialogDescription>
            Caps what this one key can spend. Credits and money are capped separately, and
            leaving a field empty means that side is unlimited. Other keys on this customer
            are unaffected.
          </DialogDescription>
        </DialogHeader>

        {hasBudget && current ? (
          <div className="grid grid-cols-2 gap-3 rounded-md border p-3 text-sm">
            <div>
              <div className="text-muted-foreground">Credits used</div>
              <div className="font-mono">
                {formatNumber(Number(current.creditsSpent ?? 0))}
                {current.creditLimit != null
                  ? ` / ${formatNumber(Number(current.creditLimit))}`
                  : " (unlimited)"}
              </div>
            </div>
            <div>
              <div className="text-muted-foreground">Money spent</div>
              <div className="font-mono">
                {formatCurrency(Number(current.amountSpent ?? 0))}
                {current.amountLimit != null
                  ? ` / ${formatCurrency(Number(current.amountLimit))}`
                  : " (unlimited)"}
              </div>
            </div>
            <div className="col-span-2 text-muted-foreground">
              {current.resetsAt
                ? `Window resets ${formatDate(current.resetsAt)}`
                : "This budget never resets"}
            </div>
            {current.alerting ? (
              <div className="col-span-2 text-foreground">
                Past its {current.alertThreshold}% warning mark since{" "}
                {formatDate(current.alertingSince ?? "")} — now at {current.percentUsed}%.
              </div>
            ) : null}
          </div>
        ) : null}

        <Field>
          <FieldLabel htmlFor="budget-period">Window</FieldLabel>
          <Select items={PERIODS} value={period} onValueChange={(v) => setPeriod(String(v))}>
            <SelectTrigger id="budget-period">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {PERIODS.map((p) => (
                  <SelectItem key={p.value} value={p.value}>
                    {p.label}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
          <FieldDescription>
            Changing the window starts it over, so spend already recorded stops counting.
          </FieldDescription>
        </Field>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field>
            <FieldLabel htmlFor="budget-credits">Credit limit</FieldLabel>
            <Input
              id="budget-credits"
              inputMode="decimal"
              placeholder="Unlimited"
              value={creditLimit}
              onChange={(e) => setCreditLimit(e.target.value)}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="budget-amount">Spend limit</FieldLabel>
            <Input
              id="budget-amount"
              inputMode="decimal"
              placeholder="Unlimited"
              value={amountLimit}
              onChange={(e) => setAmountLimit(e.target.value)}
            />
            <FieldDescription>Money this key may spend on top-ups and plans.</FieldDescription>
          </Field>
        </div>

        <Field>
          <FieldLabel htmlFor="budget-alert">Warn at</FieldLabel>
          <Input
            id="budget-alert"
            inputMode="numeric"
            placeholder="80"
            value={alertThreshold}
            onChange={(e) => setAlertThreshold(e.target.value)}
          />
          <FieldDescription>
            Percent of the tightest limit at which this key starts reporting itself as near
            its ceiling, so an agent can slow down instead of hitting a wall. 0 never warns.
          </FieldDescription>
        </Field>

        <DialogFooter>
          {hasBudget ? (
            <Button
              variant="outline"
              disabled={clearBudget.isPending}
              onClick={() =>
                apiKey?.id &&
                clearBudget.mutate(apiKey.id, {
                  onSuccess: () => {
                    toast.add({ title: "Budget cleared", description: "This key is now uncapped." })
                    setLoadedFor(null)
                    onClose()
                  },
                  onError: (error: Error) =>
                    toast.add({ title: "Could not clear budget", description: error.message }),
                })
              }
            >
              Remove cap
            </Button>
          ) : null}
          <Button
            disabled={setBudget.isPending}
            onClick={() =>
              apiKey?.id &&
              setBudget.mutate(
                { keyId: apiKey.id, period, creditLimit, amountLimit, alertThreshold },
                {
                  onSuccess: () => {
                    toast.add({ title: "Budget saved" })
                    setLoadedFor(null)
                    onClose()
                  },
                  onError: (error: Error) =>
                    toast.add({ title: "Could not save budget", description: error.message }),
                },
              )
            }
          >
            Save budget
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export function CustomerKeysCard({
  customerId,
  customerReferenceId,
}: {
  customerId: string
  customerReferenceId?: string | null
}) {
  // A customer-scoped key is pinned to its customer by reference, so there is
  // nothing to pin one to until the customer has a Reference ID.
  const hasReference = Boolean(customerReferenceId)
  const keys = useCustomerKeys(customerId, hasReference)
  const createKey = useCreateCustomerKey(customerId)
  const rotateKey = useRotateCustomerKey(customerId)
  const revokeKey = useRevokeCustomerKey(customerId)

  const [newKey, setNewKey] = useState<string | null>(null)
  const [budgetFor, setBudgetFor] = useState<CustomerApiKeyDto | null>(null)

  const issue = (scopes: string[]) =>
    createKey.mutate(scopes, {
      onSuccess: (created) => setNewKey(created.apiKey ?? null),
      onError: (error: Error) =>
        toast.add({ title: "Could not issue key", description: error.message }),
    })

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-4">
        <div>
          <CardTitle>API keys</CardTitle>
          <CardDescription>
            Keys this customer&apos;s agents use, and what each one is allowed to spend.
          </CardDescription>
        </div>
        {hasReference ? (
          <div className="flex shrink-0 gap-2">
            <Button variant="outline" disabled={createKey.isPending} onClick={() => issue(["read"])}>
              <KeyRound />
              Read-only key
            </Button>
            <Button disabled={createKey.isPending} onClick={() => issue(["read", "purchase"])}>
              <KeyRound />
              Key that can buy
            </Button>
          </div>
        ) : null}
      </CardHeader>
      <CardContent>
        {!hasReference ? (
          <Empty>
            <EmptyHeader>
              <EmptyTitle>No Reference ID</EmptyTitle>
              <EmptyDescription>
                A customer-scoped key is pinned to its customer by Reference ID. Add one with
                Edit customer before issuing keys.
              </EmptyDescription>
            </EmptyHeader>
          </Empty>
        ) : keys.isPending ? (
          <Skeleton className="h-24 w-full" />
        ) : keys.data && keys.data.length > 0 ? (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Key</TableHead>
                <TableHead>Can do</TableHead>
                <TableHead>Budget</TableHead>
                <TableHead>Issued</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {keys.data.map((key) => (
                <TableRow key={key.id}>
                  <TableCell className="font-mono">{key.keyHint}</TableCell>
                  <TableCell>
                    <div className="flex gap-1">
                      {(key.scopes ?? []).map((scope) => (
                        <Badge key={scope} variant="outline">
                          {scope}
                        </Badge>
                      ))}
                    </div>
                  </TableCell>
                  <TableCell>
                    <Button
                      variant={key.budgetPeriod ? "secondary" : "outline"}
                      size="sm"
                      onClick={() => setBudgetFor(key)}
                    >
                      {key.budgetPeriod ? budgetSummary(key) : "No cap"}
                    </Button>
                  </TableCell>
                  <TableCell>{key.createdAt ? formatDate(key.createdAt) : "—"}</TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={`Rotate key ${key.keyHint}`}
                      disabled={rotateKey.isPending}
                      onClick={() =>
                        key.id &&
                        rotateKey.mutate(key.id, {
                          onSuccess: (replacement) => setNewKey(replacement.apiKey ?? null),
                          onError: (error: Error) =>
                            toast.add({ title: "Could not rotate key", description: error.message }),
                        })
                      }
                    >
                      <RotateCw />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={`Revoke key ${key.keyHint}`}
                      disabled={revokeKey.isPending}
                      onClick={() =>
                        key.id &&
                        revokeKey.mutate(key.id, {
                          onSuccess: () => toast.add({ title: "Key revoked" }),
                          onError: (error: Error) =>
                            toast.add({ title: "Could not revoke key", description: error.message }),
                        })
                      }
                    >
                      <Trash2 />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : (
          <Empty>
            <EmptyHeader>
              <EmptyTitle>No keys yet</EmptyTitle>
              <EmptyDescription>
                Issue a key so this customer&apos;s agents can read their usage, and give it the
                purchase scope only if it should be able to spend.
              </EmptyDescription>
            </EmptyHeader>
          </Empty>
        )}
      </CardContent>

      <NewKeyDialog apiKey={newKey} onClose={() => setNewKey(null)} />
      <BudgetDialog
        customerId={customerId}
        apiKey={budgetFor}
        onClose={() => setBudgetFor(null)}
      />
    </Card>
  )
}
