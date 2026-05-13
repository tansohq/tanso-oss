import { computed } from 'vue'
import { useRoute } from 'vue-router'

/**
 * Returns a function that prefixes paths with /demo when in demo mode.
 * Usage: const { demoPath } = useDemoPrefix()
 *        router.push(demoPath(`/subscriptions/${id}`))
 */
export function useDemoPrefix() {
  const route = useRoute()
  const isDemo = computed(() => route.path.startsWith('/demo'))

  function demoPath(path: string): string {
    if (isDemo.value && !path.startsWith('/demo')) {
      return `/demo${path}`
    }
    return path
  }

  return { isDemo, demoPath }
}
