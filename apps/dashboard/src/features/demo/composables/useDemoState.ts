import { ref, computed } from 'vue'
import type {
  DemoTab,
  SegmentFilter,
  Segment,
  Automation,
  Experiment,
  Simulation,
  ChurnRisk,
  Feature,
  Plan,
  PlanVersion,
  UsageBasedFee,
  FixedFee,
  PlanSubscription,
  SubscriptionOverride,
  SubscriptionActivity,
  FixedFeeQuantity
} from '../types'
import {
  segments as initialSegments,
  automations as initialAutomations,
  experiments as initialExperiments,
  simulations as initialSimulations,
  integrations,
  segmentSummaryStats,
  automationSummaryStats,
  experimentSummaryStats,
  simulationSummaryStats,
  integrationSummaryStats,
  segmentOptimizationSuggestions,
  customers,
  portfolioSummary,
  features as initialFeatures,
  pricingPlans as initialPricingPlans,
  planSubscriptions as initialSubscriptions
} from '../data/mockData'

// Global reactive state for the demo
const activeTab = ref<DemoTab>('segments')
const segmentFilter = ref<SegmentFilter>('all')
const searchQuery = ref('')
const bannerDismissed = ref(false)

// Reactive copies of mock data for demo interactions
const segmentsData = ref<Segment[]>([...initialSegments])
const automationsData = ref<Automation[]>([...initialAutomations])
const experimentsData = ref<Experiment[]>([...initialExperiments])
const simulationsData = ref<Simulation[]>([...initialSimulations])

// Pricing & Features state
const featuresData = ref<Feature[]>([...initialFeatures])
const pricingPlansData = ref<Plan[]>(JSON.parse(JSON.stringify(initialPricingPlans)))
const featureSearchQuery = ref('')

// Subscriptions state
const subscriptionsData = ref<PlanSubscription[]>(JSON.parse(JSON.stringify(initialSubscriptions)))

// Plan-Feature relationships (maps planId -> array of featureIds)
const planFeatureMap = ref<Record<string, string[]>>({
  plan_starter: ['feat_1', 'feat_2', 'feat_metered_1'],
  plan_growth: ['feat_1', 'feat_2', 'feat_3', 'feat_metered_1', 'feat_metered_2'],
  plan_enterprise: [
    'feat_1',
    'feat_2',
    'feat_3',
    'feat_4',
    'feat_5',
    'feat_metered_1',
    'feat_metered_2',
    'feat_metered_3',
    'feat_metered_4'
  ]
})

// Selection state
const selectedSegmentId = ref<string | null>(null)
const selectedCustomerId = ref<string | null>(null)
const selectedExperimentId = ref<string | null>(null)
const selectedSimulationId = ref<string | null>(null)
const selectedPlanId = ref<string | null>(null)
const selectedPlanVersion = ref<number | null>(null)

// Modal/Sheet visibility state
const showSegmentBuilder = ref(false)
const showAutomationBuilder = ref(false)
const showSegmentDetail = ref(false)
const showCustomerDetail = ref(false)
const showExperimentBuilder = ref(false)
const showCombineSegments = ref(false)

// Editing state
const editingSegmentId = ref<string | null>(null)
const editingAutomationId = ref<string | null>(null)
const combineSourceSegmentId = ref<string | null>(null)

