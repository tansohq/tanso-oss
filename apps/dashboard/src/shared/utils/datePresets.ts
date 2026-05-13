export type EventsPeriodPreset = '7d' | '30d' | '3m' | '12m' | 'custom'
export type RevenuePeriodPreset = '3m' | '6m' | '12m'

export interface DateRange {
  start: Date
  end: Date
}

export function getDateRange(preset: EventsPeriodPreset | RevenuePeriodPreset): DateRange {
  const end = new Date()
  const start = new Date()

  switch (preset) {
    case '7d':
      start.setDate(end.getDate() - 6)
      break
    case '30d':
      start.setDate(end.getDate() - 29)
      break
    case '3m':
      start.setMonth(end.getMonth() - 3)
      break
    case '6m':
      start.setMonth(end.getMonth() - 6)
      break
    case '12m':
      start.setMonth(end.getMonth() - 12)
      break
    case 'custom':
      start.setDate(end.getDate() - 6)
      break
  }

  start.setHours(0, 0, 0, 0)
  end.setHours(23, 59, 59, 999)
  return { start, end }
}

export function getPresetLabel(preset: EventsPeriodPreset | RevenuePeriodPreset): string {
  switch (preset) {
    case '7d':
      return 'Last 7 days'
    case '30d':
      return 'Last 30 days'
    case '3m':
      return 'Last 3 months'
    case '6m':
      return 'Last 6 months'
    case '12m':
      return 'Last 12 months'
    case 'custom':
      return 'Custom range'
  }
}
