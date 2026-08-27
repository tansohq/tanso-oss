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
import { useFeatures } from "@/features/features/queries"
import { useCreateSpendUnit, useUpdateSpendUnit } from "./mutations"
import type { SpendUnitDto, SpendUnitType } from "./types"

interface UnitFormProps {
  units: SpendUnitDto[]
  personLevelEnabled: boolean
  existing?: SpendUnitDto
  onDone: (unit: SpendUnitDto) => void
}

export function UnitForm({
  units,
  personLevelEnabled,
  existing,
  onDone,
}: UnitFormProps) {
  const create = useCreateSpendUnit()
  const update = useUpdateSpendUnit()
  const [type, setType] = useState<SpendUnitType>(existing?.type ?? "TEAM")
  const [name, setName] = useState(existing?.name ?? "")
  const [email, setEmail] = useState(existing?.email ?? "")
  const [githubLogin, setGithubLogin] = useState(existing?.githubLogin ?? "")
  const [parentId, setParentId] = useState<string | null>(
    existing?.parentId ?? null
  )
  const [featureId, setFeatureId] = useState<string | null>(
    existing?.featureId ?? null
  )
  const features = useFeatures()
  const featureItems = [
    { label: "No feature", value: null as string | null },
    ...(features.data ?? []).map((ft) => ({
      label: `${ft.name} (${ft.key})`,
      value: (ft.id ?? null) as string | null,
    })),
  ]
  const [error, setError] = useState<string | null>(null)
  const pending = create.isPending || update.isPending

  const typeItems = [
    { label: "Team", value: "TEAM" },
    { label: "Project", value: "PROJECT" },
    ...(personLevelEnabled ? [{ label: "Person", value: "PERSON" }] : []),
  ]
  const parentItems = [
    { label: "No parent", value: null as string | null },
    ...units
      .filter((u) => u.id !== existing?.id)
      .map((u) => ({ label: u.name, value: u.id as string | null })),
  ]

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        if (!name.trim()) {
          setError("Name is required")
          return
        }
        setError(null)
        const input = {
          type,
          name: name.trim(),
          email: email.trim() || undefined,
          githubLogin: githubLogin.trim() || undefined,
          parentId: parentId ?? undefined,
          featureId: type === "PROJECT" ? (featureId ?? undefined) : undefined,
        }
        const opts = {
          onSuccess: (unit: SpendUnitDto) => {
            toast.add({ title: existing ? "Unit updated" : "Unit created" })
            onDone(unit)
          },
          onError: (e: Error) =>
            toast.add({ title: "Save failed", description: e.message }),
        }
        if (existing) update.mutate({ id: existing.id, ...input }, opts)
        else create.mutate(input, opts)
      }}
    >
      <FieldGroup>
        <Field>
          <FieldLabel htmlFor="unit-type">Type</FieldLabel>
          <Select
            items={typeItems}
            value={type}
            onValueChange={(v) => setType((v ?? "TEAM") as SpendUnitType)}
          >
            <SelectTrigger id="unit-type">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {typeItems.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
        </Field>
        <Field data-invalid={!!error || undefined}>
          <FieldLabel htmlFor="unit-name">Name</FieldLabel>
          <Input
            id="unit-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Backend"
          />
          {error && <FieldError>{error}</FieldError>}
        </Field>
        {type === "PERSON" && (
          <Field>
            <FieldLabel htmlFor="unit-email">Email</FieldLabel>
            <Input
              id="unit-email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="alice@acme.com"
            />
            <p className="text-xs text-muted-foreground">
              The address Claude Code reports them under. Add an actor rule with
              it to attribute their spend.
            </p>
          </Field>
        )}
        {type === "PERSON" && (
          <Field>
            <FieldLabel htmlFor="unit-github">GitHub login</FieldLabel>
            <Input
              id="unit-github"
              value={githubLogin}
              onChange={(e) => setGithubLogin(e.target.value)}
              placeholder="alice"
            />
            <p className="text-xs text-muted-foreground">
              Merged pull requests by this login count as their outcomes.
            </p>
          </Field>
        )}
        {type === "PROJECT" && (
          <Field>
            <FieldLabel htmlFor="unit-feature">Feature shipped</FieldLabel>
            <Select
              items={featureItems}
              value={featureId}
              onValueChange={(v) => setFeatureId(v ?? null)}
            >
              <SelectTrigger id="unit-feature">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  {featureItems.map((item) => (
                    <SelectItem key={item.value ?? "none"} value={item.value}>
                      {item.label}
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
            <p className="text-xs text-muted-foreground">
              The monetization feature this project built. Internal spend → P&amp;L puts
              the project&apos;s build cost next to what the feature earns.
            </p>
          </Field>
        )}
        <Field>
          <FieldLabel htmlFor="unit-parent">Rolls up into</FieldLabel>
          <Select
            items={parentItems}
            value={parentId}
            onValueChange={(v) => setParentId(v ?? null)}
          >
            <SelectTrigger id="unit-parent">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {parentItems.map((item) => (
                  <SelectItem key={item.value ?? "none"} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
        </Field>
        <Button type="submit" disabled={pending}>
          {pending && <Spinner data-icon="inline-start" />}
          {existing ? "Save" : "Create"}
        </Button>
      </FieldGroup>
    </form>
  )
}
