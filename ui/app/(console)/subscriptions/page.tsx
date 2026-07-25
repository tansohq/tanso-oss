"use client"

import { useState } from "react"
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { toast } from "@/components/ui/toast"
import type { SubscriptionDto, SubscriptionScheduledChangeDto } from "@/lib/api/types"
import { formatDate } from "@/lib/format"
import { usePlans } from "@/features/plans/queries"
import { useCreateSubscription } from "@/features/subscriptions/mutations"
import {
  useCustomerOptions,
  useScheduledCancellations,
  useScheduledChanges,
  useSubscriptions,
} from "@/features/subscriptions/queries"
import { SubscriptionForm } from "@/features/subscriptions/subscription-form"

function customerLabel(subscription: SubscriptionDto): string {
  const customer = subscription.customer
  if (!customer) return "—"
  const name = [customer.firstName, customer.lastName].filter(Boolean).join(" ")
  return name || customer.email || customer.id || "—"
}

const subscriptionColumns: ColumnDef<SubscriptionDto>[] = [
  {
    id: "customer",
    header: "Customer",
    cell: ({ row }) => (
      <div className="flex flex-col">
        <span>{customerLabel(row.original)}</span>
        <span className="text-xs text-muted-foreground">{row.original.customer?.email}</span>
      </div>
    ),
  },
  {
    id: "plan",
    header: "Plan",
    cell: ({ row }) => row.original.plan?.name ?? "—",
  },
  {
    accessorKey: "isActive",
    header: "Status",
    cell: ({ row }) => (
      <Badge variant={row.original.isActive ? "default" : "secondary"}>
        {row.original.isActive ? "Active" : "Inactive"}
      </Badge>
    ),
  },
  {
    id: "period",
    header: "Current period",
    cell: ({ row }) => (
      <span className="text-sm">
        {formatDate(row.original.currentPeriodStart)} → {formatDate(row.original.currentPeriodEnd)}
      </span>
    ),
  },
  {
    accessorKey: "cancelMode",
    header: "Cancel mode",
    cell: ({ row }) => row.original.cancelMode ?? "—",
  },
  {
    accessorKey: "billingAnchorDay",
    header: "Anchor day",
    cell: ({ row }) => (
      <span className="font-mono tabular-nums">{row.original.billingAnchorDay ?? "—"}</span>
    ),
  },
]

const changeColumns: ColumnDef<SubscriptionScheduledChangeDto>[] = [
  {
    id: "fromPlan",
    header: "From plan",
    cell: ({ row }) => row.original.fromPlan?.name ?? "—",
  },
  {
    id: "toPlan",
    header: "To plan",
    cell: ({ row }) => row.original.toPlan?.name ?? "—",
  },
  { accessorKey: "type", header: "Type" },
  {
    accessorKey: "status",
    header: "Status",
    cell: ({ row }) => <Badge variant="secondary">{row.original.status}</Badge>,
  },
  {
    accessorKey: "effectiveAt",
    header: "Effective",
    cell: ({ row }) => formatDate(row.original.effectiveAt),
  },
]

export default function SubscriptionsPage() {
  const subscriptions = useSubscriptions()
  const scheduledChanges = useScheduledChanges()
  const scheduledCancellations = useScheduledCancellations()
  const customers = useCustomerOptions()
  const plans = usePlans()
  const createSubscription = useCreateSubscription()
  const [createOpen, setCreateOpen] = useState(false)

  const customerItems = (customers.data?.customers ?? []).map((c) => {
    const name = [c.firstName, c.lastName].filter(Boolean).join(" ")
    return { label: name ? `${name} (${c.email})` : (c.email ?? c.id ?? ""), value: c.id ?? "" }
  })
  const planItems = (plans.data ?? []).map((p) => ({ label: p.name ?? p.key ?? "", value: p.id ?? "" }))

  return (
    <>
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Subscriptions</h1>
          <p className="text-sm text-muted-foreground">Customers on plans, and what changes next.</p>
        </div>
        <Button onClick={() => setCreateOpen(true)}>
          <Plus data-icon="inline-start" />
          New subscription
        </Button>
      </div>
      <Tabs defaultValue="all">
        <TabsList>
          <TabsTrigger value="all">All</TabsTrigger>
          <TabsTrigger value="changes">Scheduled changes</TabsTrigger>
          <TabsTrigger value="cancellations">Scheduled cancellations</TabsTrigger>
        </TabsList>
        <TabsContent value="all">
          <DataTable
            columns={subscriptionColumns}
            data={subscriptions.data ?? []}
            isLoading={subscriptions.isPending}
            emptyTitle="No subscriptions"
            emptyDescription="Create a subscription to put a customer on a plan."
          />
        </TabsContent>
        <TabsContent value="changes">
          <DataTable
            columns={changeColumns}
            data={scheduledChanges.data ?? []}
            isLoading={scheduledChanges.isPending}
            emptyTitle="No scheduled changes"
          />
        </TabsContent>
        <TabsContent value="cancellations">
          <DataTable
            columns={subscriptionColumns}
            data={scheduledCancellations.data ?? []}
            isLoading={scheduledCancellations.isPending}
            emptyTitle="No scheduled cancellations"
          />
        </TabsContent>
      </Tabs>
      <Sheet open={createOpen} onOpenChange={setCreateOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>New subscription</SheetTitle>
            <SheetDescription>Put a customer on a plan.</SheetDescription>
          </SheetHeader>
          <div className="px-4">
            <SubscriptionForm
              customers={customerItems}
              plans={planItems}
              isPending={createSubscription.isPending}
              onSubmit={(input) =>
                createSubscription.mutate(input, {
                  onSuccess: () => {
                    setCreateOpen(false)
                    toast.add({ title: "Subscription created" })
                  },
                  onError: (error) =>
                    toast.add({ title: "Create failed", description: error.message }),
                })
              }
            />
          </div>
        </SheetContent>
      </Sheet>
    </>
  )
}
