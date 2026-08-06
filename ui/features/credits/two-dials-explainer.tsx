"use client"

import { useState } from "react"
import { ChevronDown, ChevronUp, CircleDollarSign, Flame } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"

interface TwoDialsExplainerProps {
  dial: "weights" | "pricing"
}

/**
 * The teaching strip shown on both dial tabs. Credit pricing has exactly two
 * controls, and every number a vendor sees comes from multiplying them — this
 * component exists so nobody has to reverse-engineer that from the tables.
 */
export function TwoDialsExplainer({ dial }: TwoDialsExplainerProps) {
  const [open, setOpen] = useState(false)

  const dialCard = (
    active: boolean,
    icon: React.ReactNode,
    name: string,
    tab: string,
    summary: string,
  ) => (
    <div
      className={
        active
          ? "flex flex-1 items-start gap-3 rounded-lg border border-primary bg-primary/5 p-3"
          : "flex flex-1 items-start gap-3 rounded-lg border p-3 opacity-70"
      }
    >
      <div className="mt-0.5 text-primary">{icon}</div>
      <div className="flex flex-col gap-0.5">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium">{name}</span>
          {active ? (
            <Badge variant="outline" className="border-primary text-primary">
              This tab
            </Badge>
          ) : (
            <Badge variant="outline">{tab} tab</Badge>
          )}
        </div>
        <p className="text-xs text-muted-foreground">{summary}</p>
      </div>
    </div>
  )

  return (
    <div className="flex flex-col gap-3 rounded-lg border p-4">
      <div className="flex flex-col gap-3 sm:flex-row">
        {dialCard(
          dial === "weights",
          <Flame className="size-4" />,
          "Dial 1 · Burn rate",
          "Weights",
          "How many credits one unit of usage burns. Set per feature, and per model when models cost you differently.",
        )}
        {dialCard(
          dial === "pricing",
          <CircleDollarSign className="size-4" />,
          "Dial 2 · Credit price",
          "Pricing",
          "What one credit costs your customers to buy. Set per denomination in your price book.",
        )}
      </div>

      <p className="font-mono text-xs text-muted-foreground">
        usage × <span className="text-foreground">weight</span> = credits burned · credits ×{" "}
        <span className="text-foreground">price</span> = what customers pay
      </p>

      <div>
        <Button variant="ghost" size="sm" onClick={() => setOpen((v) => !v)}>
          {open ? <ChevronUp data-icon="inline-start" /> : <ChevronDown data-icon="inline-start" />}
          {open ? "Hide the walkthrough" : "New to credit pricing? See a worked example"}
        </Button>
      </div>

      {open && (
        <div className="flex flex-col gap-4 border-t pt-4 text-sm">
          <div className="flex flex-col gap-1">
            <h3 className="text-sm font-medium">A full example, end to end</h3>
            <p className="text-muted-foreground">
              Say you sell <span className="font-mono text-xs">CREDITS</span> at{" "}
              <span className="font-mono text-xs">$0.10</span> each (Pricing tab), and{" "}
              <span className="font-mono text-xs">ai.chat</span> on{" "}
              <span className="font-mono text-xs">gpt-large</span> has a weight of{" "}
              <span className="font-mono text-xs">2.5</span> (Weights tab). A customer runs 100
              requests:
            </p>
            <p className="rounded-md bg-muted/50 p-2 font-mono text-xs">
              100 requests × 2.5 credits = 250 credits burned → 250 × $0.10 = $25.00 of credit
              value
            </p>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="flex flex-col gap-1">
              <h3 className="text-sm font-medium">Turn the burn-rate dial when…</h3>
              <ul className="flex list-disc flex-col gap-1 pl-4 text-muted-foreground">
                <li>
                  Your provider raises prices for one model — raise that model&apos;s weight (e.g.{" "}
                  <span className="font-mono text-xs">2.5 → 3</span>). Other models and your
                  credit price stay untouched.
                </li>
                <li>
                  You launch a cheaper model — add a model-specific weight below the feature
                  default so light usage burns fewer credits.
                </li>
                <li>
                  A feature turns out heavier to serve than expected — raise its default weight
                  instead of repricing every plan.
                </li>
              </ul>
            </div>
            <div className="flex flex-col gap-1">
              <h3 className="text-sm font-medium">Turn the price dial when…</h3>
              <ul className="flex list-disc flex-col gap-1 pl-4 text-muted-foreground">
                <li>
                  You reposition or run a promotion — publish a lower price per credit. Balances
                  customers already hold are unaffected.
                </li>
                <li>
                  Costs rise across the board — raise the credit price once instead of touching
                  every weight.
                </li>
                <li>
                  An enterprise negotiates a volume deal — leave the book alone and grant their
                  credits with a custom unit price on the pool&apos;s Grants form.
                </li>
              </ul>
            </div>
          </div>

          <p className="text-xs text-muted-foreground">
            Changes never rewrite history: you publish a batch that takes effect at a future time
            you pick, scheduled changes can be deleted until they take effect, and everything
            already charged keeps the numbers that were in force at the time. Rows you leave out
            of a publish keep their current values.
          </p>
        </div>
      )}
    </div>
  )
}
