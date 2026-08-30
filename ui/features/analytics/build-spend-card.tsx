"use client"

import Link from "next/link"
import { ArrowRight } from "lucide-react"

import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { daysAgo, formatCents } from "@/features/spend/format"
import {
  isBuildSideOff,
  useSpendUsage,
  useVendorConnections,
} from "@/features/spend/queries"

// The build half of Overview: what the operator's own AI cost, so the home
// page reads as both directions rather than as a billing tool.
export function BuildSpendCard() {
  const connections = useVendorConnections()
  const usage = useSpendUsage(daysAgo(29), daysAgo(-1))

  if (isBuildSideOff(connections.error) || connections.isPending) return null

  if ((connections.data ?? []).length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Internal AI spend</CardTitle>
          <CardDescription>
            Connect a vendor to see what your own AI costs next to what it
            earns.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Link
            href="/spend/connections"
            className="inline-flex items-center gap-1 text-sm font-medium hover:underline"
          >
            Connect a vendor <ArrowRight className="size-4" />
          </Link>
        </CardContent>
      </Card>
    )
  }

  const totals = usage.data?.totals

  return (
    <Card>
      <CardHeader>
        <CardDescription>Internal AI spend · last 30 days</CardDescription>
        <CardTitle className="font-mono text-2xl tabular-nums">
          {formatCents(totals?.vendorCostCents)}
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-wrap items-center justify-between gap-2 text-sm text-muted-foreground">
        <span>
          {formatCents(totals?.meteredCostCents)} metered by the price book
        </span>
        <Link
          href="/spend/usage"
          className="inline-flex items-center gap-1 font-medium text-foreground hover:underline"
        >
          Internal spend <ArrowRight className="size-4" />
        </Link>
      </CardContent>
    </Card>
  )
}
