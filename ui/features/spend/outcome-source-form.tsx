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
import { toast } from "@/components/ui/toast"
import { useCreateOutcomeSource } from "./mutations"
import type { OutcomeSource, SpendUnitDto } from "./types"

interface OutcomeSourceFormProps {
  units: SpendUnitDto[]
  onDone: () => void
}

export function OutcomeSourceForm({ units, onDone }: OutcomeSourceFormProps) {
  const create = useCreateOutcomeSource()
  const [source, setSource] = useState<OutcomeSource>("GITHUB")
  const [label, setLabel] = useState("")
  const [token, setToken] = useState("")
  const [scope, setScope] = useState("")
  const [unitId, setUnitId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const sourceItems = [
    { label: "GitHub (merged pull requests)", value: "GITHUB" },
    { label: "Linear (completed issues)", value: "LINEAR" },
  ]
  const unitItems = [
    { label: "None — attribute by person only", value: null as string | null },
    ...units.map((u) => ({ label: u.name, value: u.id as string | null })),
  ]

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        if (!label.trim() || !token.trim() || !scope.trim()) {
          setError("Label, token and scope are required")
          return
        }
        setError(null)
        create.mutate(
          {
            source,
            label: label.trim(),
            token: token.trim(),
            scope: scope.trim(),
            defaultSpendUnitId: unitId ?? undefined,
          },
          {
            onSuccess: () => {
              toast.add({
                title: "Source connected",
                description: "Check the token, then sync.",
              })
              onDone()
            },
            onError: (e) =>
              toast.add({ title: "Connect failed", description: e.message }),
          }
        )
      }}
    >
      <FieldGroup>
        <Field>
          <FieldLabel htmlFor="src-kind">System</FieldLabel>
          <Select
            items={sourceItems}
            value={source}
            onValueChange={(v) => setSource((v ?? "GITHUB") as OutcomeSource)}
          >
            <SelectTrigger id="src-kind">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {sourceItems.map((i) => (
                  <SelectItem key={i.value} value={i.value}>
                    {i.label}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
        </Field>
        <Field data-invalid={!!error || undefined}>
          <FieldLabel htmlFor="src-label">Label</FieldLabel>
          <Input
            id="src-label"
            value={label}
            onChange={(e) => setLabel(e.target.value)}
            placeholder="acme monorepo"
          />
        </Field>
        <Field>
          <FieldLabel htmlFor="src-token">
            {source === "GITHUB" ? "GitHub token" : "Linear API key"}
          </FieldLabel>
          <Input
            id="src-token"
            type="password"
            autoComplete="off"
            value={token}
            onChange={(e) => setToken(e.target.value)}
          />
          <p className="text-xs text-muted-foreground">
            {source === "GITHUB"
              ? "A fine-grained token with read access to pull requests on the repos below. Stored encrypted."
              : "A personal or workspace API key with read access. Stored encrypted."}
          </p>
        </Field>
        <Field>
          <FieldLabel htmlFor="src-scope">
            {source === "GITHUB" ? "Repositories" : "Team keys"}
          </FieldLabel>
          <Input
            id="src-scope"
            value={scope}
            onChange={(e) => setScope(e.target.value)}
            placeholder={
              source === "GITHUB"
                ? "acme/app, acme/site"
                : "BE, FE (or * for all)"
            }
          />
          {error && <FieldError>{error}</FieldError>}
        </Field>
        <Field>
          <FieldLabel htmlFor="src-unit">Default unit</FieldLabel>
          <Select
            items={unitItems}
            value={unitId}
            onValueChange={(v) => setUnitId(v ?? null)}
          >
            <SelectTrigger id="src-unit">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {unitItems.map((i) => (
                  <SelectItem key={i.value ?? "none"} value={i.value}>
                    {i.label}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
          <p className="text-xs text-muted-foreground">
            Where an outcome lands when no person matches its author (by email
            or GitHub login).
          </p>
        </Field>
        <Button type="submit" disabled={create.isPending}>
          {create.isPending && <Spinner data-icon="inline-start" />}
          Connect
        </Button>
      </FieldGroup>
    </form>
  )
}
