"use client"

import { use, useState } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { Pencil } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
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
import { formatCurrency, formatDate, formatDateTime, formatNumber } from "@/lib/format"
import { CustomerForm } from "@/features/customers/customer-form"
import { useUpdateCustomer } from "@/features/customers/mutations"
import {
  useCustomer,
  useCustomerCreditPools,
  useCustomerEvents,
  useCustomerSubscriptions,
  useCustomerUsageTotals,
} from "@/features/customers/queries"
import { useFeatures } from "@/features/features/queries"

export default function CustomerDetailPage({
  params,
}: {
  params: Promise<{ customerId: string }>
}) {
  const { customerId } = use(params)
  const router = useRouter()
  const customer = useCustomer(customerId)
  const subscriptions = useCustomerSubscriptions(customerId)
  const pools = useCustomerCreditPools(customerId)
  const events = useCustomerEvents(customer.data?.customerReferenceId ?? undefined)
  const usage = useCustomerUsageTotals(customerId, customer.data?.customerReferenceId)
  const features = useFeatures()
  const featureById = new Map((features.data ?? []).map((f) => [f.id ?? "", f.key ?? ""]))
  const updateCustomer = useUpdateCustomer(customerId)
  const [editOpen, setEditOpen] = useState(false)

  if (customer.isPending) {
    return (
      <div className="flex flex-col gap-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-40 w-full" />
      </div>
    )
  }

  const name =
    [customer.data?.firstName, customer.data?.lastName].filter(Boolean).join(" ") ||
    customer.data?.email

  return (
    <>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="text-xl font-semibold tracking-tight">{name}</h1>
          {customer.data?.customerReferenceId && (
            <span className="font-mono text-xs text-muted-foreground">
              {customer.data.customerReferenceId}
            </span>
          )}
          {customer.data?.source && <Badge variant="secondary">{customer.data.source}</Badge>}
        </div>
        <Button variant="outline" onClick={() => setEditOpen(true)}>
          <Pencil data-icon="inline-start" />
          Edit customer
        </Button>
      </div>

      <div className="text-sm text-muted-foreground">
        {customer.data?.email}
        {customer.data?.phoneNumber && ` · ${customer.data.phoneNumber}`}
        {customer.data?.createdAt && ` · Customer since ${formatDate(customer.data.createdAt)}`}
      </div>

      <div className="grid gap-4 md:grid-cols-4">
        {[
          { label: "Events", value: formatNumber(usage.data?.eventCount) },
          { label: "Usage units", value: formatNumber(usage.data?.totalUsageUnits) },
          { label: "Cost", value: formatCurrency(usage.data?.totalCost) },
          { label: "Revenue", value: formatCurrency(usage.data?.totalRevenue) },
        ].map((stat) => (
          <Card key={stat.label}>
            <CardHeader>
              <CardDescription>{stat.label}</CardDescription>
              <CardTitle className="font-mono text-2xl tabular-nums">{stat.value}</CardTitle>
            </CardHeader>
            <CardContent className="text-sm text-muted-foreground">All time</CardContent>
          </Card>
        ))}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Recent usage</CardTitle>
          <CardDescription>
            Latest events from this customer.{" "}
            <Link href="/events" className="text-primary underline-offset-4 hover:underline">
              See all events
            </Link>
          </CardDescription>
        </CardHeader>
        <CardContent>
          {events.data?.items?.length ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Occurred</TableHead>
                  <TableHead>Event</TableHead>
                  <TableHead>Feature</TableHead>
                  <TableHead>Model</TableHead>
                  <TableHead className="text-right">Units</TableHead>
                  <TableHead className="text-right">Cost</TableHead>
                  <TableHead className="text-right">Revenue</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {events.data.items.map((event) => (
                  <TableRow key={event.id}>
                    <TableCell className="whitespace-nowrap">
                      {formatDateTime(event.occurredAt)}
                    </TableCell>
                    <TableCell>{event.eventName}</TableCell>
                    <TableCell className="font-mono text-xs">
                      {event.featureKey ?? featureById.get(event.featureId ?? "") ?? "—"}
                    </TableCell>
                    <TableCell className="font-mono text-xs">{event.model ?? "—"}</TableCell>
                    <TableCell className="text-right font-mono tabular-nums">
                      {formatNumber(event.usageUnits)}
                    </TableCell>
                    <TableCell className="text-right font-mono tabular-nums">
                      {formatCurrency(event.costAmount)}
                    </TableCell>
                    <TableCell className="text-right font-mono tabular-nums">
                      {formatCurrency(event.revenueAmount)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : (
            <Empty>
              <EmptyHeader>
                <EmptyTitle>No usage yet</EmptyTitle>
                <EmptyDescription>
                  Events ingested for this customer will appear here.
                </EmptyDescription>
              </EmptyHeader>
            </Empty>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Subscriptions</CardTitle>
          <CardDescription>Plans this customer is on.</CardDescription>
        </CardHeader>
        <CardContent>
          {subscriptions.data?.length ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Plan</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Current period</TableHead>
                  <TableHead>Cancel mode</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {subscriptions.data.map((subscription) => (
                  <TableRow key={subscription.id}>
                    <TableCell>{subscription.plan?.name}</TableCell>
                    <TableCell>
                      <Badge variant={subscription.isActive ? "default" : "secondary"}>
                        {subscription.isActive ? "Active" : "Inactive"}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      {formatDate(subscription.currentPeriodStart)} –{" "}
                      {formatDate(subscription.currentPeriodEnd)}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {subscription.cancelMode ?? "—"}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : (
            <Empty>
              <EmptyHeader>
                <EmptyTitle>No subscriptions</EmptyTitle>
                <EmptyDescription>This customer isn&apos;t on any plan yet.</EmptyDescription>
              </EmptyHeader>
            </Empty>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Credit pools</CardTitle>
          <CardDescription>Prepaid balances this customer can draw down.</CardDescription>
        </CardHeader>
        <CardContent>
          {pools.data?.length ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Denomination</TableHead>
                  <TableHead>Balance</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {pools.data.map((pool) => (
                  <TableRow
                    key={pool.id}
                    className="cursor-pointer"
                    onClick={() => router.push(`/credits/pools/${pool.id}`)}
                  >
                    <TableCell>{pool.name}</TableCell>
                    <TableCell className="text-muted-foreground">{pool.denomination}</TableCell>
                    <TableCell className="font-mono tabular-nums">
                      {pool.denomination === "CURRENCY"
                        ? formatCurrency(pool.balance, pool.currency ?? "USD")
                        : pool.balance}
                    </TableCell>
                    <TableCell>
                      <Badge variant={pool.status === "ACTIVE" ? "default" : "secondary"}>
                        {pool.status}
                      </Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : (
            <Empty>
              <EmptyHeader>
                <EmptyTitle>No credit pools</EmptyTitle>
                <EmptyDescription>
                  Create a pool from the Credits page to grant prepaid credits.
                </EmptyDescription>
              </EmptyHeader>
            </Empty>
          )}
        </CardContent>
      </Card>

      <Sheet open={editOpen} onOpenChange={setEditOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Edit customer</SheetTitle>
            <SheetDescription>Contact and reference details.</SheetDescription>
          </SheetHeader>
          <div className="px-4">
            {customer.data && (
              <CustomerForm
                customer={customer.data}
                isPending={updateCustomer.isPending}
                onSubmit={(input) =>
                  updateCustomer.mutate(input, {
                    onSuccess: () => {
                      setEditOpen(false)
                      toast.add({ title: "Customer updated" })
                    },
                    onError: (error) =>
                      toast.add({ title: "Update failed", description: error.message }),
                  })
                }
              />
            )}
          </div>
        </SheetContent>
      </Sheet>
    </>
  )
}
