import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { env } from '@/lib/env'
import { checkSubscriptionStatus } from '@/lib/subscriptionCache'

const PRODUCTION_SIGNUP_URL = '/signup'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // Public demo routes (no auth required) - uses existing demo pages
    {
      path: '/demo',
      component: () => import('@/features/demo/pages/DemoLayout.vue'),
      meta: { requiresAuth: false },
      children: [
        {
          path: '',
          name: 'demo-home',
          redirect: { name: 'demo-getting-started' }
        },
        {
          path: 'getting-started',
          name: 'demo-getting-started',
          component: () => import('@/features/demo/pages/HomePage.vue')
        },
        {
          path: 'customers',
          name: 'demo-customers',
          component: () => import('@/features/customers/pages/CustomersPage.vue')
        },
        {
          path: 'features',
          name: 'demo-features',
          component: () => import('@/features/features/pages/FeaturesPage.vue')
        },
        {
          path: 'features/:id',
          name: 'demo-feature-detail',
          component: () => import('@/features/features/pages/FeatureDetailPage.vue')
        },
        {
          path: 'credits',
          name: 'demo-credits',
          component: () => import('@/features/credits/pages/CreditModelsPage.vue')
        },
        {
          path: 'credits/:id',
          name: 'demo-credit-detail',
          component: () => import('@/features/credits/pages/CreditModelDetailPage.vue')
        },
        {
          path: 'events',
          name: 'demo-events',
          component: () => import('@/features/events/pages/EventsPage.vue')
        },
        {
          path: 'metering',
          redirect: { name: 'demo-events' }
        },
        {
          path: 'customers/:id',
          name: 'demo-customer-detail',
          component: () => import('@/features/customers/pages/CustomerDetailPage.vue')
        },
        {
          path: 'plans',
          name: 'demo-plans',
          component: () => import('@/features/plans/pages/PlansPage.vue')
        },
        {
          path: 'plans/:id',
          name: 'demo-plan-detail',
          component: () => import('@/features/plans/pages/PlanDetailPage.vue')
        },
        {
          path: 'subscriptions',
          name: 'demo-subscriptions',
          component: () => import('@/features/subscriptions/pages/SubscriptionsPage.vue')
        },
        {
          path: 'subscriptions/:id',
          name: 'demo-subscription-detail',
          component: () => import('@/features/subscriptions/pages/SubscriptionDetailPage.vue')
        },
        {
          path: 'invoices',
          name: 'demo-invoices',
          component: () => import('@/features/invoices/pages/InvoicesPage.vue')
        },
        {
          path: 'invoices/:id',
          name: 'demo-invoice-detail',
          component: () => import('@/features/invoices/pages/InvoiceDetailPage.vue')
        }
      ]
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/features/auth/pages/LoginPage.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/signup',
      name: 'signup',
      component: () => import('@/features/auth/pages/SignupPage.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/',
      name: 'home',
      component: () => import('@/app/views/HomeView.vue'),
      meta: { requiresAuth: true, platformOnly: true }
    },
    {
      path: '/plans',
      name: 'plans',
      component: () => import('@/features/plans/pages/PlansPage.vue'),
      meta: { requiresAuth: true, platformOnly: true }
    },
    {
      path: '/plans/:id',
      name: 'plan-detail',
      component: () => import('@/features/plans/pages/PlanDetailPage.vue'),
      meta: { requiresAuth: true, platformOnly: true }
    },
    {
      path: '/features',
      name: 'features',
      component: () => import('@/features/features/pages/FeaturesPage.vue'),
      meta: { requiresAuth: true, platformOnly: true }
    },
    {
      path: '/features/:id',
      name: 'feature-detail',
      component: () => import('@/features/features/pages/FeatureDetailPage.vue'),
      meta: { requiresAuth: true, platformOnly: true }
    },
    {
      path: '/customers',
      name: 'customers',
      component: () => import('@/features/customers/pages/CustomersPage.vue'),
      meta: { requiresAuth: true, platformOnly: true }
    },
    {
      path: '/customers/:id',
      name: 'customers-detail',
      component: () => import('@/features/customers/pages/CustomerDetailPage.vue'),
      meta: { requiresAuth: true, platformOnly: true }
    },
    {
      path: '/subscriptions',
      name: 'subscriptions',
      component: () => import('@/features/subscriptions/pages/SubscriptionsPage.vue'),
      meta: { requiresAuth: true, platformOnly: true }
    },
    {
      path: '/subscriptions/:id',
      name: 'subscription-detail',
      component: () => import('@/features/subscriptions/pages/SubscriptionDetailPage.vue'),
      meta: { requiresAuth: true, platformOnly: true }
    },
    {
      path: '/invoices',
      name: 'invoices',
      component: () => import('@/features/invoices/pages/InvoicesPage.vue'),
      meta: { requiresAuth: true, platformOnly: true }
    },
    {
      path: '/invoices/:id',
      name: 'invoice-detail',
      component: () => import('@/features/invoices/pages/InvoiceDetailPage.vue'),
      meta: { requiresAuth: true, platformOnly: true }
    },
    {
      path: '/credits',
      name: 'credits',
      component: () => import('@/features/credits/pages/CreditModelsPage.vue'),
      meta: { requiresAuth: true, platformOnly: true }
    },
    {
      path: '/credits/:id',
      name: 'credit-detail',
      component: () => import('@/features/credits/pages/CreditModelDetailPage.vue'),
      meta: { requiresAuth: true, platformOnly: true }
    },
    {
      path: '/events',
      name: 'events',
      component: () => import('@/features/events/pages/EventsPage.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@/features/settings/pages/SettingsPage.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/select-plan',
      name: 'select-plan',
      component: () => import('@/features/settings/pages/SelectPlanPage.vue'),
      meta: { requiresAuth: true, skipSubscriptionCheck: true }
    },
    // Example app routes (preview of customer-facing experience)
    {
      path: '/example-app',
      component: () => import('@/features/example-app/pages/ExampleAppLayout.vue'),
      meta: { requiresAuth: true, skipSubscriptionCheck: true },
      children: [
        {
          path: '',
          name: 'example-signup',
          component: () => import('@/features/example-app/pages/ExampleSignupPage.vue')
        },
        {
          path: 'pricing',
          name: 'example-pricing',
          component: () => import('@/features/example-app/pages/ExamplePricingPage.vue')
        },
        {
          path: 'dashboard',
          name: 'example-dashboard',
          component: () => import('@/features/example-app/pages/ExampleDashboardPage.vue')
        },
        {
          path: 'invoices',
          name: 'example-invoices',
          component: () => import('@/features/example-app/pages/ExampleInvoicesPage.vue')
        }
      ]
    },
    {
      path: '/settings/stripe-import',
      redirect: '/settings?tab=integrations'
    },
    {
      path: '/integrations',
      redirect: '/settings?tab=integrations'
    },
    {
      path: '/developer',
      redirect: '/settings?tab=developer'
    }
  ]
})

