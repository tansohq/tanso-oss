export function formatCurrency(value: number | string | undefined | null, currency = "USD"): string {
  if (value === undefined || value === null || value === "") return "—"
  return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(Number(value))
}

export function formatNumber(value: number | string | undefined | null): string {
  if (value === undefined || value === null || value === "") return "—"
  return new Intl.NumberFormat("en-US").format(Number(value))
}

export function formatDate(value: string | undefined | null): string {
  if (!value) return "—"
  return new Intl.DateTimeFormat("en-US", { dateStyle: "medium" }).format(new Date(value))
}

export function formatDateTime(value: string | undefined | null): string {
  if (!value) return "—"
  return new Intl.DateTimeFormat("en-US", { dateStyle: "medium", timeStyle: "short" }).format(
    new Date(value),
  )
}

/** For values that are a calendar date, stored as a UTC-midnight instant. */
export function formatUtcDate(value: string | undefined | null): string {
  if (!value) return "—"
  return new Intl.DateTimeFormat("en-US", { dateStyle: "medium", timeZone: "UTC" }).format(
    new Date(value),
  )
}
