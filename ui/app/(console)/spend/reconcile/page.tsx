"use client"

import { useState } from "react"
import { Trash2, Upload } from "lucide-react"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { toast } from "@/components/ui/toast"
import {
  formatCents,
  lastFullMonth,
  providerLabel,
} from "@/features/spend/format"
import { InvoiceImportForm } from "@/features/spend/invoice-import-form"
import {
  useDeleteVendorInvoice,
  useImportVendorInvoice,
} from "@/features/spend/mutations"
import {
  isBuildSideOff,
  useSpendReconcile,
  useVendorInvoices,
} from "@/features/spend/queries"

function signed(cents: number | undefined | null): string {
  if (cents === undefined || cents === null) return "—"
  const s = formatCents(Math.abs(cents))
  return cents > 0 ? `+${s}` : cents < 0 ? `−${s}` : s
}

export default function SpendReconcilePage() {
  const initial = lastFullMonth()
  const [range, setRange] = useState({ from: initial.from, to: initial.to })
  const [draft, setDraft] = useState(range)
  // Date inputs emit every partial value while typing a year; only a complete,
  // ordered range reaches the query key.
  const commit = () => {
    if (
      /^\d{4}-\d{2}-\d{2}$/.test(draft.from) &&
      /^\d{4}-\d{2}-\d{2}$/.test(draft.to) &&
      draft.from < draft.to
    ) {
      setRange(draft)
    }
  }
  const report = useSpendReconcile(range.from, range.to)
  const invoices = useVendorInvoices()
  const importInvoice = useImportVendorInvoice()
  const deleteInvoice = useDeleteVendorInvoice()
  const [open, setOpen] = useState(false)

  return (
    <>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Reconcile</h1>
          <p className="text-sm text-muted-foreground">
            Per vendor: what the price book says, what the vendor&apos;s report
            says, what the invoice says.
          </p>
        </div>
        <div className="flex items-end gap-2">
          <div className="grid gap-1">
            <Label htmlFor="rec-from">From</Label>
            <Input
              id="rec-from"
              type="date"
              value={draft.from}
              onChange={(e) => setDraft({ ...draft, from: e.target.value })}
              onBlur={commit}
              onKeyDown={(e) => e.key === "Enter" && commit()}
            />
          </div>
          <div className="grid gap-1">
            <Label htmlFor="rec-to">To (inclusive)</Label>
            <Input
              id="rec-to"
              type="date"
              value={draft.to}
              onChange={(e) => setDraft({ ...draft, to: e.target.value })}
              onBlur={commit}
              onKeyDown={(e) => e.key === "Enter" && commit()}
            />
          </div>
          <Button onClick={() => setOpen(true)}>
            <Upload data-icon="inline-start" />
            Import invoice
          </Button>
        </div>
      </div>

      {isBuildSideOff(report.error) ? (
        <p className="text-sm text-muted-foreground">
          The build side is switched off on this install
          (APP_MODULES_BUILD_ENABLED=false).
        </p>
      ) : report.error ? (
        <Alert variant="destructive">
          <AlertTitle>Could not reconcile</AlertTitle>
          <AlertDescription>{report.error.message}</AlertDescription>
        </Alert>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>
              {range.from} → {range.to}
            </CardTitle>
            <CardDescription>
              Metered is an estimate when a model is unpriced or a cache rate is
              missing. Only invoices that sit entirely inside the window count.
            </CardDescription>
          </CardHeader>
          <CardContent className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Vendor</TableHead>
                  <TableHead className="text-right">Metered</TableHead>
                  <TableHead className="text-right">Vendor report</TableHead>
                  <TableHead className="text-right">Invoiced</TableHead>
                  <TableHead className="text-right">Metered − vendor</TableHead>
                  <TableHead className="text-right">Vendor − invoice</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(report.data?.rows ?? []).map((row) => (
                  <TableRow key={row.provider}>
                    <TableCell>{providerLabel[row.provider]}</TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatCents(row.meteredCents)}
                      {row.meteredIsEstimate && (
                        <span
                          className="ml-1 text-muted-foreground"
                          title="Estimate"
                        >
                          ~
                        </span>
                      )}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatCents(row.vendorReportedCents)}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatCents(row.invoicedCents)}
                      {row.invoiceCount > 1 && (
                        <span className="ml-1 text-xs text-muted-foreground">
                          ({row.invoiceCount})
                        </span>
                      )}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {signed(row.meteredVsVendorCents)}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {signed(row.vendorVsInvoiceCents)}
                    </TableCell>
                  </TableRow>
                ))}
                {report.data && report.data.rows.length === 0 && (
                  <TableRow>
                    <TableCell
                      colSpan={6}
                      className="text-center text-muted-foreground"
                    >
                      Nothing to reconcile for this window. Sync a vendor or
                      import an invoice.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle>Imported invoices</CardTitle>
          <CardDescription>
            CSV with a header row: description, amount (in dollars), optional
            kind (TOKEN, SEAT, TOOL, OTHER), model, quantity.
          </CardDescription>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Vendor</TableHead>
                <TableHead>Period</TableHead>
                <TableHead className="text-right">Lines</TableHead>
                <TableHead className="text-right">Total</TableHead>
                <TableHead>File</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {(invoices.data ?? []).map((inv) => (
                <TableRow key={inv.id}>
                  <TableCell>{providerLabel[inv.provider]}</TableCell>
                  <TableCell className="tabular-nums">
                    {inv.periodStart} → {inv.periodEnd}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {inv.lines.length}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatCents(inv.totalCents, inv.currency)}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {inv.importedFrom ?? "—"}
                  </TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label="Remove invoice"
                      disabled={deleteInvoice.isPending}
                      onClick={() => {
                        if (!window.confirm("Remove this imported invoice?"))
                          return
                        deleteInvoice.mutate(inv.id, {
                          onSuccess: () =>
                            toast.add({ title: "Invoice removed" }),
                          onError: (error) =>
                            toast.add({
                              title: "Remove failed",
                              description: error.message,
                            }),
                        })
                      }}
                    >
                      <Trash2 />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
              {invoices.data && invoices.data.length === 0 && (
                <TableRow>
                  <TableCell
                    colSpan={6}
                    className="text-center text-muted-foreground"
                  >
                    No invoices imported yet.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Import a vendor invoice</SheetTitle>
            <SheetDescription>
              The bill for one period, as a CSV of its lines.
            </SheetDescription>
          </SheetHeader>
          <div className="px-4">
            <InvoiceImportForm
              isPending={importInvoice.isPending}
              onSubmit={(input) =>
                importInvoice.mutate(input, {
                  onSuccess: (inv) => {
                    setOpen(false)
                    toast.add({
                      title: "Invoice imported",
                      description: `${inv.lines.length} lines, ${formatCents(inv.totalCents, inv.currency)}`,
                    })
                  },
                  onError: (error) =>
                    toast.add({
                      title: "Import failed",
                      description: error.message,
                    }),
                })
              }
            />
          </div>
        </SheetContent>
      </Sheet>
    </>
  )
}
