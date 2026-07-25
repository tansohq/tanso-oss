const utcFormat = new Intl.DateTimeFormat("en-US", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "UTC",
})

export function formatUtc(value: string | Date): string {
  return `${utcFormat.format(new Date(value))} UTC`
}

export function formatRelative(target: Date, from: number): string {
  const mins = Math.round((target.getTime() - from) / 60000)
  if (mins < 60) return `in ${Math.max(mins, 1)} min`
  if (mins < 48 * 60) {
    const hours = Math.round(mins / 60)
    return `in ${hours} ${hours === 1 ? "hour" : "hours"}`
  }
  const days = Math.round(mins / 1440)
  return `in ${days} ${days === 1 ? "day" : "days"}`
}

export function pairKey(featureId: string, model: string | null): string {
  return `${featureId}|${model ?? ""}`
}

const costFormat = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
  maximumFractionDigits: 6,
})

export function formatUnitCost(value: number | undefined): string {
  if (value === undefined) return "—"
  return costFormat.format(value)
}