export function useDemoState() {
  // Computed filtered segments
  const filteredSegments = computed(() => {
    let result = segmentsData.value

    // Apply filter by experiment readiness
    switch (segmentFilter.value) {
      case 'ready':
        result = result.filter((c) => c.customerCount > 40)
        break
      case 'small_sample':
        result = result.filter((c) => c.customerCount >= 15 && c.customerCount <= 40)
        break
      case 'too_small':
        result = result.filter((c) => c.customerCount < 15)
        break
    }

    // Apply search
    if (searchQuery.value.trim()) {
      const query = searchQuery.value.toLowerCase()
      result = result.filter(
        (c) =>
          c.name.toLowerCase().includes(query) ||
          c.rules.some((r) => r.fieldLabel.toLowerCase().includes(query))
      )
    }

    return result
  })

  // Get selected segment
  const selectedSegment = computed(() => {
    if (!selectedSegmentId.value) return null
    return segmentsData.value.find((c) => c.id === selectedSegmentId.value) ?? null
  })

  // Get selected customer
  const selectedCustomer = computed(() => {
    if (!selectedCustomerId.value || !selectedSegment.value) return null
    return selectedSegment.value.customers.find((c) => c.id === selectedCustomerId.value) ?? null
  })

  // Get selected experiment
  const selectedExperiment = computed(() => {
    if (!selectedExperimentId.value) return null
    return experimentsData.value.find((e) => e.id === selectedExperimentId.value) ?? null
  })

  // Get selected simulation
  const selectedSimulation = computed(() => {
    if (!selectedSimulationId.value) return null
    return simulationsData.value.find((s) => s.id === selectedSimulationId.value) ?? null
  })

  // Get optimization suggestions for selected segment
  const selectedSegmentSuggestions = computed(() => {
    if (!selectedSegmentId.value) return null
    return segmentOptimizationSuggestions[selectedSegmentId.value] ?? null
  })

  // ============ FEATURES ============

  // Computed filtered features
  const filteredFeatures = computed(() => {
    let result = featuresData.value

    // Apply search
    if (featureSearchQuery.value.trim()) {
      const query = featureSearchQuery.value.toLowerCase()
      result = result.filter(
        (f) =>
          f.name.toLowerCase().includes(query) ||
          f.key.toLowerCase().includes(query) ||
          f.description?.toLowerCase().includes(query)
      )
    }

    return result
  })

  // Create a new feature
  function createFeature(
    feature: Omit<Feature, 'id' | 'createdAt' | 'modifiedAt' | 'plansCount'>
  ): Feature {
    const now = new Date().toISOString()
    const newFeature: Feature = {
      ...feature,
      id: `feat_${Date.now()}`,
      createdAt: now,
      modifiedAt: now,
      plansCount: 0
    }
    featuresData.value.push(newFeature)
    return newFeature
  }

  // Update a feature
  function updateFeature(id: string, updates: Partial<Feature>): void {
    const index = featuresData.value.findIndex((f) => f.id === id)
    if (index !== -1) {
      featuresData.value[index] = {
        ...featuresData.value[index],
        ...updates,
        modifiedAt: new Date().toISOString()
      }
    }
  }

  // Delete a feature
  function deleteFeature(id: string): void {
    const index = featuresData.value.findIndex((f) => f.id === id)
    if (index !== -1) {
      featuresData.value.splice(index, 1)
    }
  }

  // Get features for a specific plan
  function getPlanFeatures(planId: string): Feature[] {
    const featureIds = planFeatureMap.value[planId] || []
    return featuresData.value.filter((f) => featureIds.includes(f.id))
  }

  // Get features available to add to a plan (not already linked)
  function getAvailableFeatures(planId: string): Feature[] {
    const featureIds = planFeatureMap.value[planId] || []
    return featuresData.value.filter((f) => !featureIds.includes(f.id))
  }

  // Add a feature to a plan
  function addFeatureToPlan(planId: string, featureId: string): void {
    if (!planFeatureMap.value[planId]) {
      planFeatureMap.value[planId] = []
    }
    if (!planFeatureMap.value[planId].includes(featureId)) {
      planFeatureMap.value[planId].push(featureId)
      // Update feature's plansCount
      const feature = featuresData.value.find((f) => f.id === featureId)
      if (feature) {
        feature.plansCount++
      }
    }
  }

  // Remove a feature from a plan
  function removeFeatureFromPlan(planId: string, featureId: string): void {
    if (!planFeatureMap.value[planId]) return
    const index = planFeatureMap.value[planId].indexOf(featureId)
    if (index !== -1) {
      planFeatureMap.value[planId].splice(index, 1)
      // Update feature's plansCount
      const feature = featuresData.value.find((f) => f.id === featureId)
      if (feature && feature.plansCount > 0) {
        feature.plansCount--
      }
    }
  }

  // Get plans that use a specific feature
  function getPlansUsingFeature(featureId: string): { id: string; name: string }[] {
    const plans: { id: string; name: string }[] = []
    for (const [planId, featureIds] of Object.entries(planFeatureMap.value)) {
      if (featureIds.includes(featureId)) {
        const plan = pricingPlansData.value.find((p) => p.id === planId)
        if (plan) {
          plans.push({ id: plan.id, name: plan.name })
        }
      }
    }
    return plans
  }

  // ============ PRICING PLANS ============

  // Get selected plan
  const selectedPlan = computed(() => {
    if (!selectedPlanId.value) return null
    return pricingPlansData.value.find((p) => p.id === selectedPlanId.value) ?? null
  })

  // Get the current version of the selected plan
  const selectedPlanCurrentVersion = computed(() => {
    if (!selectedPlan.value) return null
    const version =
      selectedPlanVersion.value ?? selectedPlan.value.versions.find((v) => v.isDefault)?.version
    return selectedPlan.value.versions.find((v) => v.version === version) ?? null
  })

  // Select a plan
  function selectPlan(id: string, version?: number): void {
    selectedPlanId.value = id
    const plan = pricingPlansData.value.find((p) => p.id === id)
    if (plan) {
      selectedPlanVersion.value = version ?? plan.versions.find((v) => v.isDefault)?.version ?? null
    }
  }

  // Create a new plan
  function createPlan(
    plan: Omit<Plan, 'id' | 'createdAt' | 'activity' | 'versions' | 'subscriptionCount' | 'mrr'>
  ): Plan {
    const now = new Date().toISOString().split('T')[0]
    const newPlan: Plan = {
      ...plan,
      id: `plan_${Date.now()}`,
      createdAt: now,
      subscriptionCount: 0,
      mrr: 0,
      versions: [
        {
          version: 1,
          isDefault: true,
          status: 'draft',
          usageBasedFees: [],
          fixedFees: []
        }
      ],
      activity: [{ date: now, action: 'Plan created' }]
    }
    pricingPlansData.value.push(newPlan)
    return newPlan
  }

  // Update a plan
  function updatePlan(id: string, updates: Partial<Omit<Plan, 'versions'>>): void {
    const index = pricingPlansData.value.findIndex((p) => p.id === id)
    if (index !== -1) {
      const plan = pricingPlansData.value[index]
      pricingPlansData.value[index] = {
        ...plan,
        ...updates,
        activity: [
          { date: new Date().toISOString().split('T')[0], action: 'Plan updated' },
          ...plan.activity
        ]
      }
    }
  }

  // Delete a plan
  function deletePlan(id: string): void {
    const index = pricingPlansData.value.findIndex((p) => p.id === id)
    if (index !== -1) {
      pricingPlansData.value.splice(index, 1)
    }
  }

  // ============ PLAN VERSIONS ============

  // Create a new plan version
  function createPlanVersion(planId: string): PlanVersion | null {
    const plan = pricingPlansData.value.find((p) => p.id === planId)
    if (!plan) return null

    const latestVersion = Math.max(...plan.versions.map((v) => v.version))
    const newVersion: PlanVersion = {
      version: latestVersion + 1,
      isDefault: false,
      status: 'draft',
      usageBasedFees: [],
      fixedFees: []
    }
    plan.versions.push(newVersion)
    plan.activity.unshift({
      date: new Date().toISOString().split('T')[0],
      action: `Version ${newVersion.version} created`
    })
    return newVersion
  }

  // Duplicate an existing plan version
  function duplicatePlanVersion(planId: string, sourceVersion: number): PlanVersion | null {
    const plan = pricingPlansData.value.find((p) => p.id === planId)
    if (!plan) return null

    const source = plan.versions.find((v) => v.version === sourceVersion)
    if (!source) return null

    const latestVersion = Math.max(...plan.versions.map((v) => v.version))
    const newVersion: PlanVersion = {
      version: latestVersion + 1,
      isDefault: false,
      status: 'draft',
      usageBasedFees: JSON.parse(JSON.stringify(source.usageBasedFees)),
      fixedFees: JSON.parse(JSON.stringify(source.fixedFees))
    }
    plan.versions.push(newVersion)
    plan.activity.unshift({
      date: new Date().toISOString().split('T')[0],
      action: `Version ${newVersion.version} created (duplicated from v${sourceVersion})`
    })
    return newVersion
  }

  // Publish a plan version
  function publishPlanVersion(planId: string, version: number): void {
    const plan = pricingPlansData.value.find((p) => p.id === planId)
    if (!plan) return

    const planVersion = plan.versions.find((v) => v.version === version)
    if (!planVersion || planVersion.status !== 'draft') return

    planVersion.status = 'published'
    planVersion.publishedAt = new Date().toISOString().split('T')[0]
    plan.activity.unshift({
      date: new Date().toISOString().split('T')[0],
      action: `Version ${version} published`
    })
  }

  // Set a version as default
  function setDefaultPlanVersion(planId: string, version: number): void {
    const plan = pricingPlansData.value.find((p) => p.id === planId)
    if (!plan) return

    const planVersion = plan.versions.find((v) => v.version === version)
    if (!planVersion || planVersion.status === 'draft') return

    // Remove default from all versions
    plan.versions.forEach((v) => {
      v.isDefault = false
    })
    // Set new default
    planVersion.isDefault = true
    plan.activity.unshift({
      date: new Date().toISOString().split('T')[0],
      action: `Version ${version} set as default`
    })
  }

  // Archive a plan version
  function archivePlanVersion(planId: string, version: number): void {
    const plan = pricingPlansData.value.find((p) => p.id === planId)
    if (!plan) return

    const planVersion = plan.versions.find((v) => v.version === version)
    if (!planVersion || planVersion.isDefault) return // Can't archive default version

    planVersion.status = 'archived'
    plan.activity.unshift({
      date: new Date().toISOString().split('T')[0],
      action: `Version ${version} archived`
    })
  }

  // ============ USAGE-BASED FEES ============

  // Add usage-based fee to a plan version
  function addUsageBasedFee(
    planId: string,
    version: number,
    fee: Omit<UsageBasedFee, 'id'>
  ): UsageBasedFee | null {
    const plan = pricingPlansData.value.find((p) => p.id === planId)
    if (!plan) return null

    const planVersion = plan.versions.find((v) => v.version === version)
    if (!planVersion) return null

    const newFee: UsageBasedFee = {
      ...fee,
      id: `fee_${Date.now()}`
    }
    planVersion.usageBasedFees.push(newFee)
    plan.activity.unshift({
      date: new Date().toISOString().split('T')[0],
      action: `Added usage fee "${fee.component}" to version ${version}`
    })
    return newFee
  }

  // Update usage-based fee
  function updateUsageBasedFee(
    planId: string,
    version: number,
    feeId: string,
    updates: Partial<UsageBasedFee>
  ): void {
    const plan = pricingPlansData.value.find((p) => p.id === planId)
    if (!plan) return

    const planVersion = plan.versions.find((v) => v.version === version)
    if (!planVersion) return

    const index = planVersion.usageBasedFees.findIndex((f) => f.id === feeId)
    if (index !== -1) {
      planVersion.usageBasedFees[index] = {
        ...planVersion.usageBasedFees[index],
        ...updates
      }
      plan.activity.unshift({
        date: new Date().toISOString().split('T')[0],
        action: `Updated usage fee in version ${version}`
      })
    }
  }

  // Remove usage-based fee
  function removeUsageBasedFee(planId: string, version: number, feeId: string): void {
    const plan = pricingPlansData.value.find((p) => p.id === planId)
    if (!plan) return

    const planVersion = plan.versions.find((v) => v.version === version)
    if (!planVersion) return

    const index = planVersion.usageBasedFees.findIndex((f) => f.id === feeId)
    if (index !== -1) {
      const fee = planVersion.usageBasedFees[index]
      planVersion.usageBasedFees.splice(index, 1)
      plan.activity.unshift({
        date: new Date().toISOString().split('T')[0],
        action: `Removed usage fee "${fee.component}" from version ${version}`
      })
    }
  }

  // ============ FIXED FEES ============

  // Add fixed fee to a plan version
  function addFixedFee(
    planId: string,
    version: number,
    fee: Omit<FixedFee, 'id'>
  ): FixedFee | null {
    const plan = pricingPlansData.value.find((p) => p.id === planId)
    if (!plan) return null

    const planVersion = plan.versions.find((v) => v.version === version)
    if (!planVersion) return null

    const newFee: FixedFee = {
      ...fee,
      id: `fee_${Date.now()}`
    }
    planVersion.fixedFees.push(newFee)
    plan.activity.unshift({
      date: new Date().toISOString().split('T')[0],
      action: `Added fixed fee "${fee.component}" to version ${version}`
    })
    return newFee
  }

  // Update fixed fee
  function updateFixedFee(
    planId: string,
    version: number,
    feeId: string,
    updates: Partial<FixedFee>
  ): void {
    const plan = pricingPlansData.value.find((p) => p.id === planId)
    if (!plan) return

    const planVersion = plan.versions.find((v) => v.version === version)
    if (!planVersion) return

    const index = planVersion.fixedFees.findIndex((f) => f.id === feeId)
    if (index !== -1) {
      planVersion.fixedFees[index] = {
        ...planVersion.fixedFees[index],
        ...updates
      }
      plan.activity.unshift({
        date: new Date().toISOString().split('T')[0],
        action: `Updated fixed fee in version ${version}`
      })
    }
  }

  // Remove fixed fee
  function removeFixedFee(planId: string, version: number, feeId: string): void {
    const plan = pricingPlansData.value.find((p) => p.id === planId)
    if (!plan) return

    const planVersion = plan.versions.find((v) => v.version === version)
    if (!planVersion) return

    const index = planVersion.fixedFees.findIndex((f) => f.id === feeId)
    if (index !== -1) {
      const fee = planVersion.fixedFees[index]
      planVersion.fixedFees.splice(index, 1)
      plan.activity.unshift({
        date: new Date().toISOString().split('T')[0],
        action: `Removed fixed fee "${fee.component}" from version ${version}`
      })
    }
  }

  // ============ SUBSCRIPTIONS ============

  // Get a subscription by ID
  function getSubscription(id: string): PlanSubscription | undefined {
    return subscriptionsData.value.find((s) => s.id === id)
  }

  // Add activity to a subscription
  function addSubscriptionActivity(id: string, action: string, details?: string): void {
    const subscription = subscriptionsData.value.find((s) => s.id === id)
    if (!subscription) return

    const activity: SubscriptionActivity = {
      date: new Date().toISOString().split('T')[0],
      action,
      details
    }

    if (!subscription.activity) {
      subscription.activity = []
    }
    subscription.activity.push(activity)
  }

  // Pause a subscription
  function pauseSubscription(id: string): void {
    const subscription = subscriptionsData.value.find((s) => s.id === id)
    if (!subscription || subscription.status !== 'active') return

    subscription.status = 'paused'
    addSubscriptionActivity(id, 'Subscription paused', 'Billing suspended')
  }

  // Resume a subscription
  function resumeSubscription(id: string): void {
    const subscription = subscriptionsData.value.find((s) => s.id === id)
    if (!subscription || subscription.status !== 'paused') return

    subscription.status = 'active'
    addSubscriptionActivity(id, 'Subscription resumed', 'Billing resumed')
  }

  // Cancel a subscription
  function cancelSubscription(id: string, endDate?: string): void {
    const subscription = subscriptionsData.value.find((s) => s.id === id)
    if (!subscription || subscription.status === 'canceled') return

    subscription.status = 'canceled'
    subscription.endDate = endDate || new Date().toISOString().split('T')[0]
    addSubscriptionActivity(
      id,
      'Subscription canceled',
      endDate ? `Effective ${endDate}` : 'Effective immediately'
    )
  }

  // Change plan for a subscription
  function changeSubscriptionPlan(
    id: string,
    newPlanId: string,
    newVersion: number,
    effectiveDate?: string
  ): void {
    const subscription = subscriptionsData.value.find((s) => s.id === id)
    if (!subscription) return

    const newPlan = pricingPlansData.value.find((p) => p.id === newPlanId)
    if (!newPlan) return

    const oldPlanName = subscription.planName
    subscription.planId = newPlanId
    subscription.planName = newPlan.name
    subscription.version = newVersion

    addSubscriptionActivity(
      id,
      'Plan changed',
      `From ${oldPlanName} to ${newPlan.name} v${newVersion}${effectiveDate ? ` (effective ${effectiveDate})` : ''}`
    )
  }

  // Add a new subscription
  function addSubscription(
    subscription: Omit<PlanSubscription, 'id' | 'activity'>
  ): PlanSubscription {
    const newSubscription: PlanSubscription = {
      ...subscription,
      id: `sub_${Date.now()}`,
      activity: [
        {
          date: new Date().toISOString().split('T')[0],
          action: 'Subscription created',
          details: `${subscription.planName} - ${subscription.billingCycle}`
        }
      ]
    }
    subscriptionsData.value.push(newSubscription)

    // Update plan subscription count
    const plan = pricingPlansData.value.find((p) => p.id === subscription.planId)
    if (plan) {
      plan.subscriptionCount++
    }

    return newSubscription
  }

  // Update subscription billing info
  function updateSubscriptionBilling(
    id: string,
    updates: { billingCycle?: 'monthly' | 'quarterly' | 'annual'; netTerms?: string }
  ): void {
    const subscription = subscriptionsData.value.find((s) => s.id === id)
    if (!subscription) return

    if (updates.billingCycle) {
      subscription.billingCycle = updates.billingCycle
    }

    addSubscriptionActivity(id, 'Billing information updated')
  }

  // Add pricing override to subscription
  function addSubscriptionOverride(id: string, override: SubscriptionOverride): void {
    const subscription = subscriptionsData.value.find((s) => s.id === id)
    if (!subscription) return

    if (!subscription.overrides) {
      subscription.overrides = []
    }

    // Remove existing override for same feature if exists
    const existingIndex = subscription.overrides.findIndex(
      (o) => o.featureId === override.featureId
    )
    if (existingIndex !== -1) {
      subscription.overrides.splice(existingIndex, 1)
    }

    subscription.overrides.push(override)
    subscription.hasOverrides = true

    addSubscriptionActivity(
      id,
      'Pricing override added',
      `${override.featureName}: $${override.originalPrice} → $${override.overridePrice}`
    )
  }

  // Update pricing override
  function updateSubscriptionOverride(
    id: string,
    featureId: string,
    updates: Partial<SubscriptionOverride>
  ): void {
    const subscription = subscriptionsData.value.find((s) => s.id === id)
    if (!subscription || !subscription.overrides) return

    const override = subscription.overrides.find((o) => o.featureId === featureId)
    if (!override) return

    Object.assign(override, updates)
    addSubscriptionActivity(id, 'Pricing override updated', override.featureName)
  }

  // Remove pricing override
  function removeSubscriptionOverride(id: string, featureId: string): void {
    const subscription = subscriptionsData.value.find((s) => s.id === id)
    if (!subscription || !subscription.overrides) return

    const index = subscription.overrides.findIndex((o) => o.featureId === featureId)
    if (index !== -1) {
      const removed = subscription.overrides.splice(index, 1)[0]
      subscription.hasOverrides = subscription.overrides.length > 0
      addSubscriptionActivity(id, 'Pricing override removed', removed.featureName)
    }
  }

  // Update fixed fee quantity for subscription
  function updateFixedFeeQuantity(id: string, feeQuantity: FixedFeeQuantity): void {
    const subscription = subscriptionsData.value.find((s) => s.id === id)
    if (!subscription) return

    if (!subscription.fixedFeeQuantities) {
      subscription.fixedFeeQuantities = []
    }

    const existingIndex = subscription.fixedFeeQuantities.findIndex(
      (fq) => fq.feeId === feeQuantity.feeId
    )

    if (existingIndex !== -1) {
      subscription.fixedFeeQuantities[existingIndex] = feeQuantity
      addSubscriptionActivity(
        id,
        'Fixed fee quantity updated',
        `${feeQuantity.component}: ${feeQuantity.quantity} units`
      )
    } else {
      subscription.fixedFeeQuantities.push(feeQuantity)
      addSubscriptionActivity(
        id,
        'Fixed fee quantity added',
        `${feeQuantity.component}: ${feeQuantity.quantity} units`
      )
    }
  }

  // Remove fixed fee quantity override
  function removeFixedFeeQuantity(id: string, feeId: string): void {
    const subscription = subscriptionsData.value.find((s) => s.id === id)
    if (!subscription || !subscription.fixedFeeQuantities) return

    const index = subscription.fixedFeeQuantities.findIndex((fq) => fq.feeId === feeId)
    if (index !== -1) {
      const removed = subscription.fixedFeeQuantities.splice(index, 1)[0]
      addSubscriptionActivity(id, 'Fixed fee quantity removed', removed.component)
    }
  }

  // Toggle automation status
  function toggleAutomation(id: string) {
    const automation = automationsData.value.find((a) => a.id === id)
    if (automation) {
      automation.status = automation.status === 'active' ? 'paused' : 'active'
    }
  }

  // Select segment and open detail sheet
  function selectSegment(id: string) {
    selectedSegmentId.value = id
    showSegmentDetail.value = true
  }

  // Select customer and open detail sheet
  function selectCustomer(id: string) {
    selectedCustomerId.value = id
    showCustomerDetail.value = true
  }

  // Select experiment (for internal use, navigation happens via router)
  function selectExperiment(id: string) {
    selectedExperimentId.value = id
  }

  // Close all sheets/modals
  function closeAllSheets() {
    showSegmentDetail.value = false
    showCustomerDetail.value = false
    showSegmentBuilder.value = false
    showAutomationBuilder.value = false
    showExperimentBuilder.value = false
    showCombineSegments.value = false
  }

  // Open segment builder for new segment
  function openSegmentBuilder() {
    editingSegmentId.value = null
    showSegmentBuilder.value = true
  }

  // Open segment builder for editing
  function editSegment(id: string) {
    editingSegmentId.value = id
    showSegmentBuilder.value = true
  }

  // Open automation builder for new automation
  function openAutomationBuilder() {
    editingAutomationId.value = null
    showAutomationBuilder.value = true
  }

  // Open automation builder for editing
  function editAutomation(id: string) {
    editingAutomationId.value = id
    showAutomationBuilder.value = true
  }

  // Dismiss banner
  function dismissBanner() {
    bannerDismissed.value = true
  }

  // Open experiment builder
  function openExperimentBuilder() {
    showExperimentBuilder.value = true
  }

  // Select simulation (for internal use, navigation happens via router)
  function selectSimulation(id: string) {
    selectedSimulationId.value = id
  }

  // Create a new simulation
  function createSimulation(simulation: Simulation) {
    simulationsData.value.push(simulation)
    return simulation
  }

  // Update an existing simulation
  function updateSimulation(id: string, updates: Partial<Simulation>) {
    const index = simulationsData.value.findIndex((s) => s.id === id)
    if (index !== -1) {
      simulationsData.value[index] = { ...simulationsData.value[index], ...updates }
    }
  }

  // Run a simulation (skip running state for demo, go directly to completed)
  function runSimulation(id: string) {
    const simulation = simulationsData.value.find((s) => s.id === id)
    if (!simulation) return

    // Generate mock results based on scenarios
    const baselineRevenue = simulation.segmentPreview.totalMrr
    const baselineMargin = simulation.segmentPreview.avgMargin
    const customerCount = simulation.segmentPreview.customerCount

    // Generate summary table
    const summaryTable = simulation.scenarios.map((scenario, index) => {
      let revenue = baselineRevenue
      let margin = baselineMargin
      let churnRiskCount = Math.floor(customerCount * 0.02)

      if (scenario.pricingChange) {
        const change = scenario.pricingChange.value
        if (scenario.pricingChange.type === 'percentage_increase') {
          revenue = Math.round(baselineRevenue * (1 + change / 100))
          margin = Math.min(95, baselineMargin + Math.floor(change / 3))
          churnRiskCount = Math.floor(customerCount * (change / 100) * 0.5)
        } else if (scenario.pricingChange.type === 'percentage_decrease') {
          revenue = Math.round(baselineRevenue * (1 - change / 100))
          margin = Math.max(20, baselineMargin - Math.floor(change / 2))
          churnRiskCount = Math.max(0, Math.floor(customerCount * 0.01))
        }
      }

      const revenueChange = revenue - baselineRevenue
      const revenuePctChange = baselineRevenue > 0 ? (revenueChange / baselineRevenue) * 100 : 0

      // Assign badges
      const badges: ('highest_revenue' | 'best_margin' | 'lowest_risk')[] = []
      if (index === simulation.scenarios.length - 1 && !scenario.isBaseline) {
        badges.push('highest_revenue')
      }
      if (scenario.isBaseline) {
        badges.push('lowest_risk')
      }

      return {
        scenarioId: scenario.id,
        scenarioName: scenario.name,
        revenue,
        revenueChange,
        revenuePctChange: Math.round(revenuePctChange * 10) / 10,
        margin,
        marginChange: margin - baselineMargin,
        churnRiskCount,
        badges
      }
    })

    // Generate customer impacts using real customer data
    const realCustomers = customers.slice(0, Math.min(5, customerCount))
    const customerImpacts = realCustomers.map((customer) => {
      const baseMrr = customer.mrr
      const scenarioMrrs: Record<string, number> = {}

      simulation.scenarios.forEach((s) => {
        if (s.isBaseline) {
          scenarioMrrs[s.id] = baseMrr
        } else if (s.pricingChange?.type === 'percentage_increase') {
          scenarioMrrs[s.id] = Math.round(baseMrr * (1 + s.pricingChange.value / 100))
        } else {
          scenarioMrrs[s.id] = baseMrr
        }
      })

      const lastScenario = simulation.scenarios[simulation.scenarios.length - 1]
      const changePercent = lastScenario.pricingChange?.value || 0
      const currentMargin = Math.round((customer.margin ?? 0.5) * 100)

      return {
        customerId: customer.id,
        customerName: customer.name,
        currentMrr: baseMrr,
        scenarioMrrs,
        changePercent,
        currentMargin,
        newMargin: Math.min(95, currentMargin + Math.floor(changePercent / 3)),
        churnRisk: (changePercent > 15 ? 'high' : changePercent > 8 ? 'medium' : 'low') as ChurnRisk
      }
    })

    // Find winning scenario (highest revenue non-baseline)
    const nonBaselineResults = summaryTable.filter(
      (s) => !simulation.scenarios.find((sc) => sc.id === s.scenarioId)?.isBaseline
    )
    const winningScenarioId =
      nonBaselineResults.length > 0
        ? nonBaselineResults.reduce((a, b) => (a.revenue > b.revenue ? a : b)).scenarioId
        : undefined

    updateSimulation(id, {
      status: 'completed',
      runAt: new Date().toISOString().split('T')[0],
      summaryTable,
      customerImpacts,
      winningScenarioId
    })
  }

  // Roll out a simulation scenario
  function rolloutSimulation(id: string, scenarioId: string) {
    updateSimulation(id, {
      status: 'rolled_out',
      rolledOutScenarioId: scenarioId,
      rolledOutAt: new Date().toISOString().split('T')[0]
    })
  }

  // Open combine segments dialog
  function openCombineSegments(segmentId: string) {
    combineSourceSegmentId.value = segmentId
    showCombineSegments.value = true
  }

  // Toggle automation pause-during-experiments setting
  function toggleAutomationPauseDuringExperiments(id: string) {
    const automation = automationsData.value.find((a) => a.id === id)
    if (automation) {
      automation.pauseDuringExperiments = !automation.pauseDuringExperiments
      // Clear the paused-by info if disabling
      if (!automation.pauseDuringExperiments) {
        automation.pausedByExperimentId = undefined
        automation.pausedByExperimentName = undefined
        automation.pausedCustomerCount = undefined
      }
    }
  }

  // Get source segment for combine dialog
  const combineSourceSegment = computed(() => {
    if (!combineSourceSegmentId.value) return null
    return segmentsData.value.find((c) => c.id === combineSourceSegmentId.value) ?? null
  })

  // Create a new segment
  function createSegment(segment: Omit<Segment, 'id'> & { id?: string }): Segment {
    const newSegment: Segment = {
      ...segment,
      id: segment.id || `segment_${Date.now()}`
    }
    segmentsData.value.push(newSegment)
    return newSegment
  }

  // Reset demo state
  function resetDemo() {
    segmentsData.value = [...initialSegments]
    automationsData.value = [...initialAutomations]
    experimentsData.value = [...initialExperiments]
    simulationsData.value = [...initialSimulations]
    featuresData.value = [...initialFeatures]
    pricingPlansData.value = JSON.parse(JSON.stringify(initialPricingPlans))
    subscriptionsData.value = JSON.parse(JSON.stringify(initialSubscriptions))
    planFeatureMap.value = {
      plan_starter: ['feat_1', 'feat_2', 'feat_metered_1'],
      plan_growth: ['feat_1', 'feat_2', 'feat_3', 'feat_metered_1', 'feat_metered_2'],
      plan_enterprise: [
        'feat_1',
        'feat_2',
        'feat_3',
        'feat_4',
        'feat_5',
        'feat_metered_1',
        'feat_metered_2',
        'feat_metered_3',
        'feat_metered_4'
      ]
    }
    selectedSegmentId.value = null
    selectedCustomerId.value = null
    selectedExperimentId.value = null
    selectedSimulationId.value = null
    selectedPlanId.value = null
    selectedPlanVersion.value = null
    segmentFilter.value = 'all'
    searchQuery.value = ''
    featureSearchQuery.value = ''
    bannerDismissed.value = false
    closeAllSheets()
  }

  return {
    // Tab state
    activeTab,

    // Filter & search
    segmentFilter,
    searchQuery,
    filteredSegments,

    // Banner
    bannerDismissed,
    dismissBanner,

    // Data
    segmentsData,
    automationsData,
    experimentsData,
    simulationsData,
    integrations,

    // Stats
    segmentSummaryStats,
    automationSummaryStats,
    experimentSummaryStats,
    simulationSummaryStats,
    integrationSummaryStats,

    // Analytics / Customers
    customers,
    portfolioSummary,

    // Selection
    selectedSegmentId,
    selectedSegment,
    selectedSegmentSuggestions,
    selectedCustomerId,
    selectedCustomer,
    selectedExperimentId,
    selectedExperiment,
    selectedSimulationId,
    selectedSimulation,

    // Sheet/modal visibility
    showSegmentBuilder,
    showAutomationBuilder,
    showSegmentDetail,
    showCustomerDetail,
    showExperimentBuilder,
    showCombineSegments,

    // Editing state
    editingSegmentId,
    editingAutomationId,
    combineSourceSegmentId,
    combineSourceSegment,

    // Actions
    toggleAutomation,
    toggleAutomationPauseDuringExperiments,
    selectSegment,
    selectCustomer,
    selectExperiment,
    selectSimulation,
    createSimulation,
    updateSimulation,
    runSimulation,
    rolloutSimulation,
    closeAllSheets,
    openSegmentBuilder,
    editSegment,
    openAutomationBuilder,
    editAutomation,
    openExperimentBuilder,
    openCombineSegments,
    createSegment,
    resetDemo,

    // ============ FEATURES & PRICING ============

    // Features
    featuresData,
    featureSearchQuery,
    filteredFeatures,
    createFeature,
    updateFeature,
    deleteFeature,
    getPlanFeatures,
    getAvailableFeatures,
    addFeatureToPlan,
    removeFeatureFromPlan,
    getPlansUsingFeature,
    planFeatureMap,

    // Plans
    pricingPlansData,
    selectedPlanId,
    selectedPlanVersion,
    selectedPlan,
    selectedPlanCurrentVersion,
    selectPlan,
    createPlan,
    updatePlan,
    deletePlan,

    // Plan Versions
    createPlanVersion,
    duplicatePlanVersion,
    publishPlanVersion,
    setDefaultPlanVersion,
    archivePlanVersion,

    // Usage-Based Fees
    addUsageBasedFee,
    updateUsageBasedFee,
    removeUsageBasedFee,

    // Fixed Fees
    addFixedFee,
    updateFixedFee,
    removeFixedFee,

    // ============ SUBSCRIPTIONS ============
    subscriptionsData,
    getSubscription,
    pauseSubscription,
    resumeSubscription,
    cancelSubscription,
    changeSubscriptionPlan,
    addSubscription,
    updateSubscriptionBilling,
    addSubscriptionOverride,
    updateSubscriptionOverride,
    removeSubscriptionOverride,
    updateFixedFeeQuantity,
    removeFixedFeeQuantity
  }
}
