"use client"

import { useEffect, useState } from "react"
import { History, Plus, Trash2 } from "lucide-react"

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
import { Field, FieldLabel } from "@/components/ui/field"
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
import { ApiError } from "@/lib/api/client"
import { useFeatures } from "@/features/features/queries"
import { useDeleteCreditWeight, usePublishCreditWeights } from "./mutations"
import {
  useCreditPrices,
  useCreditWeights,
  useCreditWeightUnitCosts,
  useFeatureDenominations,
} from "./queries"
import { creditWeightValueSchema } from "./schemas"
import type { CreditFeatureWeightDto, CreditPriceDto } from "./types"
import { PublishWeightsDialog, type WeightChange } from "./publish-weights-dialog"
import { TwoDialsExplainer } from "./two-dials-explainer"
import { WeightHistorySheet, type WeightPair } from "./weight-history-sheet"
import { formatMoney, formatUnitCost, formatUtc, pairKey } from "./weight-utils"

interface WeightsTabProps {
  onDirtyChange: (dirty: boolean) => void
}

interface RowView {
  featureId: string
  featureKey: string
  model: string | null
  current: CreditFeatureWeightDto | null
}

export function WeightsTab({ onDirtyChange }: WeightsTabProps) {
  const features = useFeatures()
  const weights = useCreditWeights()
  const unitCosts = useCreditWeightUnitCosts()
  const denominations = useFeatureDenominations()
  const prices = useCreditPrices()
  const publish = usePublishCreditWeights()
  const deleteWeight = useDeleteCreditWeight()

  const [draft, setDraft] = useState<Record<string, string>>({})
  const [extraRows, setExtraRows] = useState<{ featureId: string; model: string }[]>([])
  const [publishOpen, setPublishOpen] = useState(false)
  const [historyPair, setHistoryPair] = useState<WeightPair | null>(null)
  const [addFeatureId, setAddFeatureId] = useState("")
  const [addModel, setAddModel] = useState("")
  const [focusKey, setFocusKey] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<CreditFeatureWeightDto | null>(null)

  const [now] = useState(() => Date.now())
  const allWeights = weights.data ?? []
  const scheduled = allWeights.filter((w) => new Date(w.effectiveFrom).getTime() > now)
  const effective = allWeights.filter((w) => new Date(w.effectiveFrom).getTime() <= now)

  // Current book price per denomination, to translate weights into money
  const currentPriceByDenomination = new Map<string, CreditPriceDto>()
  for (const row of prices.data ?? []) {
    if (new Date(row.effectiveFrom).getTime() > now) continue
    const existing = currentPriceByDenomination.get(row.denomination)
    if (!existing || row.effectiveFrom > existing.effectiveFrom)
      currentPriceByDenomination.set(row.denomination, row)
  }
  const priceForFeature = (featureId: string): CreditPriceDto | undefined => {
    const denomination = denominations.data?.[featureId]
    return denomination ? currentPriceByDenomination.get(denomination) : undefined
  }

  const currentByPair = new Map<string, CreditFeatureWeightDto>()
  for (const row of effective) {
    const key = pairKey(row.featureId, row.model)
    const existing = currentByPair.get(key)
    if (!existing || row.effectiveFrom > existing.effectiveFrom) currentByPair.set(key, row)
  }

  const featureList = features.data ?? []
  const keyById = new Map(featureList.map((f) => [f.id ?? "", f.key ?? ""]))
  const seen = new Set<string>()
  const rows: RowView[] = []
  const push = (featureId: string, featureKey: string, model: string | null) => {
    const key = pairKey(featureId, model)
    if (seen.has(key)) return
    seen.add(key)
    rows.push({ featureId, featureKey, model, current: currentByPair.get(key) ?? null })
  }
  for (const f of featureList) {
    if (f.id && f.key) push(f.id, f.key, null)
  }
  for (const w of allWeights) {
    push(w.featureId, w.featureKey, w.model)
  }
  for (const extra of extraRows) {
    push(extra.featureId, keyById.get(extra.featureId) ?? "", extra.model)
  }
  rows.sort(
    (a, b) =>
      a.featureKey.localeCompare(b.featureKey) ||
      (a.model === null ? -1 : b.model === null ? 1 : a.model.localeCompare(b.model)),
  )

  const changes: WeightChange[] = []
  const invalidKeys = new Set<string>()
  for (const row of rows) {
    const key = pairKey(row.featureId, row.model)
    const raw = draft[key]
    if (raw === undefined || raw.trim() === "") continue
    const parsed = creditWeightValueSchema.safeParse(raw)
    if (!parsed.success) {
      invalidKeys.add(key)
      continue
    }
    const baseline = row.current ? Number(row.current.creditsPerUnit) : null
    if (baseline === null || parsed.data !== baseline) {
      changes.push({
        featureId: row.featureId,
        featureKey: row.featureKey,
        model: row.model,
        from: baseline,
        to: parsed.data,
      })
    }
  }

  const dirty = changes.length > 0 || invalidKeys.size > 0
  useEffect(() => onDirtyChange(dirty), [dirty, onDirtyChange])

  const batchGroups = new Map<string, CreditFeatureWeightDto[]>()
  for (const row of scheduled) {
    const list = batchGroups.get(row.effectiveFrom) ?? []
    list.push(row)
    batchGroups.set(row.effectiveFrom, list)
  }
  const scheduledBatches = [...batchGroups.entries()].sort(([a], [b]) => a.localeCompare(b))

  const discard = () => {
    setDraft({})
    setExtraRows([])
  }

  const addRow = () => {
    const model = addModel.trim()
    if (!addFeatureId || !model) return
    setExtraRows((prev) =>
      prev.some((r) => r.featureId === addFeatureId && r.model === model)
        ? prev
        : [...prev, { featureId: addFeatureId, model }],
    )
    setFocusKey(pairKey(addFeatureId, model))
    setAddModel("")
  }

  const handlePublish = (effectiveFromIso: string) => {
    publish.mutate(
      {
        effectiveFrom: effectiveFromIso,
        entries: changes.map((c) => ({
          featureId: c.featureId,
          model: c.model ?? undefined,
          creditsPerUnit: c.to,
        })),
      },
      {
        onSuccess: () => {
          setPublishOpen(false)
          discard()
          toast.add({
            title: "Tariff published",
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

  const handleDelete = (row: CreditFeatureWeightDto) => {
    deleteWeight.mutate(row.id, {
      onSuccess: () => {
        setDeleteTarget(null)
        toast.add({ title: "Scheduled weight deleted" })
      },
      onError: (error) => {
        setDeleteTarget(null)
        toast.add({ title: "Delete failed", description: error.message })
      },
    })
  }

  if (features.isPending || weights.isPending) {
    return (
      <div className="flex flex-col gap-2">
        <Skeleton className="h-9 w-full" />
        <Skeleton className="h-9 w-full" />
        <Skeleton className="h-9 w-full" />
      </div>
    )
  }

  if ((features.data ?? []).length === 0) {
    return (
      <div className="flex flex-col gap-4">
        <TwoDialsExplainer dial="weights" />
        <Empty>
          <EmptyHeader>
            <EmptyTitle>No features to weight</EmptyTitle>
            <EmptyDescription>
              Every usage unit burns 1 credit until you set weights. Create features to customize
              — e.g. make one heavy feature burn 5 credits per call while everything else stays
              at 1.
            </EmptyDescription>
          </EmptyHeader>
        </Empty>
      </div>
    )
  }

  const featureItems = (features.data ?? [])
    .filter((f) => f.id && f.key)
    .map((f) => ({ label: f.key ?? "", value: f.id ?? "" }))

  return (
    <div className="flex flex-col gap-4">
      <TwoDialsExplainer dial="weights" />
      {scheduledBatches.length > 0 && (
        <div className="flex flex-col gap-3 rounded-lg border bg-muted/50 p-4">
          <div className="flex items-center gap-2">
            <h2 className="text-sm font-medium">Scheduled tariff changes</h2>
            <Badge variant="outline">{scheduled.length}</Badge>
          </div>
          {scheduledBatches.map(([effectiveFrom, batch]) => (
            <div key={effectiveFrom} className="flex flex-col gap-1">
              <p className="text-xs text-muted-foreground">
                {batch.length} {batch.length === 1 ? "weight changes" : "weights change"}{" "}
                {formatUtc(effectiveFrom)}
              </p>
              {batch.map((row) => (
                <div key={row.id} className="flex items-center gap-2 text-sm">
                  <span className="font-mono">
                    {row.featureKey}
                    {row.model ? ` (${row.model})` : ""}: {row.creditsPerUnit}
                  </span>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    aria-label="Delete scheduled weight"
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
              <TableHead>Feature</TableHead>
              <TableHead>Model</TableHead>
              <TableHead>Credits / unit</TableHead>
              <TableHead>≈ Customer pays / unit</TableHead>
              <TableHead>Avg cost / unit</TableHead>
              <TableHead>Effective since</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row) => {
              const key = pairKey(row.featureId, row.model)
              const baseline = row.current ? String(row.current.creditsPerUnit) : "1"
              const value = draft[key] ?? baseline
              const invalid = invalidKeys.has(key)
              const edited = changes.some(
                (c) => c.featureId === row.featureId && c.model === row.model,
              )
              // Live money preview: the weight in the input (when valid) × the
              // current book price for this feature's denomination.
              const price = priceForFeature(row.featureId)
              const parsedValue = creditWeightValueSchema.safeParse(value)
              const customerPays =
                price && parsedValue.success
                  ? formatMoney(parsedValue.data * price.pricePerCredit, price.currency)
                  : null
              return (
                <TableRow key={key}>
                  <TableCell className="font-mono text-xs">{row.featureKey}</TableCell>
                  <TableCell>
                    {row.model ? (
                      <span className="font-mono text-xs">{row.model}</span>
                    ) : (
                      <span className="text-xs text-muted-foreground">All models (default)</span>
                    )}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Input
                        ref={(el) => {
                          if (el && focusKey === key) {
                            el.focus()
                            el.select()
                            setFocusKey(null)
                          }
                        }}
                        className={
                          edited
                            ? "w-24 border-primary font-mono tabular-nums"
                            : "w-24 font-mono tabular-nums"
                        }
                        aria-label={`Weight for ${row.featureKey}${row.model ? ` (${row.model})` : ""}`}
                        aria-invalid={invalid}
                        value={value}
                        onChange={(e) => setDraft((d) => ({ ...d, [key]: e.target.value }))}
                      />
                      {edited ? (
                        <Badge variant="outline" className="border-primary text-primary">
                          Pending
                        </Badge>
                      ) : (
                        !row.current && <Badge variant="outline">Default</Badge>
                      )}
                    </div>
                    {invalid && (
                      <p className="mt-1 text-xs text-destructive">
                        Positive, up to 6 decimals, max 1,000,000.
                      </p>
                    )}
                  </TableCell>
                  <TableCell className="font-mono text-xs tabular-nums">
                    {customerPays ? (
                      <span className={edited ? "text-primary" : undefined}>{customerPays}</span>
                    ) : (
                      <span
                        className="text-muted-foreground"
                        title={
                          price
                            ? undefined // weight input invalid — the error under it explains
                            : "Publish a price for this feature's denomination on the Pricing tab to see money here."
                        }
                      >
                        —
                      </span>
                    )}
                  </TableCell>
                  <TableCell className="font-mono text-xs tabular-nums">
                    {formatUnitCost(unitCosts.data?.[key])}
                  </TableCell>
                  <TableCell className="text-xs text-muted-foreground">
                    {row.current ? formatUtc(row.current.effectiveFrom) : "—"}
                  </TableCell>
                  <TableCell>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label="Weight history"
                      onClick={() =>
                        setHistoryPair({
                          featureId: row.featureId,
                          featureKey: row.featureKey,
                          model: row.model,
                        })
                      }
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

      <div className="flex flex-wrap items-end gap-2 rounded-lg border p-3">
        <Field className="w-fit">
          <FieldLabel htmlFor="add-weight-feature">Feature</FieldLabel>
          <Select
            items={featureItems}
            value={addFeatureId || null}
            onValueChange={(v) => setAddFeatureId((v as string) ?? "")}
          >
            <SelectTrigger id="add-weight-feature" className="w-48">
              <SelectValue placeholder="Select feature" />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {featureItems.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
        </Field>
        <Field className="w-fit">
          <FieldLabel htmlFor="add-weight-model">Model</FieldLabel>
          <Input
            id="add-weight-model"
            className="w-48 font-mono"
            placeholder="gpt-4.1"
            value={addModel}
            onChange={(e) => setAddModel(e.target.value)}
          />
        </Field>
        <Button variant="outline" onClick={addRow} disabled={!addFeatureId || !addModel.trim()}>
          <Plus data-icon="inline-start" />
          Add model weight
        </Button>
        <p className="basis-full text-xs text-muted-foreground">
          Model must exactly match the model string sent on events. Weights allow up to 6 decimals
          — if you need 0.1, your credit unit is 10× too large.
        </p>
      </div>

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
            <DialogTitle>Delete scheduled weight</DialogTitle>
            <DialogDescription>
              {deleteTarget && (
                <>
                  <span className="font-mono">
                    {deleteTarget.featureKey}
                    {deleteTarget.model ? ` (${deleteTarget.model})` : ""}: {deleteTarget.creditsPerUnit}
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
              disabled={deleteWeight.isPending}
              onClick={() => deleteTarget && handleDelete(deleteTarget)}
            >
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <PublishWeightsDialog
        key={String(publishOpen)}
        open={publishOpen}
        onOpenChange={setPublishOpen}
        changes={changes}
        isPending={publish.isPending}
        onSubmit={handlePublish}
      />
      <WeightHistorySheet
        key={historyPair ? pairKey(historyPair.featureId, historyPair.model) : "closed"}
        pair={historyPair}
        onOpenChange={(open) => !open && setHistoryPair(null)}
      />
    </div>
  )
}
