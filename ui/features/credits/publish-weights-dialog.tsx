"use client"

import { PublishBatchDialog } from "./publish-batch-dialog"

export interface WeightChange {
  featureId: string
  featureKey: string
  model: string | null
  from: number | null
  to: number
}

interface PublishWeightsDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  changes: WeightChange[]
  isPending: boolean
  onSubmit: (effectiveFromIso: string) => void
}

export function PublishWeightsDialog({
  open,
  onOpenChange,
  changes,
  isPending,
  onSubmit,
}: PublishWeightsDialogProps) {
  return (
    <PublishBatchDialog
      open={open}
      onOpenChange={onOpenChange}
      title="Publish tariff"
      description="All changes below take effect together at the chosen time. Charges before that time keep the old weights."
      submitLabel="Publish tariff"
      lines={changes.map((change) => ({
        key: `${change.featureId}|${change.model ?? ""}`,
        label: `${change.featureKey}${change.model ? ` (${change.model})` : ""}: ${change.from ?? "default"} → ${change.to}`,
      }))}
      isPending={isPending}
      onSubmit={onSubmit}
    />
  )
}
