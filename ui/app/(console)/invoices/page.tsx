"use client"

import { useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"

import { DataTable } from "@/components/data-table"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { toast } from "@/components/ui/toast"
import type { InvoiceDto } from "@/lib/api/types"
import { formatCurrency, formatDate } from "@/lib/format"
import { useMarkInvoicePaid } from "@/features/invoices/mutations"
import { useInvoice, useInvoices } from "@/features/invoices/queries"

function statusVariant(status: string | undefined) {
  if (status === "PAID") return "default" as const
  if (status === "VOID") return "outline" as const
  return "secondary" as const
}

function customerLabel(invoice: InvoiceDto): string {
  const customer = invoice.subscription?.customer
  if (!customer) return "—"
  const name = [customer.firstName, customer.lastName].filter(Boolean).join(" ")
  return name || customer.email || "—"
}

const columns: ColumnDef<InvoiceDto>[] = [
  {
    accessorKey: "id",
    header: "Invoice",
    cell: ({ row }) => (
      <span className="font-mono text-xs">{row.original.id?.slice(0, 8)}</span>
    ),
  },
  {
    id: "customer",
    header: "Customer",
    cell: ({ row }) => (
      <div className="flex flex-col">
        <span>{customerLabel(row.original)}</span>
        <span className="text-xs text-muted-foreground">
          {row.original.subscription?.customer?.email}
        </span>
      </div>
    ),
  },
  {
    id: "plan",
    header: "Plan",
    cell: ({ row }) => row.original.subscription?.plan?.name ?? "—",
  },
  {
    accessorKey: "amount",
    header: "Amount",
    cell: ({ row }) => (
      <span className="font-mono tabular-nums">
        {formatCurrency(row.original.amount, row.original.currency ?? "USD")}
      </span>
    ),
  },
  {
    accessorKey: "status",
    header: "Status",
    cell: ({ row }) => (
      <Badge variant={statusVariant(row.original.status)}>{row.original.status}</Badge>
    ),
  },
  {
    accessorKey: "dueDate",
    header: "Due",
    cell: ({ row }) => formatDate(row.original.dueDate),
  },
]

export default function InvoicesPage() {
  const invoices = useInvoices()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const detail = useInvoice(selectedId)
  const markPaid = useMarkInvoicePaid()

  const invoice = detail.data

  return (
    <>
      <div>
        <h1 className="text-xl font-semibold tracking-tight">Invoices</h1>
        <p className="text-sm text-muted-foreground">Generated at cycle end, synced with Stripe.</p>
      </div>
      <DataTable
        columns={columns}
        data={invoices.data ?? []}
        isLoading={invoices.isPending}
        emptyTitle="No invoices"
        emptyDescription="Invoices appear when billing cycles roll over."
        onRowClick={(row) => setSelectedId(row.id ?? null)}
      />
      <Dialog open={!!selectedId} onOpenChange={(open) => !open && setSelectedId(null)}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>
              Invoice{" "}
              <span className="font-mono text-sm text-muted-foreground">
                {invoice?.id?.slice(0, 8)}
              </span>
            </DialogTitle>
            <DialogDescription>
              {customerLabel(invoice ?? ({} as InvoiceDto))}
              {invoice?.subscription?.plan?.name ? ` · ${invoice.subscription.plan.name}` : ""}
              {" · due "}
              {formatDate(invoice?.dueDate)}
            </DialogDescription>
          </DialogHeader>
          {detail.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-9 w-full" />
              <Skeleton className="h-9 w-full" />
              <Skeleton className="h-9 w-full" />
            </div>
          ) : (
            <div className="flex flex-col gap-4">
              <div className="flex items-center justify-between">
                <Badge variant={statusVariant(invoice?.status)}>{invoice?.status}</Badge>
                <span className="font-mono text-xl tabular-nums">
                  {formatCurrency(invoice?.amount, invoice?.currency ?? "USD")}
                </span>
              </div>
              {invoice?.items && invoice.items.length > 0 && (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Description</TableHead>
                      <TableHead className="text-right">Charge</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {invoice.items.map((item) => (
                      <TableRow key={item.id}>
                        <TableCell>{item.description}</TableCell>
                        <TableCell className="text-right font-mono tabular-nums">
                          {formatCurrency(item.chargeAmount, invoice.currency ?? "USD")}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </div>
          )}
          <DialogFooter>
            {invoice?.status !== "PAID" && invoice?.status !== "VOID" && (
              <Button
                disabled={markPaid.isPending || !selectedId}
                onClick={() =>
                  selectedId &&
                  markPaid.mutate(selectedId, {
                    onSuccess: () => {
                      toast.add({ title: "Invoice marked as paid" })
                      setSelectedId(null)
                    },
                    onError: (error) =>
                      toast.add({ title: "Update failed", description: error.message }),
                  })
                }
              >
                {markPaid.isPending && <Spinner data-icon="inline-start" />}
                Mark as paid
              </Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
