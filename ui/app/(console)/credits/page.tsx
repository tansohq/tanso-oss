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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { toast } from "@/components/ui/toast"
import type { CreditModelDto, CreditPoolDto } from "@/lib/api/types"
import { formatNumber } from "@/lib/format"
import { CreditModelForm } from "@/features/credits/credit-model-form"
import { CreditPoolForm } from "@/features/credits/credit-pool-form"
import { WeightsTab } from "@/features/credits/weights-tab"
import { useCreateCreditModel, useCreateCreditPool } from "@/features/credits/mutations"
import { useCreditModels, useCreditPools } from "@/features/credits/queries"
import { useCustomerOptions } from "@/features/subscriptions/queries"

const modelColumns: ColumnDef<CreditModelDto>[] = [
  { accessorKey: "name", header: "Name" },
  {
    accessorKey: "denomination",
    header: "Denomination",
    cell: ({ row }) => <span className="font-mono text-xs">{row.original.denomination}</span>,
  },
  {
    accessorKey: "hardLimit",
    header: "Hard limit",
    cell: ({ row }) => (
      <Badge variant={row.original.hardLimit ? "default" : "secondary"}>
        {row.original.hardLimit ? "Yes" : "No"}
      </Badge>
    ),
  },
  { accessorKey: "rolloverPolicy", header: "Rollover" },
  {
    accessorKey: "rolloverCap",
    header: "Cap",
    cell: ({ row }) => (
      <span className="font-mono tabular-nums">{formatNumber(row.original.rolloverCap)}</span>
    ),
  },
]

const poolColumns: ColumnDef<CreditPoolDto>[] = [
  { accessorKey: "name", header: "Name" },
  {
    accessorKey: "denomination",
    header: "Denomination",
    cell: ({ row }) => <span className="font-mono text-xs">{row.original.denomination}</span>,
  },
  {
    accessorKey: "balance",
    header: "Balance",
    cell: ({ row }) => (
      <span className="font-mono tabular-nums">{formatNumber(row.original.balance)}</span>
    ),
  },
  {
    accessorKey: "totalGranted",
    header: "Granted",
    cell: ({ row }) => (
      <span className="font-mono tabular-nums">{formatNumber(row.original.totalGranted)}</span>
    ),
  },
  {
    accessorKey: "totalConsumed",
    header: "Consumed",
    cell: ({ row }) => (
      <span className="font-mono tabular-nums">{formatNumber(row.original.totalConsumed)}</span>
    ),
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
]

export default function CreditsPage() {
  const router = useRouter()
  const models = useCreditModels()
  const pools = useCreditPools()
  const customers = useCustomerOptions()
  const createModel = useCreateCreditModel()
  const createPool = useCreateCreditPool()
  const [modelOpen, setModelOpen] = useState(false)
  const [poolOpen, setPoolOpen] = useState(false)
  const [tab, setTab] = useState("models")
  const [weightsDirty, setWeightsDirty] = useState(false)

  const customerItems = (customers.data?.customers ?? []).map((c) => {
    const name = [c.firstName, c.lastName].filter(Boolean).join(" ")
    return { label: name ? `${name} (${c.email})` : (c.email ?? c.id ?? ""), value: c.id ?? "" }
  })

  return (
    <>
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Credits</h1>
          <p className="text-sm text-muted-foreground">
            Credit models define units; pools hold customer balances.
          </p>
        </div>
        {tab === "models" && (
          <Button onClick={() => setModelOpen(true)}>
            <Plus data-icon="inline-start" />
            New model
          </Button>
        )}
        {tab === "pools" && (
          <Button onClick={() => setPoolOpen(true)}>
            <Plus data-icon="inline-start" />
            New pool
          </Button>
        )}
      </div>
      <Tabs
        value={tab}
        onValueChange={(value) => {
          if (
            tab === "weights" &&
            weightsDirty &&
            !window.confirm("Discard unsaved weight changes?")
          )
            return
          setTab(value as string)
        }}
      >
        <TabsList>
          <TabsTrigger value="models">Models</TabsTrigger>
          <TabsTrigger value="pools">Pools</TabsTrigger>
          <TabsTrigger value="weights">Weights</TabsTrigger>
        </TabsList>
        <TabsContent value="models">
          <DataTable
            columns={modelColumns}
            data={models.data ?? []}
            isLoading={models.isPending}
            emptyTitle="No credit models"
            emptyDescription="Define a credit model to allocate credits through plans."
          />
        </TabsContent>
        <TabsContent value="pools">
          <DataTable
            columns={poolColumns}
            data={pools.data ?? []}
            isLoading={pools.isPending}
            emptyTitle="No credit pools"
            emptyDescription="Pools are created from plan allocations or manually."
            onRowClick={(pool) => router.push(`/credits/pools/${pool.id}`)}
          />
        </TabsContent>
        <TabsContent value="weights">
          <WeightsTab onDirtyChange={setWeightsDirty} />
        </TabsContent>
      </Tabs>
      <Sheet open={modelOpen} onOpenChange={setModelOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>New credit model</SheetTitle>
            <SheetDescription>A unit of prepaid value, like tokens or credits.</SheetDescription>
          </SheetHeader>
          <div className="px-4">
            <CreditModelForm
              isPending={createModel.isPending}
              onSubmit={(input) =>
                createModel.mutate(input, {
                  onSuccess: () => {
                    setModelOpen(false)
                    toast.add({ title: "Credit model created" })
                  },
                  onError: (error) =>
                    toast.add({ title: "Create failed", description: error.message }),
                })
              }
            />
          </div>
        </SheetContent>
      </Sheet>
      <Sheet open={poolOpen} onOpenChange={setPoolOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>New credit pool</SheetTitle>
            <SheetDescription>A balance of credits held by a customer.</SheetDescription>
          </SheetHeader>
          <div className="px-4">
            <CreditPoolForm
              customers={customerItems}
              isPending={createPool.isPending}
              onSubmit={(input) =>
                createPool.mutate(input, {
                  onSuccess: () => {
                    setPoolOpen(false)
                    toast.add({ title: "Credit pool created" })
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
