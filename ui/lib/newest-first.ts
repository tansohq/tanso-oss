/**
 * Sorts a list so the most recently created row is first.
 *
 * The monetization endpoints return rows oldest-first. On an account with any
 * history that puts whatever the operator just created at the bottom of a long
 * list, so creating a plan showed a success toast and no visible change — the
 * new row was several screens below the fold.
 *
 * A row with no usable timestamp keeps its position relative to the other
 * undated rows and sorts after the dated ones, because an absent createdAt says
 * nothing about recency. A list where nothing is dated therefore comes back in
 * the order the API sent it: applying this to a list that cannot benefit is a
 * no-op rather than a mistake, so it is safe to apply everywhere and starts
 * working on its own the day a DTO grows a createdAt.
 */
export function newestFirst<T>(rows: T[]): T[] {
  const at = (row: T) => {
    const raw = (row as { createdAt?: unknown } | null)?.createdAt
    if (typeof raw !== "string" && typeof raw !== "number") return NaN
    return typeof raw === "number" ? raw : Date.parse(raw)
  }
  return [...rows]
    .map((row, index) => ({ row, index, time: at(row) }))
    .sort((a, b) => {
      const aMissing = Number.isNaN(a.time)
      const bMissing = Number.isNaN(b.time)
      // Stable for anything we cannot date, so an undated list keeps API order.
      if (aMissing && bMissing) return a.index - b.index
      if (aMissing) return 1
      if (bMissing) return -1
      return b.time - a.time
    })
    .map((entry) => entry.row)
}
