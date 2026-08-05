"use client"

import { useEffect, useState } from "react"
import { History, Trash2 } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { Input } from "@/components/ui/input"
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
import { ApiError } from "@/lib/api/client"
import { useDeleteCreditPrice, usePublishCreditPrices } from "./mutations"
import { useCreditModels, useCreditPrices } from "./queries"
import { creditPriceValueSchema } from "./schemas"
import type { CreditPriceDto } from "./types"
import { PublishBatchDialog } from "./publish-batch-dialog"
import { PriceHistorySheet } from "./price-history-sheet"
import { formatUtc } from "./weight-utils"

interface PricesTabProps {
  onDirtyChange: (dirty: boolean) => void
}

interface PriceChange {
  denomination: string
  currency: string
  from: number | null
  to: number
}

const CURRENCY_RE = /^[A-Za-z]{3}$/

export function PricesTab({ onDirtyChange }: PricesTabProps) {
  const models = useCreditModels()
  const prices = useCreditPrices()
  const publish = usePublishCreditPrices()
  const deletePrice = useDeleteCreditPrice()

  const [draft, setDraft] = useState<Record<string, { price?: string; currency?: string }>>({})
  const [publishOpen, setPublishOpen] = useState(false)
  const [historyDenomination, setHistoryDenomination] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<CreditPriceDto | null>(null)

  const [now] = useState(() => Date.now())
  const allPrices = prices.data ?? []
  const scheduled = allPrices.filter((p) => new Date(p.effectiveFrom).getTime() > now)
  const effective = allPrices.filter((p) => new Date(p.effectiveFrom).getTime() <= now)

  const currentByDenomination = new Map<string, CreditPriceDto>()
  for (const row of effective) {
    const existing = currentByDenomination.get(row.denomination)
    if (!existing || row.effectiveFrom > existing.effectiveFrom)
      currentByDenomination.set(row.denomination, row)
  }

  // One row per denomination in the account's catalog; rows from retired
  // denominations that still have price history are shown too.
  const denominations = new Set<string>()
  for (const m of models.data ?? []) {
    if (m.denomination) denominations.add(m.denomination)
  }
  for (const p of allPrices) denominations.add(p.denomination)
  const rows = [...denominations].sort().map((denomination) => ({
    denomination,
    current: currentByDenomination.get(denomination) ?? null,
  }))

  const changes: PriceChange[] = []
  const invalidKeys = new Set<string>()
  for (const row of rows) {
    const entry = draft[row.denomination]
    if (!entry) continue
    const rawPrice = entry.price
    const rawCurrency = entry.currency ?? row.current?.currency ?? "USD"
    const priceUntouched = rawPrice === undefined || rawPrice.trim() === ""
    const currencyChanged =
      rawCurrency.toUpperCase() !== (row.current?.currency ?? "USD").toUpperCase()
    if (priceUntouched && !currencyChanged) continue

    const effectivePrice = priceUntouched ? String(row.current?.pricePerCredit ?? "") : rawPrice
    const parsed = creditPriceValueSchema.safeParse(effectivePrice)
    if (!parsed.success || !CURRENCY_RE.test(rawCurrency)) {
      invalidKeys.add(row.denomination)
      continue
    }
    const baseline = row.current ? Number(row.current.pricePerCredit) : null
    if (baseline === null || parsed.data !== baseline || currencyChanged) {
      changes.push({
        denomination: row.denomination,
        currency: rawCurrency.toUpperCase(),
        from: baseline,
        to: parsed.data,
      })
    }
  }

  const dirty = changes.length > 0 || invalidKeys.size > 0
  useEffect(() => onDirtyChange(dirty), [dirty, onDirtyChange])

  const batchGroups = new Map<string, CreditPriceDto[]>()
  for (const row of scheduled) {
    const list = batchGroups.get(row.effectiveFrom) ?? []
    list.push(row)
    batchGroups.set(row.effectiveFrom, list)
  }
  const scheduledBatches = [...batchGroups.entries()].sort(([a], [b]) => a.localeCompare(b))

  const discard = () => setDraft({})

  const handlePublish = (effectiveFromIso: string) => {
    publish.mutate(
      {
        effectiveFrom: effectiveFromIso,
        entries: changes.map((c) => ({
          denomination: c.denomination,
          currency: c.currency,
          pricePerCredit: c.to,
        })),
      },
      {
        onSuccess: () => {
          setPublishOpen(false)
          discard()
          toast.add({
            title: "Prices published",
            description: `Takes effect ${formatUtc(effectiveFromIso)}.`,
          })
        },
        onError: (error) => {
          const conflict = error instanceof ApiError && error.status === 409
          toast.add({
            title: "Publish failed",
            description: conflict
              ? "A change is already scheduled for this exact time — refresh and pick another."
              : error.message,
          })
        },
      },
    )
  }

  const handleDelete = (row: CreditPriceDto) => {
    deletePrice.mutate(row.id, {
      onSuccess: () => {
        setDeleteTarget(null)
        toast.add({ title: "Scheduled price deleted" })
      },
      onError: (error) => {
        setDeleteTarget(null)
        toast.add({ title: "Delete failed", description: error.message })
      },
    })
  }

  if (models.isPending || prices.isPending) {
    return (
      <div className="flex flex-col gap-2">
        <Skeleton className="h-9 w-full" />
        <Skeleton className="h-9 w-full" />
        <Skeleton className="h-9 w-full" />
      </div>
    )
  }

  if (rows.length === 0) {
    return (
      <Empty>
        <EmptyHeader>
          <EmptyTitle>No denominations to price</EmptyTitle>
          <EmptyDescription>
            Create a credit model first — its denomination is what gets a price.
          </EmptyDescription>
        </EmptyHeader>
      </Empty>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      {scheduledBatches.length > 0 && (
        <div className="flex flex-col gap-3 rounded-lg border bg-muted/50 p-4">
          <div className="flex items-center gap-2">
            <h2 className="text-sm font-medium">Scheduled price changes</h2>
            <Badge variant="outline">{scheduled.length}</Badge>
          </div>
          {scheduledBatches.map(([effectiveFrom, batch]) => (
            <div key={effectiveFrom} className="flex flex-col gap-1">
              <p className="text-xs text-muted-foreground">
                {batch.length} {batch.length === 1 ? "price changes" : "prices change"}{" "}
                {formatUtc(effectiveFrom)}
              </p>
              {batch.map((row) => (
                <div key={row.id} className="flex items-center gap-2 text-sm">
                  <span className="font-mono">
                    {row.denomination}: {row.pricePerCredit} {row.currency}
                  </span>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label="Delete scheduled price"
                    onClick={() => setDeleteTarget(row)}
                  >
                    <Trash2 />
                  </Button>
                </div>
              ))}
            </div>
          ))}
        </div>
      )}

      <div className="overflow-x-auto rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Denomination</TableHead>
              <TableHead>Price / credit</TableHead>
              <TableHead>Currency</TableHead>
              <TableHead>Effective since</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row) => {
              const entry = draft[row.denomination]
              const priceBaseline = row.current ? String(row.current.pricePerCredit) : ""
              const priceValue = entry?.price ?? priceBaseline
              const currencyValue = entry?.currency ?? row.current?.currency ?? "USD"
              const invalid = invalidKeys.has(row.denomination)
              const edited = changes.some((c) => c.denomination === row.denomination)
              return (
                <TableRow key={row.denomination}>
                  <TableCell className="font-mono text-xs">{row.denomination}</TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Input
                        className={
                          edited
                            ? "w-28 border-primary font-mono tabular-nums"
                            : "w-28 font-mono tabular-nums"
                        }
                        aria-label={`Price per credit for ${row.denomination}`}
                        aria-invalid={invalid}
                        placeholder="unpriced"
                        value={priceValue}
                        onChange={(e) =>
                          setDraft((d) => ({
                            ...d,
                            [row.denomination]: { ...d[row.denomination], price: e.target.value },
                          }))
                        }
                      />
                      {edited ? (
                        <Badge variant="outline" className="border-primary text-primary">
                          Pending
                        </Badge>
                      ) : (
                        !row.current && <Badge variant="outline">Unpriced</Badge>
                      )}
                    </div>
                    {invalid && (
                      <p className="mt-1 text-xs text-destructive">
                        Positive price, up to 6 decimals, max 1,000,000; 3-letter currency.
                      </p>
                    )}
                  </TableCell>
                  <TableCell>
                    <Input
                      className="w-20 font-mono uppercase"
                      aria-label={`Currency for ${row.denomination}`}
                      maxLength={3}
                      value={currencyValue}
                      onChange={(e) =>
                        setDraft((d) => ({
                          ...d,
                          [row.denomination]: { ...d[row.denomination], currency: e.target.value },
                        }))
                      }
                    />
                  </TableCell>
                  <TableCell className="text-xs text-muted-foreground">
                    {row.current ? formatUtc(row.current.effectiveFrom) : "—"}
                  </TableCell>
                  <TableCell>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label="Price history"
                      onClick={() => setHistoryDenomination(row.denomination)}
                    >
                      <History />
                    </Button>
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </div>

      <p className="text-xs text-muted-foreground">
        The price book sets what one credit costs to buy. Purchased grants without an explicit
        unit price are stamped with the book price in force at the moment of sale.
      </p>

      {dirty && (
        <div className="sticky bottom-0 z-10 flex items-center justify-between rounded-lg border bg-background p-3 shadow-sm">
          <span className="text-sm">
            {changes.length} pending {changes.length === 1 ? "change" : "changes"}
            {invalidKeys.size > 0 && (
              <span className="text-destructive"> · {invalidKeys.size} invalid</span>
            )}
          </span>
          <div className="flex gap-2">
            <Button variant="ghost" onClick={discard}>
              Discard
            </Button>
            <Button
              disabled={changes.length === 0 || invalidKeys.size > 0}
              onClick={() => setPublishOpen(true)}
            >
              Publish…
            </Button>
          </div>
        </div>
      )}

      <Dialog open={!!deleteTarget} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete scheduled price</DialogTitle>
            <DialogDescription>
              {deleteTarget && (
                <>
                  <span className="font-mono">
                    {deleteTarget.denomination}: {deleteTarget.pricePerCredit}{" "}
                    {deleteTarget.currency}
                  </span>{" "}
                  will no longer take effect {formatUtc(deleteTarget.effectiveFrom)}.
                </>
              )}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setDeleteTarget(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={deletePrice.isPending}
              onClick={() => deleteTarget && handleDelete(deleteTarget)}
            >
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <PublishBatchDialog
        key={String(publishOpen)}
        open={publishOpen}
        onOpenChange={setPublishOpen}
        title="Publish credit prices"
        description="All changes below take effect together at the chosen time. Grants stamped before that time keep the old price."
        submitLabel="Publish prices"
        lines={changes.map((change) => ({
          key: change.denomination,
          label: `${change.denomination}: ${change.from ?? "unpriced"} → ${change.to} ${change.currency}`,
        }))}
        isPending={publish.isPending}
        onSubmit={handlePublish}
      />
      <PriceHistorySheet
        key={historyDenomination ?? "closed"}
        denomination={historyDenomination}
        onOpenChange={(open) => !open && setHistoryDenomination(null)}
      />
    </div>
  )
}
