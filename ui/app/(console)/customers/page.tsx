"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import type { ColumnDef } from "@tanstack/react-table"
import { Plus } from "lucide-react"

import { DataTable } from "@/components/data-table"
import { Button } from "@/components/ui/button"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { toast } from "@/components/ui/toast"
import type { CustomerBulkResponse } from "@/lib/api/types"
import { formatDate } from "@/lib/format"
import { CustomerForm } from "@/features/customers/customer-form"
import { useCreateCustomer } from "@/features/customers/mutations"
import { useCustomers } from "@/features/customers/queries"

type CustomerRow = NonNullable<CustomerBulkResponse["customers"]>[number]

const columns: ColumnDef<CustomerRow>[] = [
  {
    id: "name",
    header: "Name",
    accessorFn: (row) => [row.firstName, row.lastName].filter(Boolean).join(" "),
    cell: ({ getValue }) => (getValue() as string) || "—",
  },
  { accessorKey: "email", header: "Email" },
  {
    accessorKey: "referenceId",
    header: "Reference ID",
    cell: ({ row }) => <span className="font-mono text-xs">{row.original.referenceId}</span>,
  },
  {
    accessorKey: "createdAt",
    header: "Created",
    cell: ({ row }) => formatDate(row.original.createdAt),
  },
]

export default function CustomersPage() {
  const router = useRouter()
  const customers = useCustomers()
  const createCustomer = useCreateCustomer()
  const [createOpen, setCreateOpen] = useState(false)

  return (
    <>
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Customers</h1>
          <p className="text-sm text-muted-foreground">The businesses you bill.</p>
        </div>
        <Button onClick={() => setCreateOpen(true)}>
          <Plus data-icon="inline-start" />
          New customer
        </Button>
      </div>
      <DataTable
        columns={columns}
        data={customers.data?.customers ?? []}
        isLoading={customers.isPending}
        emptyTitle="No customers yet"
        emptyDescription="Create a customer or ingest events to auto-create them."
        onRowClick={(customer) => router.push(`/customers/${customer.id}`)}
      />
      <Sheet open={createOpen} onOpenChange={setCreateOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>New customer</SheetTitle>
            <SheetDescription>A billing entity you can subscribe to plans.</SheetDescription>
          </SheetHeader>
          <div className="px-4">
            <CustomerForm
              isPending={createCustomer.isPending}
              onSubmit={(input) =>
                createCustomer.mutate(input, {
                  onSuccess: () => {
                    setCreateOpen(false)
                    toast.add({ title: "Customer created" })
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
