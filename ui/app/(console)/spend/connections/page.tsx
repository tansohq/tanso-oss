"use client"

import { useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import { Plus, Trash2 } from "lucide-react"

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
import {
  useCreateVendorConnection,
  useDeleteVendorConnection,
} from "@/features/spend/mutations"
import { useVendorConnections } from "@/features/spend/queries"
import type { VendorConnectionDto } from "@/features/spend/types"
import { VendorConnectionForm } from "@/features/spend/vendor-connection-form"

const providerLabel: Record<string, string> = {
  ANTHROPIC: "Anthropic",
  OPENAI: "OpenAI",
}

export default function SpendConnectionsPage() {
  const connections = useVendorConnections()
  const create = useCreateVendorConnection()
  const remove = useDeleteVendorConnection()
  const [open, setOpen] = useState(false)

  const columns: ColumnDef<VendorConnectionDto>[] = [
    {
      accessorKey: "provider",
      header: "Provider",
      cell: ({ row }) => (
        <Badge variant="secondary">
          {providerLabel[row.original.provider ?? ""] ?? row.original.provider}
        </Badge>
      ),
    },
    { accessorKey: "label", header: "Label" },
    {
      accessorKey: "keyHint",
      header: "Key",
      cell: ({ row }) => (
        <span className="font-mono text-muted-foreground">
          …{row.original.keyHint}
        </span>
      ),
    },
    {
      accessorKey: "lastSyncedAt",
      header: "Last synced",
      cell: ({ row }) =>
        row.original.lastSyncedAt ? (
          new Date(row.original.lastSyncedAt).toLocaleString()
        ) : (
          <span className="text-muted-foreground">Never</span>
        ),
    },
    {
      id: "actions",
      header: "",
      cell: ({ row }) => (
        <Button
          variant="ghost"
          size="icon"
          aria-label="Disconnect"
          disabled={remove.isPending}
          onClick={(event) => {
            event.stopPropagation()
            remove.mutate(row.original.id ?? "", {
              onSuccess: () => toast.add({ title: "Disconnected" }),
              onError: (error) =>
                toast.add({
                  title: "Disconnect failed",
                  description: error.message,
                }),
            })
          }}
        >
          <Trash2 />
        </Button>
      ),
    },
  ]

  return (
    <>
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">
            Vendor connections
          </h1>
          <p className="text-sm text-muted-foreground">
            Admin credentials Tanso pulls your organisation&apos;s AI usage and
            cost from.
          </p>
        </div>
        <Button onClick={() => setOpen(true)}>
          <Plus data-icon="inline-start" />
          Connect vendor
        </Button>
      </div>
      <DataTable
        columns={columns}
        data={connections.data ?? []}
        isLoading={connections.isPending}
        emptyTitle="No vendors connected"
        emptyDescription="Connect an Anthropic or OpenAI admin key to start pulling internal spend."
      />
      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Connect a vendor</SheetTitle>
            <SheetDescription>One admin key per vendor org.</SheetDescription>
          </SheetHeader>
          <div className="px-4">
            <VendorConnectionForm
              isPending={create.isPending}
              onSubmit={(input) =>
                create.mutate(input, {
                  onSuccess: () => {
                    setOpen(false)
                    toast.add({ title: "Vendor connected" })
                  },
                  onError: (error) =>
                    toast.add({
                      title: "Connect failed",
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
