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
import { Spinner } from "@/components/ui/spinner"

interface ReplaceKeyFormProps {
  isPending: boolean
  onSubmit: (adminKey: string) => void
}

export function ReplaceKeyForm({ isPending, onSubmit }: ReplaceKeyFormProps) {
  const [adminKey, setAdminKey] = useState("")
  const [error, setError] = useState<string | null>(null)
  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        if (!adminKey.trim()) {
          setError("Admin key is required")
          return
        }
        setError(null)
        onSubmit(adminKey.trim())
      }}
    >
      <FieldGroup>
        <Field data-invalid={!!error || undefined}>
          <FieldLabel htmlFor="replace-admin-key">New admin key</FieldLabel>
          <Input
            id="replace-admin-key"
            type="password"
            autoComplete="off"
            value={adminKey}
            onChange={(e) => setAdminKey(e.target.value)}
          />
          {error && <FieldError>{error}</FieldError>}
        </Field>
        <Button type="submit" disabled={isPending}>
          {isPending && <Spinner data-icon="inline-start" />}
          Replace
        </Button>
      </FieldGroup>
    </form>
  )
}
