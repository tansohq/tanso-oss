"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import type { ColumnDef } from "@tanstack/react-table"
import { Plus } from "lucide-react"

import { DataTable } from "@/components/data-table"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { toast } from "@/components/ui/toast"
import type { PlanDto } from "@/lib/api/types"
import { formatCurrency, formatDate } from "@/lib/format"
import { useCreatePlan } from "@/features/plans/mutations"
import { PlanForm } from "@/features/plans/plan-form"
import { usePlans } from "@/features/plans/queries"

const columns: ColumnDef<PlanDto>[] = [
  { accessorKey: "name", header: "Name" },
  {
    accessorKey: "key",
    header: "Key",
    cell: ({ row }) => <span className="font-mono text-xs">{row.original.key}</span>,
  },
  {
    accessorKey: "priceAmount",
    header: "Price",
    cell: ({ row }) => (
      <span className="font-mono tabular-nums">{formatCurrency(row.original.priceAmount)}</span>
    ),
  },
  {
    accessorKey: "intervalMonths",
    header: "Interval",
    cell: ({ row }) =>
      row.original.intervalMonths === "1" ? "Monthly" : `Every ${row.original.intervalMonths} mo`,
  },
  {
    accessorKey: "billingTiming",
    header: "Billing",
    cell: ({ row }) =>
      row.original.billingTiming === "IN_ADVANCE" ? "In advance" : "In arrears",
  },
  {
    accessorKey: "status",
    header: "Status",
    cell: ({ row }) => (
      <Badge variant={row.original.status === "ACTIVE" ? "default" : "secondary"}>
        {row.original.status}
      </Badge>
    ),
  },
  {
    accessorKey: "createdAt",
    header: "Created",
    cell: ({ row }) => formatDate(row.original.createdAt),
  },
]

export default function PlansPage() {
  const router = useRouter()
  const plans = usePlans()
  const createPlan = useCreatePlan()
  const [createOpen, setCreateOpen] = useState(false)

  return (
    <>
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Plans</h1>
          <p className="text-sm text-muted-foreground">Product bundles you sell.</p>
        </div>
        <Button onClick={() => setCreateOpen(true)}>
          <Plus data-icon="inline-start" />
          New plan
        </Button>
      </div>
      <DataTable
        columns={columns}
        data={plans.data ?? []}
        isLoading={plans.isPending}
        emptyTitle="No plans yet"
        emptyDescription="Create your first plan to start monetizing features."
        onRowClick={(plan) => router.push(`/plans/${plan.id}`)}
      />
      <Sheet open={createOpen} onOpenChange={setCreateOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>New plan</SheetTitle>
            <SheetDescription>Define a product bundle customers subscribe to.</SheetDescription>
          </SheetHeader>
          <div className="px-4">
            <PlanForm
              isPending={createPlan.isPending}
              onSubmit={(input) =>
                createPlan.mutate(input, {
                  onSuccess: () => {
                    setCreateOpen(false)
                    toast.add({ title: "Plan created" })
                  },
                  onError: (error) => toast.add({ title: "Create failed", description: error.message }),
                })
              }
            />
          </div>
        </SheetContent>
      </Sheet>
    </>
  )
}
