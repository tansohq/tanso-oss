import { ref, computed } from 'vue'
import { useDemoState } from './useDemoState'

export type FieldType = 'dimension' | 'metric'
export type AggregationType = 'sum' | 'avg' | 'count'
export type FilterOperator = 'equals' | 'not_equals' | 'contains'

export interface PivotField {
  id: string
  name: string
  type: FieldType
  source: string
  aggregations?: AggregationType[]
}

export interface PivotFieldConfig {
  fieldId: string
  aggregation?: AggregationType
}

export interface PivotFilter {
  fieldId: string
  operator: FilterOperator
  value: string | string[]
}

export interface PivotResult {
  headers: string[]
  columnHeaders: string[]
  rows: PivotResultRow[]
  totals: Record<string, number>
}

export interface PivotResultRow {
  rowKeys: string[]
  values: Record<string, number | null>
}

// Available dimension fields
const dimensionFields: PivotField[] = [
  { id: 'customer', name: 'Customer', type: 'dimension', source: 'customers' },
  { id: 'plan', name: 'Plan', type: 'dimension', source: 'subscriptions' },
  { id: 'segment', name: 'Segment', type: 'dimension', source: 'segments' },
  { id: 'marginStatus', name: 'Margin Status', type: 'dimension', source: 'computed' },
  { id: 'billingCycle', name: 'Billing Cycle', type: 'dimension', source: 'subscriptions' }
]

// Available metric fields
const metricFields: PivotField[] = [
  { id: 'mrr', name: 'MRR', type: 'metric', source: 'customers', aggregations: ['sum', 'avg'] },
  { id: 'costs', name: 'Costs', type: 'metric', source: 'customers', aggregations: ['sum', 'avg'] },
  { id: 'margin', name: 'Margin %', type: 'metric', source: 'customers', aggregations: ['avg'] },
  {
    id: 'customerCount',
    name: 'Customer Count',
    type: 'metric',
    source: 'computed',
    aggregations: ['count']
  },
  {
    id: 'usage',
    name: 'Usage (actions)',
    type: 'metric',
    source: 'customers',
    aggregations: ['sum', 'avg']
  }
]

// Get unique values for a dimension field from data
function getUniqueValues(
  fieldId: string,
  customers: ReturnType<typeof useDemoState>['customers']
): string[] {
  const values = new Set<string>()

  customers.forEach((customer) => {
    switch (fieldId) {
      case 'customer':
        values.add(customer.name)
        break
      case 'plan':
        values.add(customer.plan)
        break
      case 'segment':
        customer.segments.forEach((s) => values.add(s))
        break
      case 'marginStatus':
        values.add(customer.marginStatus)
        break
      case 'billingCycle':
        // Default to monthly for demo
        values.add('monthly')
        break
    }
  })

  return Array.from(values).sort()
}

// Get value from customer for a dimension field
function getDimensionValue(
  fieldId: string,
  customer: ReturnType<typeof useDemoState>['customers'][0]
): string | string[] {
  switch (fieldId) {
    case 'customer':
      return customer.name
    case 'plan':
      return customer.plan
    case 'segment':
      return customer.segments.length > 0 ? customer.segments : ['(none)']
    case 'marginStatus':
      return customer.marginStatus
    case 'billingCycle':
      return 'monthly'
    default:
      return '(unknown)'
  }
}

// Get numeric value from customer for a metric field
function getMetricValue(
  fieldId: string,
  customer: ReturnType<typeof useDemoState>['customers'][0]
): number {
  switch (fieldId) {
    case 'mrr':
      return customer.mrr
    case 'costs':
      return customer.costs.total
    case 'margin':
      return customer.margin * 100
    case 'customerCount':
      return 1
    case 'usage':
      return customer.actionsLast30d
    default:
      return 0
  }
}

// Global reactive state for the pivot builder
const rowFields = ref<PivotFieldConfig[]>([])
const columnFields = ref<PivotFieldConfig[]>([])
const valueFields = ref<PivotFieldConfig[]>([])
const filters = ref<PivotFilter[]>([])

