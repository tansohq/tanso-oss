"use client"

import { useState } from "react"

import { Button } from "@/components/ui/button"
import {
  Field,
  FieldError,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Spinner } from "@/components/ui/spinner"
import { lastFullMonth } from "./format"
import type { ImportInvoiceInput } from "./mutations"
import type { VendorProvider } from "./types"

const providerItems = [
  { label: "Anthropic", value: "ANTHROPIC" },
  { label: "OpenAI", value: "OPENAI" },
]

interface InvoiceImportFormProps {
  isPending: boolean
  onSubmit: (input: ImportInvoiceInput) => void
}

export function InvoiceImportForm({
  isPending,
  onSubmit,
}: InvoiceImportFormProps) {
  const initial = lastFullMonth()
  const [provider, setProvider] = useState<VendorProvider>("ANTHROPIC")
  const [periodStart, setPeriodStart] = useState(initial.from)
  const [periodEnd, setPeriodEnd] = useState(initial.to)
  const [currency, setCurrency] = useState("USD")
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        if (!file) {
          setError("Choose a CSV file")
          return
        }
        if (periodEnd < periodStart) {
          setError("Period end is before period start")
          return
        }
        setError(null)
        onSubmit({ provider, periodStart, periodEnd, currency, file })
      }}
    >
      <FieldGroup>
        <Field>
          <FieldLabel htmlFor="invoice-provider">Vendor</FieldLabel>
          <Select
            items={providerItems}
            value={provider}
            onValueChange={(value) =>
              setProvider((value ?? "ANTHROPIC") as VendorProvider)
            }
          >
            <SelectTrigger id="invoice-provider">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {providerItems.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
        </Field>
        <div className="grid grid-cols-2 gap-4">
          <Field>
            <FieldLabel htmlFor="invoice-start">Period start</FieldLabel>
            <Input
              id="invoice-start"
              type="date"
              value={periodStart}
              onChange={(e) => setPeriodStart(e.target.value)}
              required
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="invoice-end">Period end</FieldLabel>
            <Input
              id="invoice-end"
              type="date"
              value={periodEnd}
              onChange={(e) => setPeriodEnd(e.target.value)}
              required
            />
          </Field>
        </div>
        <Field>
          <FieldLabel htmlFor="invoice-currency">Currency</FieldLabel>
          <Input
            id="invoice-currency"
            value={currency}
            onChange={(e) => setCurrency(e.target.value.toUpperCase())}
            maxLength={3}
            required
          />
        </Field>
        <Field data-invalid={!!error || undefined}>
          <FieldLabel htmlFor="invoice-file">CSV file</FieldLabel>
          <Input
            id="invoice-file"
            type="file"
            accept=".csv,text/csv"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
          <p className="text-xs text-muted-foreground">
            Columns: description, amount (dollars). Optional: kind, model,
            quantity.
          </p>
          {error && <FieldError>{error}</FieldError>}
        </Field>
        <Button type="submit" disabled={isPending}>
          {isPending && <Spinner data-icon="inline-start" />}
          Import
        </Button>
      </FieldGroup>
    </form>
  )
}
