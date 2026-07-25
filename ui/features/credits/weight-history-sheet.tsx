"use client"

import { Badge } from "@/components/ui/badge"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { useState } from "react"

import { Skeleton } from "@/components/ui/skeleton"
import { formatDateTime } from "@/lib/format"
import { useCreditWeightHistory } from "./queries"
import { formatUtc } from "./weight-utils"

export interface WeightPair {
  featureId: string
  featureKey: string
  model: string | null
}

interface WeightHistorySheetProps {
  pair: WeightPair | null
  onOpenChange: (open: boolean) => void
}

export function WeightHistorySheet({ pair, onOpenChange }: WeightHistorySheetProps) {
  const history = useCreditWeightHistory(pair?.featureId, pair?.model ?? null)
  const [now] = useState(() => Date.now())
  const rows = history.data ?? []
  const currentId = rows
    .filter((row) => new Date(row.effectiveFrom).getTime() <= now)
    .sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom))[0]?.id

  return (
    <Sheet open={!!pair} onOpenChange={onOpenChange}>
      <SheetContent>
        <SheetHeader>
          <SheetTitle>
            {pair?.featureKey}
            {pair?.model ? ` (${pair.model})` : ""}
          </SheetTitle>
          <SheetDescription>
            {pair?.model ? "Model-specific weight history." : "Feature default weight history."}
          </SheetDescription>
        </SheetHeader>
        <div className="flex flex-col gap-3 px-4">
          {history.isPending && (
            <>
              <Skeleton className="h-14 w-full" />
              <Skeleton className="h-14 w-full" />
            </>
          )}
          {!history.isPending && rows.length === 0 && (
            <p className="text-sm text-muted-foreground">
              No explicit weight rows — this pair burns at the identity default of 1.0.
            </p>
          )}
          {rows.map((row) => {
            const scheduled = new Date(row.effectiveFrom).getTime() > now
            return (
              <div key={row.id} className="flex flex-col gap-1 rounded-lg border p-3">
                <div className="flex items-center justify-between">
                  <span className="font-mono text-sm tabular-nums">{row.creditsPerUnit}</span>
                  {scheduled ? (
                    <Badge variant="outline">Scheduled</Badge>
                  ) : row.id === currentId ? (
                    <Badge>Current</Badge>
                  ) : (
                    <Badge variant="secondary">Superseded</Badge>
                  )}
                </div>
                <p className="text-xs text-muted-foreground">
                  Effective {formatUtc(row.effectiveFrom)}
                </p>
                <p className="text-xs text-muted-foreground">
                  Published {formatDateTime(row.createdAt)}
                  {row.createdBy ? ` by ${row.createdBy.slice(0, 8)}` : ""}
                </p>
              </div>
            )
          })}
        </div>
      </SheetContent>
    </Sheet>
  )
}
