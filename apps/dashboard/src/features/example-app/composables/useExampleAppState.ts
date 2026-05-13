import { ref, computed } from 'vue'
import type { SimulatedInvoice } from '../types'
import { clearCachedApiKey } from '../api'

// Module-level state (singleton, shared across all components)
const customerId = ref<string | null>(null)
const customerUuid = ref<string | null>(null)
const customerName = ref<string | null>(null)
const customerEmail = ref<string | null>(null)

const subscribedPlanId = ref<string | null>(null)
const subscribedPlanName = ref<string | null>(null)
const subscribedPlanPrice = ref<number>(0)
const subscribedPlanCurrency = ref<string>('USD')
const subscriptionId = ref<string | null>(null)
const subscriptionStatus = ref<'active' | 'pending_cancellation' | 'cancelled' | null>(null)
const cancelEffectiveAt = ref<string | null>(null)
const featureUsage = ref<Record<string, number>>({})
const invoices = ref<SimulatedInvoice[]>([])

let invoiceCounter = 0

function generateInvoiceId(): string {
  invoiceCounter++
  return `INV-${String(invoiceCounter).padStart(4, '0')}`
}

export function useExampleAppState() {
  const isSignedUp = computed(() => customerId.value !== null)
  const isSubscribed = computed(() => subscriptionStatus.value === 'active' || subscriptionStatus.value === 'pending_cancellation')
  const currentPlanId = computed(() => subscribedPlanId.value)
  const currentPlanName = computed(() => subscribedPlanName.value)

  function setCustomer(id: string, name: string, email: string, uuid?: string) {
    customerId.value = id
    customerName.value = name
    customerEmail.value = email
    if (uuid) customerUuid.value = uuid
  }

  function setSubscription(subId: string, planId: string, planName: string, price: number, currency = 'USD') {
    subscriptionId.value = subId
    subscribedPlanId.value = planId
    subscribedPlanName.value = planName
    subscribedPlanPrice.value = price
    subscribedPlanCurrency.value = currency
    subscriptionStatus.value = 'active'
    cancelEffectiveAt.value = null
    featureUsage.value = {}

    invoices.value.unshift({
      id: generateInvoiceId(),
      date: new Date().toISOString(),
      planName,
      amount: price,
      currency,
      status: 'Paid'
    })
  }

  function subscribe(planId: string, planName: string, price: number, currency = 'USD') {
    subscribedPlanId.value = planId
    subscribedPlanName.value = planName
    subscribedPlanPrice.value = price
    subscribedPlanCurrency.value = currency
    subscriptionStatus.value = 'active'
    cancelEffectiveAt.value = null
    featureUsage.value = {}

    invoices.value.unshift({
      id: generateInvoiceId(),
      date: new Date().toISOString(),
      planName,
      amount: price,
      currency,
      status: 'Paid'
    })
  }

  function changePlan(planId: string, planName: string, price: number, currency = 'USD') {
    subscribedPlanId.value = planId
    subscribedPlanName.value = planName
    subscribedPlanPrice.value = price
    subscribedPlanCurrency.value = currency
    subscriptionStatus.value = 'active'
    cancelEffectiveAt.value = null
    featureUsage.value = {}

    invoices.value.unshift({
      id: generateInvoiceId(),
      date: new Date().toISOString(),
      planName,
      amount: price,
      currency,
      status: 'Pending'
    })
  }

  function cancelSubscription(effectiveAt?: string) {
    if (effectiveAt) {
      subscriptionStatus.value = 'pending_cancellation'
      cancelEffectiveAt.value = effectiveAt
    } else {
      subscriptionStatus.value = 'cancelled'
      cancelEffectiveAt.value = null
    }
  }

  function setUsage(featureKey: string, used: number) {
    featureUsage.value = {
      ...featureUsage.value,
      [featureKey]: used
    }
  }

  function trackUsage(featureKey: string, units = 1) {
    featureUsage.value = {
      ...featureUsage.value,
      [featureKey]: (featureUsage.value[featureKey] || 0) + units
    }
  }

  function getUsage(featureKey: string): number {
    return featureUsage.value[featureKey] || 0
  }

  function resetState() {
    customerId.value = null
    customerUuid.value = null
    customerName.value = null
    customerEmail.value = null
    subscribedPlanId.value = null
    subscribedPlanName.value = null
    subscribedPlanPrice.value = 0
    subscribedPlanCurrency.value = 'USD'
    subscriptionId.value = null
    subscriptionStatus.value = null
    cancelEffectiveAt.value = null
    featureUsage.value = {}
    invoices.value = []
    invoiceCounter = 0
    clearCachedApiKey()
  }

  return {
    customerId,
    customerUuid,
    customerName,
    customerEmail,
    subscribedPlanId,
    subscribedPlanName,
    subscribedPlanPrice,
    subscribedPlanCurrency,
    subscriptionId,
    subscriptionStatus,
    cancelEffectiveAt,
    featureUsage,
    invoices,
    isSignedUp,
    isSubscribed,
    currentPlanId,
    currentPlanName,
    setCustomer,
    setSubscription,
    subscribe,
    changePlan,
    cancelSubscription,
    setUsage,
    trackUsage,
    getUsage,
    resetState
  }
}
