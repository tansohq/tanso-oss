"use client"

import { useState } from "react"
import { usePathname } from "next/navigation"

import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { useSpendRange } from "@/features/spend/range-context"

// The pages that read the shared window. Reconcile is deliberately absent —
// it keeps its own last-full-month, inclusive range.
const rangedRoutes = [
  "/spend/usage",
  "/spend/savings",
  "/spend/pnl",
  "/spend/outcomes",
  "/spend/teams",
]

export function SpendRangePicker() {
  const pathname = usePathname()
  const { from, to, setRange } = useSpendRange()
  const [draft, setDraft] = useState({ from, to })

  if (!rangedRoutes.includes(pathname)) return null

  // Date inputs emit every partial value while typing a year; only a complete,
  // ordered range reaches the query key.
  function commit() {
    if (
      /^\d{4}-\d{2}-\d{2}$/.test(draft.from) &&
      /^\d{4}-\d{2}-\d{2}$/.test(draft.to) &&
      draft.from < draft.to
    ) {
      setRange(draft)
    }
  }

  return (
    <div className="ml-auto flex items-end gap-2">
      <div className="grid gap-1">
        <Label htmlFor="spend-from" className="text-xs">
          From
        </Label>
        <Input
          id="spend-from"
          type="date"
          className="h-8"
          value={draft.from}
          onChange={(e) => setDraft({ ...draft, from: e.target.value })}
          onBlur={commit}
          onKeyDown={(e) => e.key === "Enter" && commit()}
        />
      </div>
      <div className="grid gap-1">
        <Label htmlFor="spend-to" className="text-xs">
          To (exclusive)
        </Label>
        <Input
          id="spend-to"
          type="date"
          className="h-8"
          value={draft.to}
          onChange={(e) => setDraft({ ...draft, to: e.target.value })}
          onBlur={commit}
          onKeyDown={(e) => e.key === "Enter" && commit()}
        />
      </div>
    </div>
  )
}