export function usePivotState() {
  const { customers } = useDemoState()

  // All available fields
  const allFields = computed(() => [...dimensionFields, ...metricFields])

  // Fields that are currently placed in a zone
  const usedFieldIds = computed(() => {
    const ids = new Set<string>()
    rowFields.value.forEach((f) => ids.add(f.fieldId))
    columnFields.value.forEach((f) => ids.add(f.fieldId))
    valueFields.value.forEach((f) => ids.add(f.fieldId))
    return ids
  })

  // Available fields (not yet placed in a zone)
  const availableDimensions = computed(() =>
    dimensionFields.filter((f) => !usedFieldIds.value.has(f.id))
  )

  const availableMetrics = computed(() => metricFields.filter((f) => !usedFieldIds.value.has(f.id)))

  // Get field by ID
  function getField(fieldId: string): PivotField | undefined {
    return allFields.value.find((f) => f.id === fieldId)
  }

  // Get unique values for filtering
  function getFilterOptions(fieldId: string): string[] {
    return getUniqueValues(fieldId, customers)
  }

  // Add a field to a zone
  function addFieldToZone(fieldId: string, zone: 'rows' | 'columns' | 'values' | 'filters') {
    const field = getField(fieldId)
    if (!field) return

    const config: PivotFieldConfig = {
      fieldId,
      aggregation: field.type === 'metric' ? (field.aggregations?.[0] ?? 'sum') : undefined
    }

    switch (zone) {
      case 'rows':
        if (field.type === 'dimension') rowFields.value.push(config)
        break
      case 'columns':
        if (field.type === 'dimension') columnFields.value.push(config)
        break
      case 'values':
        if (field.type === 'metric') valueFields.value.push(config)
        break
      case 'filters':
        if (field.type === 'dimension') {
          filters.value.push({
            fieldId,
            operator: 'equals',
            value: []
          })
        }
        break
    }
  }

  // Remove a field from a zone
  function removeFieldFromZone(fieldId: string, zone: 'rows' | 'columns' | 'values' | 'filters') {
    switch (zone) {
      case 'rows':
        rowFields.value = rowFields.value.filter((f) => f.fieldId !== fieldId)
        break
      case 'columns':
        columnFields.value = columnFields.value.filter((f) => f.fieldId !== fieldId)
        break
      case 'values':
        valueFields.value = valueFields.value.filter((f) => f.fieldId !== fieldId)
        break
      case 'filters':
        filters.value = filters.value.filter((f) => f.fieldId !== fieldId)
        break
    }
  }

  // Move a field from one zone to another
  function moveField(
    fieldId: string,
    fromZone: 'rows' | 'columns' | 'values' | 'filters',
    toZone: 'rows' | 'columns' | 'values' | 'filters'
  ) {
    if (fromZone === toZone) return

    const field = getField(fieldId)
    if (!field) return

    // Check if move is valid (dimensions can't go to values, metrics can't go to rows/columns)
    if (field.type === 'dimension' && toZone === 'values') return
    if (
      field.type === 'metric' &&
      (toZone === 'rows' || toZone === 'columns' || toZone === 'filters')
    )
      return

    removeFieldFromZone(fieldId, fromZone)
    addFieldToZone(fieldId, toZone)
  }

  // Update aggregation for a value field
  function updateAggregation(fieldId: string, aggregation: AggregationType) {
    const config = valueFields.value.find((f) => f.fieldId === fieldId)
    if (config) {
      config.aggregation = aggregation
    }
  }

  // Update filter value
  function updateFilter(fieldId: string, value: string | string[]) {
    const filter = filters.value.find((f) => f.fieldId === fieldId)
    if (filter) {
      filter.value = value
    }
  }

  // Reset pivot configuration
  function resetPivot() {
    rowFields.value = []
    columnFields.value = []
    valueFields.value = []
    filters.value = []
  }

  // Compute pivot results
  const pivotResults = computed<PivotResult | null>(() => {
    // Need at least one row dimension and one value metric
    if (rowFields.value.length === 0 || valueFields.value.length === 0) {
      return null
    }

    // Start with all customers
    let filteredCustomers = [...customers]

    // Apply filters
    filters.value.forEach((filter) => {
      if (Array.isArray(filter.value) && filter.value.length === 0) return

      filteredCustomers = filteredCustomers.filter((customer) => {
        const customerValue = getDimensionValue(filter.fieldId, customer)
        const filterValues = Array.isArray(filter.value) ? filter.value : [filter.value]

        if (Array.isArray(customerValue)) {
          return customerValue.some((v) => filterValues.includes(v))
        }

        switch (filter.operator) {
          case 'equals':
            return filterValues.includes(customerValue)
          case 'not_equals':
            return !filterValues.includes(customerValue)
          case 'contains':
            return filterValues.some((fv) => customerValue.toLowerCase().includes(fv.toLowerCase()))
          default:
            return true
        }
      })
    })

    // Get unique column values
    const columnValues: string[] =
      columnFields.value.length > 0
        ? getUniqueValues(columnFields.value[0].fieldId, filteredCustomers)
        : ['Total']

    // Build column headers
    const columnHeaders =
      valueFields.value.length === 1
        ? columnValues
        : columnValues.flatMap((cv) =>
            valueFields.value.map((vf) => {
              const field = getField(vf.fieldId)
              return columnValues.length > 1
                ? `${cv} - ${field?.name}`
                : (field?.name ?? vf.fieldId)
            })
          )

    // Build row headers
    const rowHeaders = rowFields.value.map((rf) => {
      const field = getField(rf.fieldId)
      return field?.name ?? rf.fieldId
    })

    // Group data by row keys
    const rowGroups = new Map<string, typeof filteredCustomers>()

    filteredCustomers.forEach((customer) => {
      // Handle multi-value dimensions (like segments)
      const rowKeyParts = rowFields.value.map((rf) => {
        const value = getDimensionValue(rf.fieldId, customer)
        return Array.isArray(value) ? value : [value]
      })

      // Generate all combinations for multi-value dimensions
      const rowKeysCombinations = cartesianProduct(rowKeyParts)

      rowKeysCombinations.forEach((keys) => {
        const rowKey = keys.join('|||')
        if (!rowGroups.has(rowKey)) {
          rowGroups.set(rowKey, [])
        }
        rowGroups.get(rowKey)!.push(customer)
      })
    })

    // Build result rows
    const rows: PivotResultRow[] = []
    const totals: Record<string, number> = {}

    // Initialize totals
    columnHeaders.forEach((ch) => {
      totals[ch] = 0
    })

    rowGroups.forEach((groupCustomers, rowKey) => {
      const rowKeys = rowKey.split('|||')
      const values: Record<string, number | null> = {}

      // For each column
      if (columnFields.value.length > 0) {
        columnValues.forEach((cv) => {
          // Filter customers matching this column value
          const colCustomers = groupCustomers.filter((c) => {
            const colValue = getDimensionValue(columnFields.value[0].fieldId, c)
            if (Array.isArray(colValue)) {
              return colValue.includes(cv)
            }
            return colValue === cv
          })

          // Calculate each metric
          valueFields.value.forEach((vf) => {
            const field = getField(vf.fieldId)
            const headerKey =
              valueFields.value.length === 1
                ? cv
                : columnValues.length > 1
                  ? `${cv} - ${field?.name}`
                  : (field?.name ?? vf.fieldId)

            const aggregatedValue = aggregateValues(
              colCustomers.map((c) => getMetricValue(vf.fieldId, c)),
              vf.aggregation ?? 'sum'
            )
            values[headerKey] = aggregatedValue

            if (aggregatedValue !== null) {
              totals[headerKey] = (totals[headerKey] ?? 0) + aggregatedValue
            }
          })
        })
      } else {
        // No column dimensions - just aggregate by row
        valueFields.value.forEach((vf) => {
          const field = getField(vf.fieldId)
          const headerKey = field?.name ?? vf.fieldId

          const aggregatedValue = aggregateValues(
            groupCustomers.map((c) => getMetricValue(vf.fieldId, c)),
            vf.aggregation ?? 'sum'
          )
          values[headerKey] = aggregatedValue

          if (aggregatedValue !== null) {
            totals[headerKey] = (totals[headerKey] ?? 0) + aggregatedValue
          }
        })
      }

      rows.push({ rowKeys, values })
    })

    // Sort rows by first key
    rows.sort((a, b) => a.rowKeys[0].localeCompare(b.rowKeys[0]))

    return {
      headers: rowHeaders,
      columnHeaders:
        columnFields.value.length === 0
          ? valueFields.value.map((vf) => getField(vf.fieldId)?.name ?? vf.fieldId)
          : columnHeaders,
      rows,
      totals
    }
  })

  return {
    // Field definitions
    dimensionFields,
    metricFields,
    allFields,
    availableDimensions,
    availableMetrics,

    // Current configuration
    rowFields,
    columnFields,
    valueFields,
    filters,

    // Helpers
    getField,
    getFilterOptions,

    // Actions
    addFieldToZone,
    removeFieldFromZone,
    moveField,
    updateAggregation,
    updateFilter,
    resetPivot,

    // Results
    pivotResults
  }
}

// Helper: Aggregate values
function aggregateValues(values: number[], aggregation: AggregationType): number | null {
  if (values.length === 0) return null

  switch (aggregation) {
    case 'sum':
      return values.reduce((a, b) => a + b, 0)
    case 'avg':
      return values.reduce((a, b) => a + b, 0) / values.length
    case 'count':
      return values.length
    default:
      return null
  }
}

// Helper: Cartesian product of arrays
function cartesianProduct(arrays: string[][]): string[][] {
  if (arrays.length === 0) return [[]]

  const [first, ...rest] = arrays
  const restProduct = cartesianProduct(rest)

  return first.flatMap((val) => restProduct.map((arr) => [val, ...arr]))
}
