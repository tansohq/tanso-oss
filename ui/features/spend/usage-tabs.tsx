"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"

import { cn } from "@/lib/utils"

// Usage and Savings read the same USAGE_API buckets over the same window —
// Savings is a lens on the model table, not a separate destination. Kept as
// two routes so each page stays deep-linkable and under 400 lines.
const tabs = [
  { title: "Usage", href: "/spend/usage" },
  { title: "Savings", href: "/spend/savings" },
]

export function UsageTabs() {
  const pathname = usePathname()

  return (
    <div className="flex gap-1 border-b">
      {tabs.map((tab) => (
        <Link
          key={tab.href}
          href={tab.href}
          className={cn(
            "-mb-px border-b-2 px-3 py-2 text-sm transition-colors",
            pathname === tab.href
              ? "border-primary font-medium text-foreground"
              : "border-transparent text-muted-foreground hover:text-foreground"
          )}
        >
          {tab.title}
        </Link>
      ))}
    </div>
  )
}
