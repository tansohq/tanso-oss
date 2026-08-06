"use client"

import { Badge } from "@/components/ui/badge"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
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
import { useCreditPriceHistory } from "./queries"
import { formatUtc } from "./weight-utils"

interface PriceHistorySheetProps {
  denomination: string | null
  onOpenChange: (open: boolean) => void
}

export function PriceHistorySheet({ denomination, onOpenChange }: PriceHistorySheetProps) {
  const history = useCreditPriceHistory(denomination ?? undefined)
  const [now] = useState(() => Date.now())
  const rows = history.data ?? []
  const currentId = rows
    .filter((row) => new Date(row.effectiveFrom).getTime() <= now)
    .sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom))[0]?.id

  return (
    <Sheet open={!!denomination} onOpenChange={onOpenChange}>
      <SheetContent>
        <SheetHeader>
          <SheetTitle>{denomination}</SheetTitle>
          <SheetDescription>Price-per-credit history for this denomination.</SheetDescription>
        </SheetHeader>
        <div className="flex flex-col gap-3 px-4">
          {history.isPending && (
            <>
              <Skeleton className="h-14 w-full" />
              <Skeleton className="h-14 w-full" />
            </>
          )}
          {!history.isPending && rows.length === 0 && (
            <Empty>
              <EmptyHeader>
                <EmptyTitle>No price rows</EmptyTitle>
                <EmptyDescription>
                  This denomination is unpriced until a price is published.
                </EmptyDescription>
              </EmptyHeader>
            </Empty>
          )}
          {rows.map((row) => {
            const scheduled = new Date(row.effectiveFrom).getTime() > now
            return (
              <div key={row.id} className="flex flex-col gap-1 rounded-lg border p-3">
                <div className="flex items-center justify-between">
                  <span className="font-mono text-sm tabular-nums">
                    {row.pricePerCredit} {row.currency}
                  </span>
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
