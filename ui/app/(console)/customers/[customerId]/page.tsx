"use client"

import { use, useState } from "react"
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
import { formatCurrency, formatDate } from "@/lib/format"
import { CustomerForm } from "@/features/customers/customer-form"
import { useUpdateCustomer } from "@/features/customers/mutations"
import {
  useCustomer,
  useCustomerCreditPools,
  useCustomerSubscriptions,
} from "@/features/customers/queries"

export default function CustomerDetailPage({
  params,
}: {
  params: Promise<{ customerId: string }>
}) {
  const { customerId } = use(params)
  const customer = useCustomer(customerId)
  const subscriptions = useCustomerSubscriptions(customerId)
  const pools = useCustomerCreditPools(customerId)
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
                  <TableRow key={pool.id}>
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
