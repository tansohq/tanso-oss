"use client"

import { useState } from "react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Field, FieldError, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Spinner } from "@/components/ui/spinner"
import { formatRelative, formatUtc } from "./weight-utils"

interface PublishBatchDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: string
  submitLabel: string
  lines: { key: string; label: string }[]
  isPending: boolean
  onSubmit: (effectiveFromIso: string) => void
}

function toLocalInputValue(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0")
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function tomorrowUtcMidnight(): Date {
  const now = new Date()
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1))
}

function isInFuture(date: Date): boolean {
  return date.getTime() > Date.now()
}

// State resets via key-remount from the parent each time the dialog opens.
export function PublishBatchDialog({
  open,
  onOpenChange,
  title,
  description,
  submitLabel,
  lines,
  isPending,
  onSubmit,
}: PublishBatchDialogProps) {
  const [openedAt] = useState(() => Date.now())
  const [effectiveLocal, setEffectiveLocal] = useState(() =>
    toLocalInputValue(tomorrowUtcMidnight()),
  )
  const [confirmText, setConfirmText] = useState("")
  const [submitError, setSubmitError] = useState<string | null>(null)

  const effectiveDate = effectiveLocal ? new Date(effectiveLocal) : null
  const isValidDate = !!effectiveDate && !Number.isNaN(effectiveDate.getTime())
  const isPast = isValidDate && effectiveDate.getTime() <= openedAt
  const isSoon = isValidDate && !isPast && effectiveDate.getTime() - openedAt < 60 * 60 * 1000
  const canSubmit =
    isValidDate && !isPast && !isPending && (!isSoon || confirmText.trim() === "publish")

  const handleSubmit = () => {
    if (!effectiveDate) return
    if (!isInFuture(effectiveDate)) {
      setSubmitError("Effective time must be in the future.")
      return
    }
    setSubmitError(null)
    onSubmit(effectiveDate.toISOString())
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-4">
          <ul className="flex flex-col gap-1 rounded-lg border bg-muted/50 p-3 font-mono text-sm">
            {lines.map((line) => (
              <li key={line.key}>{line.label}</li>
            ))}
          </ul>
          <Field data-invalid={isPast || !!submitError || undefined}>
            <FieldLabel htmlFor="publish-effective">Effective from</FieldLabel>
            <Input
              id="publish-effective"
              type="datetime-local"
              aria-invalid={isPast || !!submitError}
              value={effectiveLocal}
              onChange={(e) => {
                setEffectiveLocal(e.target.value)
                setSubmitError(null)
              }}
            />
            {(isPast || submitError) && (
              <FieldError>{submitError ?? "Effective time must be in the future."}</FieldError>
            )}
            {isValidDate && !isPast && (
              <p className="text-xs text-muted-foreground">
                {formatUtc(effectiveDate)} · {effectiveDate.toLocaleString()} local ·{" "}
                {formatRelative(effectiveDate, openedAt)}
              </p>
            )}
          </Field>
          {isSoon && (
            <Field>
              <FieldLabel htmlFor="publish-confirm">
                This change takes effect in under an hour. Type{" "}
                <span className="font-mono">publish</span> to confirm.
              </FieldLabel>
              <Input
                id="publish-confirm"
                value={confirmText}
                onChange={(e) => setConfirmText(e.target.value)}
              />
            </Field>
          )}
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button disabled={!canSubmit} onClick={handleSubmit}>
            {isPending && <Spinner data-icon="inline-start" />}
            {submitLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
