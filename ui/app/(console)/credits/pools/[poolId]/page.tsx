"use client"

import { use, useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import { Plus } from "lucide-react"

import { DataTable } from "@/components/data-table"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { toast } from "@/components/ui/toast"
import type { CreditGrantDto, CreditTransactionDto } from "@/lib/api/types"
import { formatDateTime, formatNumber } from "@/lib/format"
import { GrantForm } from "@/features/credits/grant-form"
import { useCreateCreditGrant } from "@/features/credits/mutations"
import {
  useCreditPool,
  useCreditPoolGrants,
  useCreditPoolTransactions,
} from "@/features/credits/queries"

const grantColumns: ColumnDef<CreditGrantDto>[] = [
  { accessorKey: "grantType", header: "Type" },
  {
    accessorKey: "amount",
    header: "Amount",
    cell: ({ row }) => (
      <span className="font-mono tabular-nums">{formatNumber(row.original.amount)}</span>
    ),
  },
  {
    accessorKey: "remaining",
    header: "Remaining",
    cell: ({ row }) => (
      <span className="font-mono tabular-nums">{formatNumber(row.original.remaining)}</span>
    ),
  },
  {
    accessorKey: "expiresAt",
    header: "Expires",
    cell: ({ row }) => formatDateTime(row.original.expiresAt),
  },
  { accessorKey: "description", header: "Description" },
  {
    accessorKey: "createdAt",
    header: "Created",
    cell: ({ row }) => formatDateTime(row.original.createdAt),
  },
]

const transactionColumns: ColumnDef<CreditTransactionDto>[] = [
  { accessorKey: "transactionType", header: "Type" },
  {
    accessorKey: "amount",
    header: "Amount",
    cell: ({ row }) => (
      <span className="font-mono tabular-nums">{formatNumber(row.original.amount)}</span>
    ),
  },
  {
    id: "balance",
    header: "Balance",
    cell: ({ row }) => (
      <span className="font-mono tabular-nums">
        {formatNumber(row.original.balanceBefore)} → {formatNumber(row.original.balanceAfter)}
      </span>
    ),
  },
  { accessorKey: "description", header: "Description" },
  {
    accessorKey: "createdAt",
    header: "Created",
    cell: ({ row }) => formatDateTime(row.original.createdAt),
  },
]

export default function CreditPoolDetailPage({
  params,
}: {
  params: Promise<{ poolId: string }>
}) {
  const { poolId } = use(params)
  const pool = useCreditPool(poolId)
  const grants = useCreditPoolGrants(poolId)
  const transactions = useCreditPoolTransactions(poolId)
  const createGrant = useCreateCreditGrant(poolId)
  const [grantOpen, setGrantOpen] = useState(false)

  if (pool.isPending) {
    return (
      <div className="flex flex-col gap-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-32 w-full" />
      </div>
    )
  }

  const stats = [
    { label: "Balance", value: pool.data?.balance },
    { label: "Granted", value: pool.data?.totalGranted },
    { label: "Consumed", value: pool.data?.totalConsumed },
    { label: "Expired", value: pool.data?.totalExpired },
  ]

  return (
    <>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="text-xl font-semibold tracking-tight">{pool.data?.name ?? "Pool"}</h1>
          <span className="font-mono text-xs text-muted-foreground">{pool.data?.denomination}</span>
          {pool.data?.status && (
            <Badge variant={pool.data.status === "ACTIVE" ? "default" : "secondary"}>
              {pool.data.status}
            </Badge>
          )}
        </div>
        <Button onClick={() => setGrantOpen(true)}>
          <Plus data-icon="inline-start" />
          New grant
        </Button>
      </div>

      <div className="grid gap-4 md:grid-cols-4">
        {stats.map((stat) => (
          <Card key={stat.label}>
            <CardHeader>
              <CardDescription>{stat.label}</CardDescription>
              <CardTitle className="font-mono text-2xl tabular-nums">
                {formatNumber(stat.value)}
              </CardTitle>
            </CardHeader>
            <CardContent className="text-sm text-muted-foreground">
              {pool.data?.denomination}
            </CardContent>
          </Card>
        ))}
      </div>

      <Tabs defaultValue="grants">
        <TabsList>
          <TabsTrigger value="grants">Grants</TabsTrigger>
          <TabsTrigger value="transactions">Transactions</TabsTrigger>
        </TabsList>
        <TabsContent value="grants">
          <DataTable
            columns={grantColumns}
            data={grants.data ?? []}
            isLoading={grants.isPending}
            emptyTitle="No grants"
            emptyDescription="Grant credits to give this pool a balance."
          />
        </TabsContent>
        <TabsContent value="transactions">
          <DataTable
            columns={transactionColumns}
            data={transactions.data ?? []}
            isLoading={transactions.isPending}
            emptyTitle="No transactions"
          />
        </TabsContent>
      </Tabs>

      <Sheet open={grantOpen} onOpenChange={setGrantOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>New grant</SheetTitle>
            <SheetDescription>Add credits to this pool.</SheetDescription>
          </SheetHeader>
          <div className="px-4">
            <GrantForm
              isPending={createGrant.isPending}
              onSubmit={(input) =>
                createGrant.mutate(input, {
                  onSuccess: () => {
                    setGrantOpen(false)
                    toast.add({ title: "Credits granted" })
                  },
                  onError: (error) =>
                    toast.add({ title: "Grant failed", description: error.message }),
                })
              }
            />
          </div>
        </SheetContent>
      </Sheet>
    </>
  )
}
