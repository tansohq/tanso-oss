"use client"

import { useState } from "react"

import { Button } from "@/components/ui/button"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { Spinner } from "@/components/ui/spinner"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import { toast } from "@/components/ui/toast"
import { useUpdateSpendSettings } from "./mutations"
import type { SpendSettingsDto } from "./types"

interface SpendSettingsFormProps {
  settings: SpendSettingsDto
  onDone: () => void
}

export function SpendSettingsForm({
  settings,
  onDone,
}: SpendSettingsFormProps) {
  const update = useUpdateSpendSettings()
  const [personLevel, setPersonLevel] = useState(settings.personLevelEnabled)
  const [notice, setNotice] = useState(settings.workerNotice ?? "")
  const [slack, setSlack] = useState("")
  const [clearSlack, setClearSlack] = useState(false)

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        update.mutate(
          {
            personLevelEnabled: personLevel,
            workerNotice: notice,
            slackWebhookUrl: clearSlack ? "" : slack.trim() || undefined,
          },
          {
            onSuccess: () => {
              toast.add({ title: "Spend settings saved" })
              onDone()
            },
            onError: (e) =>
              toast.add({ title: "Save failed", description: e.message }),
          }
        )
      }}
    >
      <FieldGroup>
        <Field>
          <FieldLabel htmlFor="settings-notice">Worker notice</FieldLabel>
          <Textarea
            id="settings-notice"
            value={notice}
            onChange={(e) => setNotice(e.target.value)}
            rows={4}
            placeholder="What staff were told: AI spend is attributed to you by name for budgeting; managers see team totals; …"
          />
          <p className="text-xs text-muted-foreground">
            Required before person-level attribution can be turned on.
            Attributing spend to a named employee is a monitoring capability; in
            some jurisdictions works councils must agree first.
          </p>
        </Field>
        <Field orientation="horizontal">
          <FieldLabel htmlFor="settings-person-level">
            Person-level attribution
          </FieldLabel>
          <Switch
            id="settings-person-level"
            checked={personLevel}
            onCheckedChange={setPersonLevel}
          />
        </Field>
        <Field>
          <FieldLabel htmlFor="settings-slack">
            Slack incoming webhook
          </FieldLabel>
          <Input
            id="settings-slack"
            type="password"
            autoComplete="off"
            value={slack}
            disabled={clearSlack}
            onChange={(e) => setSlack(e.target.value)}
            placeholder={
              settings.slackConfigured
                ? "Configured — paste a new URL to replace"
                : "https://hooks.slack.com/services/…"
            }
          />
          <p className="text-xs text-muted-foreground">
            Alerts are posted here as they fire. Stored encrypted; never shown
            again.
          </p>
          {settings.slackConfigured && (
            <label className="flex items-center gap-2 text-xs">
              <input
                type="checkbox"
                checked={clearSlack}
                onChange={(e) => setClearSlack(e.target.checked)}
              />
              Remove the stored webhook
            </label>
          )}
        </Field>
        <Button type="submit" disabled={update.isPending}>
          {update.isPending && <Spinner data-icon="inline-start" />}
          Save
        </Button>
      </FieldGroup>
    </form>
  )
}
