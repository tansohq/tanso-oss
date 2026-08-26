"use client"

import { useState } from "react"
import type { ColumnDef } from "@tanstack/react-table"
import { KeyRound, Plus, RefreshCw, ShieldCheck, Trash2 } from "lucide-react"

import { DataTable } from "@/components/data-table"
import { Badge } from "@/components/ui/badge"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { toast } from "@/components/ui/toast"
import { providerLabel } from "@/features/spend/format"
import {
  useCreateVendorConnection,
  useDeleteVendorConnection,
  useProbeVendorConnection,
  useReplaceVendorKey,
  useSyncVendorConnection,
} from "@/features/spend/mutations"
import { isBuildSideOff, useVendorConnections } from "@/features/spend/queries"
import type { VendorConnectionDto } from "@/features/spend/types"
import { ReplaceKeyForm } from "@/features/spend/replace-key-form"
import { VendorConnectionForm } from "@/features/spend/vendor-connection-form"

function shortError(message: string | undefined | null): string {
  if (!message) return "Error"
  const status = message.match(/returned (\d{3})/)?.[1]
  return status
    ? `Key rejected (${status})`
    : message.length > 60
      ? `${message.slice(0, 60)}…`
      : message
}

export default function SpendConnectionsPage() {
  const connections = useVendorConnections()
  const create = useCreateVendorConnection()
  const remove = useDeleteVendorConnection()
  const probe = useProbeVendorConnection()
  const sync = useSyncVendorConnection()
  const replaceKey = useReplaceVendorKey()
  const [open, setOpen] = useState(false)
  const [replacing, setReplacing] = useState<VendorConnectionDto | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)

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
      accessorKey: "status",
      header: "Status",
      cell: ({ row }) =>
        row.original.status === "ERROR" ? (
          <span
            className="text-destructive"
            title={row.original.lastError ?? undefined}
          >
            {shortError(row.original.lastError)}
          </span>
        ) : (
          <span className="text-muted-foreground">OK</span>
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
      cell: ({ row }) => {
        const id = row.original.id ?? ""
        const busy = busyId === id
        return (
          <div className="flex justify-end gap-1">
            <Button
              variant="ghost"
              size="sm"
              disabled={busy}
              onClick={(event) => {
                event.stopPropagation()
                setBusyId(id)
                probe.mutate(id, {
                  onSuccess: (result) =>
                    toast.add({
                      title: result.ok ? "Key accepted" : "Key rejected",
                      description: (result.message ?? "").slice(0, 160),
                    }),
                  onError: (error) =>
                    toast.add({
                      title: "Check failed",
                      description: error.message,
                    }),
                  onSettled: () => setBusyId(null),
                })
              }}
            >
              <ShieldCheck data-icon="inline-start" />
              Check key
            </Button>
            <Button
              variant="ghost"
              size="sm"
              disabled={busy}
              onClick={(event) => {
                event.stopPropagation()
                setBusyId(id)
                sync.mutate(id, {
                  onSuccess: (result) =>
                    toast.add({
                      title: "Synced",
                      description: `${result.rowsWritten} rows for ${result.from} → ${result.to}`,
                    }),
                  onError: (error) =>
                    toast.add({
                      title: "Sync failed",
                      description: error.message,
                    }),
                  onSettled: () => setBusyId(null),
                })
              }}
            >
              <RefreshCw data-icon="inline-start" />
              Sync now
            </Button>
            <Button
              variant="ghost"
              size="sm"
              disabled={busy}
              onClick={(event) => {
                event.stopPropagation()
                setReplacing(row.original)
              }}
            >
              <KeyRound data-icon="inline-start" />
              Replace key
            </Button>
            <Button
              variant="ghost"
              size="icon"
              aria-label="Disconnect"
              disabled={busy || remove.isPending}
              onClick={(event) => {
                event.stopPropagation()
                if (
                  !window.confirm(
                    `Disconnect ${row.original.label}? Tanso forgets the key.`
                  )
                )
                  return
                remove.mutate(id, {
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
          </div>
        )
      },
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
            cost from. Synced hourly; &ldquo;Sync now&rdquo; pulls the last 30
            days.
          </p>
        </div>
        <Button onClick={() => setOpen(true)}>
          <Plus data-icon="inline-start" />
          Connect vendor
        </Button>
      </div>
      {connections.error && !isBuildSideOff(connections.error) ? (
        <Alert variant="destructive">
          <AlertTitle>Could not load connections</AlertTitle>
          <AlertDescription>{connections.error.message}</AlertDescription>
        </Alert>
      ) : isBuildSideOff(connections.error) ? (
        <p className="text-sm text-muted-foreground">
          The build side is switched off on this install
          (APP_MODULES_BUILD_ENABLED=false).
        </p>
      ) : (
        <DataTable
          columns={columns}
          data={connections.data ?? []}
          isLoading={connections.isPending}
          emptyTitle="No vendors connected"
          emptyDescription="Connect an Anthropic or OpenAI admin key, a Cursor admin key, a Copilot token or a LiteLLM proxy to start pulling internal spend."
        />
      )}
      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Connect a vendor</SheetTitle>
            <SheetDescription>
              One admin key per vendor org. To swap a key that stopped working,
              use Replace key on its row instead.
            </SheetDescription>
          </SheetHeader>
          <div className="px-4">
            <VendorConnectionForm
              isPending={create.isPending}
              onSubmit={(input) =>
                create.mutate(input, {
                  onSuccess: (created) => {
                    setOpen(false)
                    if (created.status === "ERROR") {
                      toast.add({
                        title: "Key rejected",
                        description:
                          created.lastError ??
                          "The vendor refused the key. Replace it and check again.",
                      })
                    } else {
                      toast.add({
                        title: "Vendor connected",
                        description: "The key checked out. Sync to pull usage.",
                      })
                    }
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
      <Sheet open={!!replacing} onOpenChange={(v) => !v && setReplacing(null)}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>Replace key</SheetTitle>
            <SheetDescription>
              {replacing?.label} — pulled usage stays; the stored key is swapped
              and the error cleared.
            </SheetDescription>
          </SheetHeader>
          <div className="px-4">
            <ReplaceKeyForm
              isPending={replaceKey.isPending}
              onSubmit={(adminKey) =>
                replacing &&
                replaceKey.mutate(
                  { id: replacing.id ?? "", adminKey },
                  {
                    onSuccess: () => {
                      setReplacing(null)
                      toast.add({
                        title: "Key replaced",
                        description: "Check the key, then sync.",
                      })
                    },
                    onError: (error) =>
                      toast.add({
                        title: "Replace failed",
                        description: error.message,
                      }),
                  }
                )
              }
            />
          </div>
        </SheetContent>
      </Sheet>
    </>
  )
}