router.beforeEach(async (to, _from, next) => {
  // In sandbox BUILD, redirect signup to production (don't use the user's toggled
  // environment — that would loop when a production-domain user has sandbox selected)
  if (to.name === 'signup' && env.environment === 'sandbox') {
    window.location.href = PRODUCTION_SIGNUP_URL
    return
  }

  // Interactive demo is only available in local and production
  if (
    to.path.startsWith('/demo') &&
    !['local', 'staging', 'production'].includes(env.environment)
  ) {
    const authStore = useAuthStore()
    next(authStore.isAuthenticated ? { name: 'home' } : { name: 'login' })
    return
  }

  const authStore = useAuthStore()
  const requiresAuth = to.meta.requiresAuth !== false
  const skipSubscriptionCheck = to.meta.skipSubscriptionCheck === true

  // Check authentication first
  if (requiresAuth && !authStore.isAuthenticated) {
    next({ name: 'login' })
    return
  }

  // Redirect authenticated users away from login/signup
  if ((to.name === 'login' || to.name === 'signup') && authStore.isAuthenticated) {
    next({ name: 'home' })
    return
  }

  // Check subscription status for authenticated routes
  if (requiresAuth && authStore.isAuthenticated && !skipSubscriptionCheck) {
    const hasActiveSubscription = await checkSubscriptionStatus()

    if (!hasActiveSubscription) {
      next({ name: 'select-plan' })
      return
    }
  }

  next()
})

export default router
