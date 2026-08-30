"use client"

import { createContext, useContext, useState } from "react"

import { daysAgo } from "@/features/spend/format"

// One window for the spend reports, so navigating between them does not
// silently reset it. `to` is exclusive everywhere here. Reconcile keeps its
// own range: it answers a different question (does this vendor invoice
// match?), defaults to the last full month, and its window is inclusive.
interface SpendRange {
  from: string
  to: string
  setRange: (range: { from: string; to: string }) => void
}

const SpendRangeContext = createContext<SpendRange | null>(null)

export function SpendRangeProvider({
  children,
}: {
  children: React.ReactNode
}) {
  const [range, setRange] = useState({ from: daysAgo(29), to: daysAgo(-1) })

  return (
    <SpendRangeContext value={{ ...range, setRange }}>
      {children}
    </SpendRangeContext>
  )
}

export function useSpendRange() {
  const context = useContext(SpendRangeContext)
  if (!context) throw new Error("useSpendRange outside SpendRangeProvider")
  return context
}
