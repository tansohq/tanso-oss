"use client"

import { useState } from "react"

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
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Spinner } from "@/components/ui/spinner"
import { toast } from "@/components/ui/toast"
import {
  useDeleteStripeKeys,
  useRegisterStripeKey,
  useRegisterStripeWebhook,
  useStartStripeImport,
} from "./mutations"
import { useStripeImportStatus, useStripeKeys } from "./queries"

export function StripeCard() {
  const keys = useStripeKeys()
  const registerKey = useRegisterStripeKey()
  const deleteKeys = useDeleteStripeKeys()
  const registerWebhook = useRegisterStripeWebhook()
  const startImport = useStartStripeImport()
  const [keyInput, setKeyInput] = useState("")
  const [disconnectOpen, setDisconnectOpen] = useState(false)
  const [importJobId, setImportJobId] = useState<string | null>(null)
  const importStatus = useStripeImportStatus(importJobId)

  const connected = !!keys.data?.stripeApiKey
  const webhookRegistered = !!keys.data?.webhookSecret
  const importRunning =
    !!importJobId && ["PENDING", "RUNNING", "IN_PROGRESS"].includes(importStatus.data?.status ?? "IN_PROGRESS")

  const connect = () => {
    registerKey.mutate(keyInput.trim(), {
      onSuccess: () => {
        setKeyInput("")
        toast.add({ title: "Stripe connected" })
      },
      onError: (error) => toast.add({ title: "Connect failed", description: error.message }),
    })
  }

  const runImport = () => {
    startImport.mutate(undefined, {
      onSuccess: (job) => {
        setImportJobId(job.jobId ?? null)
        toast.add({ title: "Import started", description: "Auto-creating plans, features, and customers from Stripe." })
      },
      onError: (error) => toast.add({ title: "Import failed", description: error.message }),
    })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Stripe</CardTitle>
        <CardDescription>
          Connect your Stripe account to sync invoices and import existing products, customers,
          and subscriptions.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {keys.isPending ? (
          <Skeleton className="h-9 w-full" />
        ) : !connected ? (
          <div className="flex flex-wrap items-end gap-2">
            <Field className="min-w-64 flex-1">
              <FieldLabel htmlFor="stripe-key">Secret key</FieldLabel>
              <Input
                id="stripe-key"
                type="password"
                placeholder="sk_live_… or sk_test_…"
                className="font-mono"
                value={keyInput}
                onChange={(e) => setKeyInput(e.target.value)}
              />
            </Field>
            <Button onClick={connect} disabled={!keyInput.trim() || registerKey.isPending}>
              {registerKey.isPending && <Spinner data-icon="inline-start" />}
              Connect Stripe
            </Button>
          </div>
        ) : (
          <>
            <div className="flex flex-wrap items-center gap-2">
              <code className="rounded-md border bg-muted px-3 py-2 font-mono text-xs">
                {keys.data?.stripeApiKey}
              </code>
              {webhookRegistered ? (
                <Badge>Webhook registered</Badge>
              ) : (
                <Button
                  variant="outline"
                  size="sm"
                  disabled={registerWebhook.isPending}
                  onClick={() =>
                    registerWebhook.mutate(undefined, {
                      onSuccess: () => toast.add({ title: "Webhook registered" }),
                      onError: (error) =>
                        toast.add({ title: "Webhook registration failed", description: error.message }),
                    })
                  }
                >
                  {registerWebhook.isPending && <Spinner data-icon="inline-start" />}
                  Register webhook
                </Button>
              )}
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Button
                variant="outline"
                disabled={startImport.isPending || importRunning}
                onClick={runImport}
              >
                {(startImport.isPending || importRunning) && <Spinner data-icon="inline-start" />}
                Import from Stripe
              </Button>
              {importJobId && importStatus.data && (
                <span className="font-mono text-xs tabular-nums text-muted-foreground">
                  {importStatus.data.status} · {importStatus.data.processedItems}/
                  {importStatus.data.totalItems}
                  {importStatus.data.failedItems ? ` · ${importStatus.data.failedItems} failed` : ""}
                </span>
              )}
              <Button variant="ghost" size="sm" onClick={() => setDisconnectOpen(true)}>
                Disconnect
              </Button>
            </div>
            {importStatus.data?.status === "FAILED" && importStatus.data.errorDetails && (
              <p className="text-xs text-destructive">{importStatus.data.errorDetails}</p>
            )}
          </>
        )}
      </CardContent>

      <Dialog open={disconnectOpen} onOpenChange={setDisconnectOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Disconnect Stripe</DialogTitle>
            <DialogDescription>
              Removes the Stripe API key and webhook secret from this account. Invoice sync stops
              until you reconnect. Already-imported data stays.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setDisconnectOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={deleteKeys.isPending}
              onClick={() =>
                deleteKeys.mutate(undefined, {
                  onSuccess: () => {
                    setDisconnectOpen(false)
                    toast.add({ title: "Stripe disconnected" })
                  },
                  onError: (error) =>
                    toast.add({ title: "Disconnect failed", description: error.message }),
                })
              }
            >
              Disconnect
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  )
}
