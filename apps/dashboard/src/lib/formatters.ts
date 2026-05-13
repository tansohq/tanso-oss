export function formatPrice(amount: number | null | undefined): string {
  if (amount === null || amount === undefined) return '\u2014'
  if (amount !== 0 && Math.abs(amount) < 0.01) {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumSignificantDigits: 4,
      maximumSignificantDigits: 4
    }).format(amount)
  }
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(amount)
}

export function formatUnitPrice(amount: number | null | undefined): string {
  if (amount === null || amount === undefined) return '\u2014'
  if (amount > 0 && amount < 0.01) {
    const str = amount.toString()
    const match = str.match(/\.(0*)/)
    const decimals = match ? match[1].length + 2 : 4
    return `$${amount.toFixed(Math.max(decimals, 4))}`
  }
  return `$${amount.toFixed(2)}`
}

export function formatCost(amount: number | null | undefined): string {
  if (amount === null || amount === undefined) return '$0.00'
  const decimals = Math.abs(amount) >= 1 ? 2 : 4
  return (
    '$' +
    amount.toLocaleString('en-US', {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals
    })
  )
}

export function formatAmount(amount: number | null | undefined, currency?: string | null): string {
  if (amount === null || amount === undefined) return '\u2014'
  const decimals = Math.abs(amount) >= 1 ? 2 : 4
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: currency || 'USD',
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals
  }).format(amount)
}

export function formatUsageNumber(value: number | string | null | undefined): string {
  if (value === null || value === undefined) return '0'
  const num = typeof value === 'string' ? Number(value) : value
  if (isNaN(num) || !isFinite(num)) return '0'
  if (Number.isInteger(num)) return new Intl.NumberFormat('en-US').format(num)
  // Strip excessive trailing decimals — show at most 2
  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2
  }).format(num)
}

export function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return '\u2014'
  const date = new Date(dateStr)
  return date.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    timeZone: 'UTC'
  })
}

export function formatDateTime(dateStr: string | null | undefined): string {
  if (!dateStr) return '\u2014'
  const date = new Date(dateStr)
  return date.toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'UTC'
  })
}

export function formatIntervalShort(intervalMonths: string | number | null | undefined): string {
  if (intervalMonths == null || intervalMonths === '') return 'mo'
  const months = typeof intervalMonths === 'string' ? parseInt(intervalMonths) : intervalMonths
  switch (months) {
    case 1:
      return 'mo'
    case 3:
      return 'qtr'
    case 6:
      return '6mo'
    case 12:
      return 'yr'
    default:
      return `${months}mo`
  }
}

export function formatInterval(intervalMonths: string | number | null | undefined): string {
  if (intervalMonths == null || intervalMonths === '') return '—'
  const months = typeof intervalMonths === 'string' ? parseInt(intervalMonths) : intervalMonths
  switch (months) {
    case 1:
      return 'Monthly'
    case 3:
      return 'Quarterly'
    case 6:
      return 'Every 6 months'
    case 12:
      return 'Yearly'
    default:
      return `Every ${months} months`
  }
}

export function formatDateRange(start: string, end: string): string {
  const startDate = new Date(start)
  const endDate = new Date(end)
  const startStr = startDate.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC'
  })
  const endStr = endDate.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    timeZone: 'UTC'
  })
  return `${startStr} – ${endStr}`
}

export function formatInvoiceStatus(status: string): string {
  const statusMap: Record<string, string> = {
    PAST_DUE: 'Past Due',
    DUE: 'Due',
    PENDING: 'Pending',
    PAID: 'Paid',
    CANCELLED: 'Cancelled',
    CANCELLED_PROCESSED: 'Cancelled (Processed)',
    VOID: 'Void',
    ADJUSTMENT_OPEN: 'Adjustment (Open)',
    ADJUSTMENT_PAID: 'Adjustment (Paid)'
  }
  return statusMap[status] || status.charAt(0).toUpperCase() + status.slice(1).toLowerCase()
}

export function formatSubscriptionStatus(subscription: {
  isActive: boolean
  cancelledAt?: string | null
  cancelEffectiveAt?: string | null
  scheduledChange?: { changeType: string } | null
}): string {
  if (!subscription.isActive && subscription.cancelledAt) return 'Cancelled'
  if (!subscription.isActive) return 'Draft'
  if (subscription.cancelEffectiveAt) return 'Cancelling'
  if (subscription.scheduledChange) return 'Scheduled Change'
  return 'Active'
}

export function getSubscriptionStatusClasses(subscription: {
  isActive: boolean
  cancelledAt?: string | null
  cancelEffectiveAt?: string | null
  scheduledChange?: { changeType: string } | null
}): string {
  if (!subscription.isActive && subscription.cancelledAt)
    return 'bg-gray-50 text-gray-600 border border-gray-200/50 shadow-none'
  if (!subscription.isActive)
    return 'bg-amber-50 text-amber-700 border border-amber-200/50 shadow-none'
  if (subscription.cancelEffectiveAt)
    return 'bg-amber-50 text-amber-700 border border-amber-200/50 shadow-none'
  if (subscription.scheduledChange)
    return 'bg-blue-50 text-blue-700 border border-blue-200/50 shadow-none'
  return 'bg-emerald-50 text-emerald-700 border border-emerald-200/50 shadow-none'
}

export function getInvoiceStatusClasses(status: string): string {
  switch (status) {
    case 'PAID':
    case 'FINALIZED':
    case 'ADJUSTMENT_PAID':
      return 'bg-emerald-50 text-emerald-700 border border-emerald-200/50 shadow-none'
    case 'DUE':
    case 'PENDING':
    case 'ADJUSTMENT_OPEN':
      return 'bg-amber-50 text-amber-700 border border-amber-200/50 shadow-none'
    case 'PAST_DUE':
      return 'bg-red-50 text-red-700 border border-red-200/50 shadow-none'
    case 'CANCELLED':
    case 'CANCELLED_PROCESSED':
    case 'VOID':
      return 'bg-gray-50 text-gray-600 border border-gray-200/50 shadow-none'
    default:
      return 'bg-gray-50 text-gray-600 border border-gray-200/50 shadow-none'
  }
}
