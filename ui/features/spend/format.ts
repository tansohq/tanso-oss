import { formatCurrency, formatNumber } from "@/lib/format"

export function formatCents(
  cents: number | undefined | null,
  currency = "USD"
): string {
  if (cents === undefined || cents === null) return "—"
  return formatCurrency(cents / 100, currency)
}

export function formatTokens(tokens: number | undefined | null): string {
  if (tokens === undefined || tokens === null) return "—"
  if (tokens >= 1_000_000) return `${(tokens / 1_000_000).toFixed(2)}M`
  if (tokens >= 1_000) return `${(tokens / 1_000).toFixed(1)}k`
  return formatNumber(tokens)
}

export function isoDate(d: Date): string {
  return d.toISOString().slice(0, 10)
}

export function daysAgo(n: number): string {
  const d = new Date()
  d.setUTCDate(d.getUTCDate() - n)
  return isoDate(d)
}

export function lastFullMonth(): { from: string; to: string } {
  const now = new Date()
  const first = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1))
  const prevFirst = new Date(
    Date.UTC(first.getUTCFullYear(), first.getUTCMonth() - 1, 1)
  )
  const prevLast = new Date(first.getTime() - 86_400_000)
  return { from: isoDate(prevFirst), to: isoDate(prevLast) }
}

export const providerLabel: Record<string, string> = {
  ANTHROPIC: "Anthropic",
  OPENAI: "OpenAI",
  CURSOR: "Cursor",
  COPILOT: "GitHub Copilot",
}
