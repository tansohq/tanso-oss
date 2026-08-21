/**
 * Sorts a catalog list so the most recently created row is first.
 *
 * The API returns rows oldest-first. On an account with any history that puts
 * whatever you just created at the bottom of a long list, so creating a plan
 * showed a success toast and no visible change — the new row was several
 * screens below the fold.
 *
 * Rows with no timestamp keep their relative order and sort last, since an
 * absent createdAt says nothing about recency.
 */
export function newestFirst<T extends { createdAt?: string | null }>(rows: T[]): T[] {
  return [...rows].sort((a, b) => {
    const at = a.createdAt ? Date.parse(a.createdAt) : NaN
    const bt = b.createdAt ? Date.parse(b.createdAt) : NaN
    if (Number.isNaN(at) && Number.isNaN(bt)) return 0
    if (Number.isNaN(at)) return 1
    if (Number.isNaN(bt)) return -1
    return bt - at
  })
}
