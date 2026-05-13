import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed, toValue, type MaybeRef } from 'vue'
import { useRoute } from 'vue-router'
import { fetchEvents, fetchGroupedEvents } from './api'
import type { GroupBy } from './types'

export function useEventsQuery(
  params: {
    page?: MaybeRef<number>
    size?: MaybeRef<number>
    customerReferenceId?: MaybeRef<string | undefined>
    planId?: MaybeRef<string | undefined>
    featureId?: MaybeRef<string | undefined>
    start?: MaybeRef<string | undefined>
    end?: MaybeRef<string | undefined>
    eventType?: MaybeRef<string | undefined>
    model?: MaybeRef<string | undefined>
    modelProvider?: MaybeRef<string | undefined>
    eventName?: MaybeRef<string | undefined>
  } = {}
) {
  const route = useRoute()
  const queryClient = useQueryClient()
  const page = computed(() => toValue(params.page) ?? 0)
  const size = computed(() => toValue(params.size) ?? 20)
  const customerReferenceId = computed(() => toValue(params.customerReferenceId))
  const planId = computed(() => toValue(params.planId))
  const featureId = computed(() => toValue(params.featureId))
  const start = computed(() => toValue(params.start))
  const end = computed(() => toValue(params.end))
  const eventType = computed(() => toValue(params.eventType))
  const model = computed(() => toValue(params.model))
  const modelProvider = computed(() => toValue(params.modelProvider))
  const eventName = computed(() => toValue(params.eventName))

  const queryKey = computed(() => [
    'events',
    {
      page: page.value,
      size: size.value,
      customerReferenceId: customerReferenceId.value,
      planId: planId.value,
      featureId: featureId.value,
      start: start.value,
      end: end.value,
      eventType: eventType.value,
      model: model.value,
      modelProvider: modelProvider.value,
      eventName: eventName.value
    }
  ])

  return useQuery({
    queryKey,
    queryFn: () => {
      // In demo mode, use the queryDefaults set by useDemoDataSeeder
      if (route.path.startsWith('/demo')) {
        const defaults = queryClient.getQueryDefaults(['events'])
        if (defaults?.queryFn) {
          return (defaults.queryFn as Function)({
            queryKey: ['events', { page: page.value, size: size.value }]
          })
        }
      }
      return fetchEvents({
        page: page.value,
        size: size.value,
        customerReferenceId: customerReferenceId.value,
        planId: planId.value,
        featureId: featureId.value,
        start: start.value,
        end: end.value,
        eventType: eventType.value,
        model: model.value,
        modelProvider: modelProvider.value,
        eventName: eventName.value
      })
    }
  })
}

export function useGroupedEventsQuery(params: {
  groupBy: MaybeRef<GroupBy | undefined>
  customerReferenceId?: MaybeRef<string | undefined>
  planId?: MaybeRef<string | undefined>
  featureId?: MaybeRef<string | undefined>
  start?: MaybeRef<string | undefined>
  end?: MaybeRef<string | undefined>
  eventType?: MaybeRef<string | undefined>
  model?: MaybeRef<string | undefined>
  modelProvider?: MaybeRef<string | undefined>
  eventName?: MaybeRef<string | undefined>
}) {
  const groupBy = computed(() => toValue(params.groupBy))
  const customerReferenceId = computed(() => toValue(params.customerReferenceId))
  const planId = computed(() => toValue(params.planId))
  const featureId = computed(() => toValue(params.featureId))
  const start = computed(() => toValue(params.start))
  const end = computed(() => toValue(params.end))
  const eventType = computed(() => toValue(params.eventType))
  const model = computed(() => toValue(params.model))
  const modelProvider = computed(() => toValue(params.modelProvider))
  const eventName = computed(() => toValue(params.eventName))

  return useQuery({
    queryKey: computed(() => [
      'events',
      'grouped',
      {
        groupBy: groupBy.value,
        customerReferenceId: customerReferenceId.value,
        planId: planId.value,
        featureId: featureId.value,
        start: start.value,
        end: end.value,
        eventType: eventType.value,
        model: model.value,
        modelProvider: modelProvider.value,
        eventName: eventName.value
      }
    ]),
    queryFn: () => {
      if (!groupBy.value) throw new Error('groupBy is required')
      return fetchGroupedEvents({
        groupBy: groupBy.value,
        customerReferenceId: customerReferenceId.value,
        planId: planId.value,
        featureId: featureId.value,
        start: start.value,
        end: end.value,
        eventType: eventType.value,
        model: model.value,
        modelProvider: modelProvider.value,
        eventName: eventName.value
      })
    },
    enabled: computed(() => !!groupBy.value)
  })
}
